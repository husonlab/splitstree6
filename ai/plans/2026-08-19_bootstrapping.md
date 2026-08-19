# Plan — bootstrapping: finish separating the computation from the workflow

**Status:** DONE, 2026-08-19. `BootstrapSplits` `b995795c`, `BootstrapTreeSplits` `f4e4e885`, `BootstrapTree`
`0cd540cb`. All three now read the alignment and the pipeline out of the workflow in their old entry point and
delegate to a form taking both explicitly; each was checked by requiring the workflow route to produce output
identical to before, and by running the same computation with no workflow at all and requiring the same answer.
`run` stayed static in `BootstrapTree`, per Daniel. The rest of this document is kept as the record of why the
shape is what it is — §2 in particular, which explains why the pipeline is a supplier and why the algorithms
inside it are shared.
**Raised by:** Daniel, 2026-08-19, after the same construct was applied to the simpler cases in `f2ed09c2`.
**Related:** `ai/docs/20_logic.md` §3 and §10, `ai/docs/50_todo.md`, `ai/plans/2026-08-17_splitstree-py.md`.

---

## 1. What bootstrapping needs, and why it is different

Every other algorithm in SplitsTree is a function of its declared input. Bootstrapping is not. To produce
support values for a set of splits it must

1. resample the **original alignment**, which is not its input — its input is splits or trees; and
2. push each replicate through **the same chain of algorithms** that produced those splits in the first place.

Inside the application both come from the workflow: the alignment from `workflow.getWorkingDataNode()`, the
chain from `BootstrappingUtils.extractPath(workingDataNode, targetNode)`, which walks back from the target
collecting `(algorithm, output block)` pairs. That is a perfectly reasonable design — the workflow is exactly
the object that knows the provenance of a result, and bootstrapping is a question about provenance.

The cost is that the computation cannot be run any other way. `getNode()` is null for a block that was never
put in a workflow, so a test, a command-line tool or a language binding gets a `NullPointerException` before
anything is computed. The three earlier cases (`MedianJoining`, `MinSpanningNetwork`, `MinSpanningTree`) wanted
one extra data block and were fixed by handing it over; bootstrapping wants a *pipeline*, which is why it was
left out of `f2ed09c2` and needs its own pass.

**This is not a proposal to remove the workflow route.** It stays, and stays the way the application works. The
proposal is that the workflow route should *read the inputs and delegate*, so that the computation underneath
is callable directly.

## 2. What is already done

`BootstrapSplits`, in `b995795c`:

```java
// the application's entry point: read the alignment and the pipeline out of the workflow, then delegate
public void compute(progress, taxa, inputSplits, DataNode targetNode, splitsBlock)

// the computation: everything it needs, passed in
public void compute(progress, taxa, inputSplits, splitsBlock, CharactersBlock characters, PathSupplier path)
```

Two things about that shape are settled and should be copied rather than revisited:

- **The pipeline arrives as a supplier, not a list.** Each worker thread needs its own. The data blocks in a
  path are output buffers, and `TreeSelectorSplits` rewrites its own `optionWhich` against the block it is
  handed, so a shared path would have threads overwriting each other.
- **The algorithms inside a path are deliberately shared** between threads, exactly as `extractPath` has always
  returned them. They carry the user's configured options; `Algorithm.newInstance()` would hand back defaults
  and silently change the answer.

Also fixed there, and worth knowing before touching anything else: the aggregation was a non-atomic
read-modify-write on two shared maps, so **support values were not reproducible even with `optionRandomSeed`
set** — 90, 95 and 100 percent for the same split across three runs of one build. Now `merge`. Any baseline
recorded before that commit is unreliable and must not be used to check the work below.

## 3. What is left

### 3.1 `BootstrapTreeSplits` — mechanical

It converts its input tree to splits, configures a `BootstrapSplits`, and calls the *workflow* form:

```java
bootstrapSplits.compute(progress, taxaBlock, inputSplits, inputTrees.getNode(), splitsBlock);
```

So it needs the same pair of methods: a four-argument `compute` that delegates, and one taking
`(CharactersBlock, PathSupplier)` that forwards them to `BootstrapSplits`. The tree→splits conversion at the
top is pure and moves into the second form unchanged.

One wrinkle to keep: its `isApplicable` also reaches into the workflow —

```java
var workflow = (Workflow) datablock.getNode().getOwner();
... workflow.getWorkingDataNode().getDataBlock() instanceof CharactersBlock
```

— and so throws rather than returning false when there is no workflow. It should return false, or better,
answer the question it is really asking ("is there an alignment behind this?") from what it has. Note that the
Python binding calls `isApplicable` before every run, so this is on the hot path for the binding even though
the application only calls it when building a menu.

### 3.2 `BootstrapTree` — needs a small design decision

Its work happens in a public static

```java
public static PhyloTree run(progress, Workflow workflow, DataNode<TreesBlock> targetNode,
                            boolean transferBootstrap, int replicates, int randomSeed, double minPercent)
```

which uses the workflow for four things: the alignment, the path, `getWorkingTaxaBlock()`, and
`targetNode.getDataBlock().getTree(1)` as the tree to annotate. Only the first two are interesting; the taxa
and the target tree are already available to the caller.

The straightforward change is a second `run` taking `(TaxaBlock taxa, PhyloTree targetTree, CharactersBlock
characters, PathSupplier path, ...)` with the existing one reading those four things out of the workflow and
delegating. **The decision is whether `run` should stay static.** It is public and static today, which is why
`BootstrapTree.compute` can be a one-liner; keeping it static keeps that, at the cost of a seven- or
eight-argument method. An instance method could take its options from the algorithm's own properties instead
and shrink the signature to four arguments. My recommendation is to keep it static and long: it is called from
exactly one place, and turning options into hidden state is how `compute` methods become hard to test.

### 3.3 A shared vocabulary, if the shape recurs

Three algorithms will then have the same pair of methods. If a fourth appears, promote `PathSupplier` out of
`BootstrapSplits` into `splitstree6.algorithms` beside `IUsesCharacters`, and give the trio an interface
(`IResamplesCharacters`, say) so that a caller can find them by type rather than by name — the Python
generator, for one, could then expose them properly instead of listing them as unsupported. **Not worth doing
for three**, and doing it early would fix a shape we have only seen from one angle.

## 4. Verification

The reproducibility fix makes this possible at all; before it, no two runs agreed.

1. **Isolate the refactor from any behavioural change.** Record the workflow-route output for a fixed seed
   before the change and after it, and require them to be *identical* — not close. This is how `b995795c` was
   checked, and it caught nothing only because the change was in fact behaviour-preserving.
2. **Cross-check the two routes.** Build the same computation twice — once as a headless workflow, once
   standalone with a hand-built path — and require the same splits, weights and confidences. For
   `BootstrapSplits` this already passes; it is the strongest single check, because the two routes share only
   the code under test.
3. **Determinism.** Five consecutive runs at one seed, byte-identical. Worth re-running after any change to
   the threading.
4. **The GUI, by hand.** Bootstrap from the application on a small dataset and confirm the support values still
   appear on the network and are the ones the headless route gives.

Note for (1) and (2): support percentages are integer counts and are exactly reproducible; **split weights are
floating-point sums whose last bits may depend on the order replicates complete**, so compare weights with a
tolerance rather than exactly, and say so in the test.

## 5. Risks and non-goals

- **This is numeric code with concurrency in it.** The failure mode is a plausible wrong number, not an
  exception. Every step needs the before/after comparison of §4.1; a change that "obviously cannot affect the
  values" still has to be shown not to.
- **Do not restructure the threading.** The pool, the seed-per-replicate array and the round-robin assignment
  are not part of this work. The one concurrency change that was needed has been made.
- **Do not change what the workflow route does.** The application must be bit-identical; only the plumbing
  underneath moves.
- **Do not invent a path for the user.** A standalone caller supplying the wrong chain gets support values for
  a computation nobody performed — silently, and they will look plausible. The API should make the caller state
  the pipeline explicitly, and the documentation should say that it must match the one that produced the input.
- **Non-goal: making bootstrap available in `splitstree.py` v1.** Once these methods exist the binding *can*
  expose them, but a Python caller must then build a path out of Java algorithm objects, which is not an API
  anybody would want as it stands. Exposing it well means a Python-side notion of a pipeline, and that belongs
  with the workflow layer of `2026-08-17_splitstree-py.md`, not here.
