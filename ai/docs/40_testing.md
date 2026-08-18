# 40 — Testing and verification

How a change is shown to be correct. **There is no test suite in `splitstree6`** — no `src/test`, no JUnit
dependency, no test runner, and nothing that checks a commit compiles (the single GitHub Actions workflow,
`.github/workflows/deploy-docs.yml`, only rebuilds the Pages site when `docs/` changes; the installer
workflows in `build-installers` are started by hand, not by a push). That is the honest
starting point, and it means the burden of verification falls entirely on what you do by hand in the session.
Everything below is about doing that well.

A plan for building one exists — `ai/plans/2026-08-17_test-suite.md` — and is waiting to be read. Note that
its premise changed on 2026-08-17: with the workflow now runnable headless and synchronously (§ below), the
engine itself became unit-testable, which it was not before.

## Principles

1. **Run it. Do not conclude from reading.** Reading the code is how you form a hypothesis; running it is how
   you find out. This project makes running cheap — see the probe pattern below — so there is no excuse.
2. **Every claim is a number.** "The splits look better" is worthless. "Neighbor-net on the 346-taxon phyml
   alignment went from 517 splits at fit 99.9 % to 519 at 99.9 %, and the cycle is unchanged" is a result.
   Numbers go in the commit message and in `60_agent_notes.md`.
3. **Show that nothing else moved.** Every change should be accompanied by a run of an unrelated configuration
   that must *not* change, and that number should come out identical.
4. **Reproduce the failure before fixing it**, then, having fixed it, **re-break it deliberately** and confirm
   the number goes back. That is the only way to know it was your change that fixed it.
5. **Test both halves of the boundary.** An algorithm can be exercised directly (fast, no toolkit) and through
   the workflow (slow, JavaFX, but the only way to test propagation, options round-tripping and the views).
   A fix verified only by a direct call is verified only for direct calls; the workflow adds the filters, the
   taxa mapping and the option deserialisation, and those are where the interesting bugs are.
6. **Verify a pure function directly.** Where a routine has a closed form or a checkable invariant, exercise it
   against the compiled classes with a ten-line harness rather than trusting it. `LCAAddressing`-style pure
   code exists here too: `Compatibility`, `ASplit`, `SplitsBlockUtilities`, `GreedyCircular`,
   `TreeMutualRefinement`, `ClusterUtils`, `SplitNewick`.
7. **Do not verify what is already known to work.** Do not round-trip an emitted phylip or nexus file through
   SplitsTree's own reader to "confirm it loads" — it reads those formats, that is not in question. Spend the
   effort on the thing you changed.
8. **Say what you did not check.** A change to an unrooted algorithm that you did not run on a rooted network,
   or a change to the workflow that you did not open in the GUI, should be reported that way.

## The probe pattern — the main tool

Because algorithms need neither the workflow nor the JavaFX toolkit (`20_logic.md` §6), the fastest possible
verification loop is a `main` compiled against `target/classes` on the **plain classpath**. No module system,
no `--add-modules`, no toolkit, no window, milliseconds to run. This is how the two facts in `20_logic.md` §6
were established.

```bash
CP="target/classes:$(ls target/dependency/*.jar | tr '\n' ':')" && javac -cp "$CP" -d /tmp/probe/out /tmp/probe/Probe.java && java -cp "$CP:/tmp/probe/out" Probe
```

A probe that runs neighbor-net and neighbor-joining on a five-taxon matrix, prints the reflected options and
writes the splits as nexus, in full:

```java
var taxa = new TaxaBlock();
taxa.addTaxaByNames(List.of("a", "b", "c", "d", "e"));
var dist = new DistancesBlock();
dist.setNtax(5);
// ... dist.set(i, j, v) with ONE-BASED i, j ...

var nnet = new NeighborNet();
var splits = new SplitsBlock();
nnet.compute(new ProgressSilent(), taxa, dist, splits);
System.err.println("nsplits=" + splits.getNsplits() + " fit=" + splits.getFit());

for (var o : Option.getAllOptions(nnet))
    System.err.println(o.getName() + " : " + o.getOptionValueType() + " = " + o.getProperty().getValue());

var w = new OutputStreamWriter(System.out);
new NexusWriter().write(w, taxa, splits);
w.flush();
```

Verified output on 2026-08-17: `nsplits=7 fit=100.0`, NJ tree `(((a:1,b:1):3,c:1):1,d:1,e:1)`, and a nexus
splits block with `CYCLE 1 2 5 4 3`. Keep small fixtures like this one — a matrix whose answer you can work
out on paper is worth more than a big file whose answer you cannot.

Three things make this pattern work, and they are worth knowing:

- **`ProgressSilent`** (`jloda.util.progress`) satisfies the `ProgressListener` argument with no output;
  `ProgressPercentage` if you want the percentages.
- **A probe in the same package** sees package-private classes; the plain classpath sidesteps `module-info`
  entirely, so nothing needs `opens` or `exports`.
- **Readers and writers work here too** — `new NewickReader().read(new ProgressSilent(), file, taxa, trees)`
  fills a taxa block and a data block from a file with no workflow at all. `RunPhyloFusion`
  (`splitstree6.tools`) is a full worked example of exactly this shape and is the file to copy from.

Do **not** put a probe in `splitstree6.xtra` and do not commit probes into `src/main`; keep them in a scratch
directory.

## Testing through the workflow

Some things can only be checked with the whole machine running: option round-tripping through a `.stree6` file,
taxa-filter propagation, applicability gating, and anything to do with views. The cheap way is `RunWorkflow`,
which is a real headless end-to-end run.

```bash
java --module-path target/SplitsTree-1.0.0-SNAPSHOT.jar:target/dependency --add-modules splitstreesix splitstree6.tools.RunWorkflow -w examples/large_DNA_phyml/10taxaExample.stree6 -i examples/large_DNA_phyml/nucleic_M2573_346x897_2006.phy -n Splits -e Nexus -o out.nex
```

Verified on 2026-08-17: loads a workflow with 8 data nodes and 5 algorithms, reads 346 taxa, runs neighbor-net
on 256 of them in 0.3 s, whole run 2.2 s, and writes a `SPLITS` block with `ntax=256 nsplits=517 fit=99.9`.

That run also demonstrates the two traps documented in `30_tools.md` and worth repeating here, because both
produce a *plausible wrong answer* rather than an error:

- **the saved `TaxaFilter` is applied by name** — 90 taxa named `tax11`…`tax100` were disabled by the workflow
  and silently dropped from the new dataset;
- **unknown options are skipped with a warning** — a renamed option reverts to its default without failing.

Always read the taxon counts the tool prints. If they are not what you expect, the analysis you verified is not
the analysis you thought you were verifying.

`examples/publications/` is the best regression material in the repository: each dataset is paired with the
`.stree6` that reproduces a published figure, so "does this still give the published answer" is a question you
can actually ask. Nothing automates it today.

To exercise the workflow *engine* from a probe, just build it and restart the taxa filter. Since the D-1 fix
of 2026-08-17 this needs **no toolkit and no `Platform.startup()`**, and it is **synchronous**: when
`restart()` returns, the whole workflow has been computed.

```java
var workflow = new Workflow(null);
workflow.setupInputAndWorkingNodes(taxa, distances);
var splitsNode = workflow.newDataNode(new SplitsBlock());
workflow.newAlgorithmNode(new NeighborNet(), workflow.getWorkingTaxaNode(), workflow.getWorkingDataNode(), splitsNode);
workflow.getInputTaxaFilterNode().restart();          // synchronous, whole cascade
assert workflow.isValid();
```

Verified on 2026-08-17: an 11-node workflow computed in **36–43 ms** on the `main` thread with no toolkit,
`isValid()` true, `nsplits=7 fit=100.0`, NJ tree `(((a:1,b:1):3,c:1):1,d:1,e:1)` — the same answers the
toolkit path gives. This is what makes the workflow testable at all; see
`ai/plans/2026-08-17_test-suite.md`.

A probe that *does* want the toolkit — because it builds views — still starts it the old way
(`Platform.startup(latch::countDown)`), and that path is unchanged.

## Testing the GUI

There is no automated GUI testing and there is not going to be one soon. What is worth doing by hand after a
change that touches views or the workflow tab:

- open a dataset, check the workflow tab shows what you expect, change an option in an algorithm tab and
  confirm the right subtree recomputes and the drawing updates;
- deselect a taxon and confirm the change propagates all the way to the views;
- save as `.stree6`, reopen, and confirm the options survived the round trip;
- check the methods text (it is derived from the workflow, so a new algorithm without a `getCitation()` shows
  up here immediately).

`RunAfterAWhile`-related bugs are intermittent and timing-dependent: if a view renders blank *sometimes*,
suspect a shared or content-derived debounce key first (`10_context.md`).

## Known gaps in the verification

State these honestly rather than implying broader coverage than we have:

- **No unit tests at all**, and no test source root to put one in. The split arithmetic (`ASplit`,
  `Compatibility`, `SplitsBlockUtilities`), the circular-ordering and greedy routines, the tree utilities and
  the nexus round trip are all pure and checkable, and none of them is tested.
- **No stored baselines.** Unlike megan8 there is no recorded reference output for any dataset, so "did this
  change anything?" can only be answered by running the before and after in the same session. If you are about
  to make a change with wide reach, record the *before* numbers first — you cannot recover them later.
- **The example workflows are old.** Several carry options that no longer exist (`UsePreconditioner`,
  `UseDual`, `Normalize`, `ShowConfidence`), which the loader skips with a warning. They still exercise the
  pipeline, but they do not pin down current defaults.
- **The rooted-network side is much less exercised than the unrooted side** by whatever informal testing
  happens: `Trees2Trees` alone holds 23 of the 102 concrete algorithms, including PhyloFusion, the Autumn
  algorithm, cluster and blob networks, and the normalisation code.
- **Nothing checks the writers against the readers.** 31 readers and 44 writers, and no round-trip test for
  any pair.
- **Nothing checks that a `.stree6` file written by this version reloads into this version**, let alone across
  versions, even though every user's saved analysis depends on it.
