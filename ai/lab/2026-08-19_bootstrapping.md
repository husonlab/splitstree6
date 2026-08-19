# Plan — bootstrapping: finish separating the computation from the workflow

**Status:** DONE, 2026-08-19. `BootstrapSplits` `b995795c`, `BootstrapTreeSplits` `f4e4e885`, `BootstrapTree`
`0cd540cb`. All three now read the alignment and the pipeline out of the workflow in their old entry point and
delegate to a form taking both explicitly; each was checked by requiring the workflow route to produce output
identical to before, and by running the same computation with no workflow at all and requiring the same answer.
`run` stayed static in `BootstrapTree`, per Daniel. **A second pass on 2026-08-19 gave an outside caller a way
to state the pipeline, and exposed all three in `splitstree.py` — §6 below, which is where the numbers are.**
The rest of this document is kept as the record of why the shape is what it is — §2 in particular, which
explains why the pipeline is a supplier and why the algorithms inside it are shared.
**Raised by:** Daniel, 2026-08-19, after the same construct was applied to the simpler cases in `f2ed09c2`.
**Related:** `../docs/20_logic.md` §3 and §10, `ai/open/todo.md`, `ai/lab/2026-08-17_splitstree-py.md`.

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

---

## 6. Second pass, 2026-08-19 — the interface a caller without a workflow meets

§3 left the three algorithms callable but not *usable* from outside: a caller had to hand-build an
`ArrayList<Pair<Algorithm, DataBlock>>`, know that the buffers must be fresh per thread and that the algorithms
must not be, and know that a chain ending in trees needs a `TreeSelectorSplits` appended when splits are being
aggregated. That is not an API anybody would get right, and §5 said so. This pass fixed it and then used it.

### 6.1 What was added

- **`BootstrappingUtils.newPathSupplier(algorithms, targetClass)`** — the counterpart of `extractPath` for a
  caller with no workflow. It takes the chain as configured `Algorithm` objects and returns the supplier the
  three `compute` methods want. It checks the three things that *can* be checked — that the chain starts at
  characters, that each step accepts what the one before produces, and that it ends in the block being
  aggregated — and completes a trees-producing chain with a `TreeSelectorSplits` exactly where the workflow
  entry points do. It cannot check the one thing that matters most, that this is the chain that produced the
  input; §5's warning stands and is now in the javadoc.
  **The appended selector is constructed inside the supplier, not once**: it rewrites its own `optionWhich`
  against the block it is handed, so one shared instance across threads is the very race §2 warns about.
- **`BootstrapTree.compute(progress, taxa, inputTrees, outputTrees, characters, pathSupplier)`** — the same
  six-argument shape the other two already had, so a caller meets one signature rather than three. The old
  four-argument form is untouched and still does the workflow archaeology.
- **`isApplicable` in `BootstrapSplits` and `BootstrapTree`** now answers false instead of throwing
  `NullPointerException` when there is no workflow, as `BootstrapTreeSplits` was made to do in `f4e4e885`.
  Callers ask before every run.

### 6.2 Verification — §4.2, run

The cross-check §4 called the strongest single check: the same bootstrap built twice, once as a headless
workflow and once standalone with the pipeline stated, sharing only the code under test.

`examples/programs/splitstree4/{algae,bees,primates}.nex` (8×920, 6×677, 12×898), 100 replicates, seed 42,
all three algorithms:

|                                |                                                                              |
|--------------------------------|------------------------------------------------------------------------------|
| split sets                     | **identical**                                                                |
| confidences                    | **identical**, exactly                                                       |
| weights                        | **identical** — worst absolute difference **0.00**, against a 1e-9 tolerance |
| `BootstrapTree` annotated tree | **identical string**, branch lengths and support values included             |
| 5 standalone runs at seed 42   | identical splits, weights and confidences                                    |

The working alignment was checked to be cell-for-cell the input alignment first, or the comparison would have
been between two different datasets.

### 6.3 Two things the check found that the change did not cause

**The cycle is not reproducible, and neither is the order the splits come back in.** 10 runs at seed 42, same
build, same data: **10 distinct split orders and 2 distinct cycles**, on each of the three datasets. The values
are stable; their arrangement is not. `BootstrapSplits` aggregates into a `ConcurrentHashMap` and then iterates
`keySet()`, so the order depends on which thread inserted when, and that order feeds both `DimensionFilter` and
`computeCycle`. This predates the change and is in the shared code, so both routes have it. It also means the
journal's "five consecutive runs are byte-identical" from the `b995795c` fix is true of the *values* and not of
the file: a saved `SPLITS` block can differ between two runs of one analysis, occasionally in its `CYCLE` line.
Not fixed — a deterministic order would change what everybody's bootstrap prints, which is Daniel's call.

**Bootstrapping leaves `System.out` and `System.err` pointing at a discarding stream** — which is open question
Q1 (D-12), and the answer is concurrency. Its own entry: `2026-08-19_stream-hiding-race.md`.

### 6.4 `splitstree.py`

The non-goal in §5 — "not in v1, because a Python caller would have to build a path out of Java algorithm
objects" — no longer applies, because `newPathSupplier` takes the chain as a list. All three are now exposed
(`husonlab/splitstree-py`):

```python
boot = st.bootstrap_tree(tree, characters=chars,
                         pipeline=[st.HammingDistance(), st.NeighborJoining()],
                         replicates=100, random_seed=42)
```

`splitstree/_bootstrap.py` holds the one hand-written base class; the generator emits the three algorithms
against it, so their options and documentation still come from the Java classes. They are out of
`tests/test_algorithms.py::NEEDS_WORKFLOW`, which is now empty. 13 new tests, the last of which builds the same
analysis as a SplitsTree `Workflow` through JPype and requires the Python call to give the same annotated tree
— §4.2 again, from the other side of the binding. Suite: **424 passed, 26 skipped**, from 411 passed, 23
skipped and 3 xfailed.

§3.3's shared vocabulary — promoting `PathSupplier` and adding an `IResamplesCharacters` interface — was **not**
done, and is still not worth doing for three. The Python side finds them by class name in a three-entry table.
