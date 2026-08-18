# 60 — Agent notes

You don't need to read this before starting work. It's an archive of past work and findings.

Newest entries first. Record here what does not belong in `50_todo.md`: what was done and when, what was
measured, what was tried and abandoned, and why a decision went the way it did. Dead ends are worth as much as
successes — they stop the next agent repeating them.

---

## 2026-08-17 (evening) — the splitstree.py spike: JPype works, and the numbers are good

Step 1 of `ai/plans/2026-08-17_splitstree-py.md`, the time-boxed spike. Full table in that plan's §3.1;
the short version is that every one of the four questions came back favourably and nothing needs rethinking.

`nsplits=7 fit=100.0` and `(((a:1,b:1):3,c:1):1,d:1,e:1)` from Python — byte-identical to the Java probe, which
is the only result that really mattered. JVM start-up ~105 ms once per process. A 1000x1000 float64 matrix
crosses in 17 ms (numpy to JArray) + 26 ms (into DistancesBlock) and comes back in 0.7 ms, bit-exact at
`atol=0`. The same thing element-wise extrapolates to **0.8 s**, so the plan's "never a per-element call in a
loop" rule is now measured at ~19x rather than asserted. A Python `ProgressListener` via `@JImplements` works
and costs 0.06 us per callback, so progress and cancellation from Python are free.

**Three things the spike found that the plan had not anticipated, all of which improve it.**

The first is the one worth remembering. `startJVM` failed immediately with
`FileNotFoundError: JVM DLL not found: /usr/local/Cellar/openjdk@17/.../libjli.dylib` — a compiled-in guess at
a Homebrew path that does not exist on this machine. Chasing it turned up a real constraint: **JPype loads the
JVM into the Python process, so the JVM architecture must match the interpreter's.** Here `python3` is arm64
and the `java` on `PATH` is an x86_64 GraalVM 17, so this machine's default JVM cannot be used from Python at
all; the spike runs only with `JAVA_HOME` pointed at the arm64 JDK 23. Two consequences are now in the plan's
§8: `_jvm.py` must *check* the architecture rather than take the first JVM it finds, and the no-JVM error
message is the most important string in the package - it is the first thing most users will see go wrong.

Second, an accidental strengthening of the one-wheel hypothesis. The bundle carries the **x86_64** macOS
JavaFX jars, and the spike loaded them under an **arm64** JDK 23 without complaint. That is a harder test than
the portability workflow runs: not merely a different operating system but a different processor
architecture, and still nothing touched the natives. It does not replace the workflow - Windows and Linux have
their own loaders - but it is the best single piece of evidence so far.

Third, a conversion the block layer must own: `java.util.BitSet` is not iterable from Python, so
`ASplit.getA()` is unusable as it stands, and the indices are one-based on top of that. A split has to reach
Python as a `frozenset` of taxon *labels*. That was the only thing in the spike that needed fixing rather than
measuring, and it is a good worked example of why §7 exists.

Also noted and not yet solved: the algorithms print progress to stderr (`NNet algorithm: ActiveSet taxa: 5
...`). Fine in a tool, noise in a library.

**Not done, deliberately:** nothing beyond the spike. §9.4 puts the code in `husonlab/splitstree-py`, which
does not exist; creating a repository is Daniel's call, so step 2 stops here.

---

## 2026-08-17 (later still) — the layouts report coordinates headless

Daniel asked for the one thing the `splitstree.py` plan still listed as unmeasured (§9.6): can the layouts
give coordinates without a toolkit? **Yes, all of them.** Recorded in `20_logic.md` §6.2a.

The reason it looked doubtful is that `SplitNetworkLayout`, `ComputeTreeLayout` and `NetworkLayout` all import
`javafx.scene.control.Label`, `javafx.scene.shape.*` and `RichTextLabel`, so a quick grep says "this is GUI
code". Reading further shows the shape is consistently **wrapper around a pure routine**: the wrapper calls
something that fills a `NodeArray<Point2D>` / `Map<Node, Point2D>`, and only afterwards walks that map
building `Shape`s. The inner routines are public static and import at most `javafx.geometry.Point2D`, which
is a plain value class. `layout.tree.LayoutRootedPhylogeny` imports no `javafx.scene` at all.

Probed on the plain classpath, no `Platform.startup()`:

| Geometry | Entry point | Result |
|---|---|---|
| split network, outline | `PhylogeneticOutline.apply(...)` | 6-taxon neighbor-net (11 splits, circular, fit 99.9): 16 nodes, 16 edges, 11 splits used, 1 loop of size 10 |
| split network, equal angle | `EqualAngle.apply` → `assignAnglesToEdges` → `assignCoordinatesToNodes` | 16 nodes, 19 edges — the outline draws the boundary, equal angle the interior too |
| rooted / reticulate | `layout.tree.LayoutRootedPhylogeny.apply(...)` | all four layouts placed 12/12 nodes of a PhyloFusion network including both reticulate nodes |
| general network | `GraphLayouts.getService().apply(...)` | 6/6 points, provider `jloda-fmm` (JDK 17 here, so not the native `ogdf-fmmm` — the known gotcha) |

**The cross-check that makes me believe the numbers**: outline and equal angle, run independently on the same
split system, produce **identical coordinates for all six taxa** — they differ only in the interior structure,
which is exactly the documented difference between the two diagrams. And under `Scaling.ToScale` each edge's
horizontal extent equals its weight to the digit; `Scaling.EarlyBranching` on the same network gives integer
levels 0…5.

One thing that looked like a bug and was not: several edges of the PhyloFusion network came out as
zero-length segments. They are exactly the zero-weight edges — the degree-2 root chain that PhyloFusion emits
(`((((a,(b)##H1),(c)##H2),((d,##H2),##H1)))`). Under `ToScale` a zero-weight edge is a zero-length edge, which
is correct. I checked rather than assumed, by printing weight against dx for every edge.

What is *not* available headless, all of it genuinely view-layer: curved edge shapes (`layout.tree.CreateEdges`
computes control points), label placement (`RadialLabelLayout` and friends measure `RichTextLabel`s), and
scaling to a pane (the coordinates are model units, unit edge length ≈ 1). None of these blocks a Python
plotting API — a matplotlib user wants raw coordinates and places their own labels.

Net effect: the last open engineering risk in the `splitstree.py` plan is closed, and §9.6 goes from "aim to,
not yet measured" to ordinary work. Daniel then confirmed the PyPI name `splitstree` is free, which was the
only remaining unknown of any kind, so that plan is now blocked on nothing but the decision to start.

---

## 2026-08-17 (late) — D-1 to D-7 answered and addressed

Daniel answered all seven discrepancies inline in `20_logic.md`. Six led to code changes, one to a plan.
The resolutions are in `20_logic.md` §10; what follows is what was measured and what was learned.

**D-1 was the big one, and my own description of it was half wrong.** I had written that making `AService`
build its `ProgressPane` lazily "would make the entire workflow engine usable headless". It does not. The
lazy pane fixed *construction* — `workflow.setupInputAndWorkingNodes(...)` stopped throwing — but the very
next call still died:

```
IllegalStateException: Toolkit not initialized
    at javafx.application.Platform.runLater
    at javafx.concurrent.Service.runLater / start / restart
    at jloda.fx.workflow.AlgorithmNode.restart
```

`javafx.concurrent.Service.start()` dispatches its state changes through `Platform.runLater`, and no amount of
jloda-side laziness removes that. So the fix has two halves: the lazy pane, **plus** an inline execution path.
`AService.isToolkitRunning()` probes once with `Platform.runLater(() -> {})` (the only reliable test — the
obvious `Platform.isFxApplicationThread()` goes through `Toolkit.getToolkit()`, which is exactly what is
missing) and caches a true result, since a toolkit cannot be shut down and restarted. When it is false,
`AlgorithmNode.restart()` calls a private `runInline()` that clears the child blocks, invalidates, runs the
callable on the calling thread through the new `AService.runInline(progress)`, and validates — by hand the
same transitions the service state listener drives.

The consequence I did not anticipate and which turns out to be the best part: because `setValid(true)`
cascades into the children, **the inline path is synchronous over the whole subtree**. `restart()` on the
taxa filter returns only when every node below it has computed. No latch, no polling, no completion listener.

Measured, all on the plain classpath with no `Platform.startup()`:

| | |
|---|---|
| 11-node workflow (source + skeleton + NeighborNet + NeighborJoining) | **36–43 ms**, `isValid()` true |
| results | `nsplits=7 fit=100.0`, NJ `(((a:1,b:1):3,c:1):1,d:1,e:1)` — identical to the toolkit path |
| toolkit path re-run afterwards | unchanged: `nsplits=7`, valid |
| `RunWorkflow` on the phyml example | output **byte-identical** to the pre-change run |

The changes are in jloda3 (`AService`, `AlgorithmNode`, `Workflow.setHeadlessProgressListener`), so megan8,
tegula and phylosketch2 get them. Risk is low by construction: the new branch only runs where there is no
toolkit, which is where the old code threw.

**D-4 was not harmless, contrary to what I had written.** I called it "harmless today". It was not. The
`DataNode` branch of `updateTitle` registered a title in `algorithmNameTitleMap` while the node-removal
listener freed it from `dataBlockNameTitleMap` — so a data-node title was put in one map and taken out of the
other, and was **never** freed. Delete a Splits node and add another and you got `Splits-2`. After the fix,
measured: delete-then-create reuses `Splits`, and a second *live* node still correctly gets `Splits-2`. The
lesson is the ordinary one — I judged "harmless" from reading one method instead of from the pair of methods
that share the state.

**D-5 is a "no", and the evidence is worth keeping.** Daniel asked to remove the source node if it were safe.
It is not: `SourceBlock` is the input data block of all six `DataLoader`s, it holds the file names, extensions
and the multi-file / input-editor flags, and `WorkflowSetup` triggers loading by doing
`getSourceNode().setValid(true)` and letting the cascade restart the loader. Both workflow displays hide it,
which is the only reason it looks superfluous. What *was* genuinely wrong is now fixed: the `// todo: what is
the purpose of the source node?` is replaced by an answer, the no-file overload creates a source node too so
`getSourceNode()` can no longer return null, and the childless source node of a loaded `.stree6` is documented
as intended rather than looking like an oversight.

**D-2: the completion signal did not go on `ViewBlock`, deliberately.** Daniel's note suggested that. I
implemented a global registry instead — `splitstree6.utils.RunningJobs` — because view jobs are launched from
deep inside presenters (`DoTanglegram` is called from the tanglegram presenter, several layers from any
`ViewBlock`), so routing every one through the block would be a large refactor for a *weaker* guarantee: a
global registry catches a job started anywhere, not only by a view. The API is `track(service)` — one call,
wired to `runningProperty`, so a job cannot be half-registered — and `awaitAll(grace, timeout)`, which blocks
on a monitor. Measured: a job ending at 700 ms is awaited in **705 ms**, against the old code's 500 ms polling
granularity; a 300 ms timeout returns false at 302 ms. Also removes a backwards dependency —
`splitstree6.view.trees.tanglegram` no longer imports `splitstree6.tools`.

**Regressions run after every step**, all identical: the phyml `RunWorkflow` run byte-for-byte, and the
nymphoides tanglegram workflow byte-identical across two runs apart from its creation timestamp.

**One pre-existing defect surfaced and was not fixed**: `AlgorithmTabsManager.lambda$new$1` throws through
`Platform.runLater` in roughly one headless tanglegram run in three. It does not affect the output. Recorded
in `50_todo.md` rather than chased, because it is a GUI listener misbehaving in a tool and is unrelated to
anything changed today.

**D-7 produced `ai/plans/2026-08-17_test-suite.md`.** Its two load-bearing observations: D-1 makes the
workflow engine unit-testable (headless, synchronous, no toolkit, ~40 ms), which it was not this morning; and
the catalogue scan means one data-driven test can exercise all 102 algorithms and all 177 options instead of
102 hand-written classes. No test code written — the plan is a proposal.

---

## 2026-08-17 — `ai/docs` written; the JavaFX boundary measured

Daniel asked for a documentation set mirroring the one adopted in `megan8/ai`, plus an `ai/plans` directory
with a planning document for a Python package. Six files in `ai/docs` and one plan were written from the code.

**What was measured rather than assumed.** The single most consequential fact about this codebase is where its
JavaFX dependency actually begins, because it decides what can be reused headless — by a server, by an
automated build, by a language binding. It is stated in `20_logic.md` §6 as a table of probe results rather than as an opinion,
because it was easy to get wrong in both directions. Two probes, both compiled against `target/classes` plus
`target/dependency` on the **plain classpath** (no module path):

- *Probe 1, no toolkit.* Built a five-taxon `TaxaBlock` and `DistancesBlock`, ran
  `new NeighborNet().compute(new ProgressSilent(), taxa, dist, splits)` → `nsplits=7, fit=100.0`; then
  `new NeighborJoining()` → `(((a:1,b:1):3,c:1):1,d:1,e:1)`; then `Option.getAllOptions(nnet)` →
  `InferenceAlgorithm : Enum = ActiveSet legal=[GradientProjection, ActiveSet, APGD, SplitsTree4]`; then wrote
  the splits as nexus through `io.writers.splits.NexusWriter`. All of it on the `main` thread, with no
  `Platform.startup()`. **Algorithms, blocks, options reflection and IO are all toolkit-free.**

- *Probe 2, the workflow.* `new Workflow(null)` constructs fine. The very next call,
  `workflow.setupInputAndWorkingNodes(taxa, dist)`, dies:
  `ExceptionInInitializerError` at `AlgorithmNode.<init>` → `AService.<init>` → `ProgressPane.<init>` →
  `javafx.scene.control.Control.<clinit>`, caused by `IllegalStateException: Toolkit not initialized`.
  Re-run after `Platform.startup()`: 8 nodes, `restart()` on the taxa filter, `nsplits=7`,
  `workflow.isValid() == true`, no window shown, no `-Dglass.platform=Monocle` needed.

So the boundary is **exactly at `AlgorithmNode`**, and the cause is incidental: `AService` eagerly builds a
*display of* progress in its constructor. The computation has no graphical content at all. That observation is
now `20_logic.md` D-1 and `50_todo.md`'s highest-value open question, because fixing it in jloda3 would make
the entire workflow engine headless — with knock-on benefits for `RunWorkflow` (which only extends
`Application` to get a toolkit), for servers, and for the Python binding.

**An end-to-end `RunWorkflow` run was also recorded**, on the module path, as the verification recipe in
`40_testing.md`: `10taxaExample.stree6` applied to `nucleic_M2573_346x897_2006.phy` → workflow with 8 data
nodes and 5 algorithms, 346 input taxa, neighbor-net on **256** taxa in 0.3 s, 2.2 s total, `SPLITS` block with
`ntax=256 nsplits=517 fit=99.9`.

That 346 → 256 is the useful part, and it was not expected. The cause: the saved workflow's `TaxaFilter`
carries `DisabledTaxa = 'tax100' 'tax11' … 'tax99'`, and the 346-taxon file happens to use the same `taxN`
naming, so **90 taxa were matched by name and silently dropped**. Confirmed by counting: exactly 90 taxon names
in the phylip file match `tax11`…`tax100`. A saved taxa filter is applied by name to whatever dataset you point
the workflow at, which is reasonable behaviour and a very good way to verify the wrong thing. Recorded in both
`30_tools.md` and `40_testing.md`. The same run also showed that unknown options in an old workflow file are
skipped with a warning, not an error (`UsePreconditioner`, `UseDual`, `Normalize`, `ShowConfidence`), so a
renamed option silently reverts to its current default.

**The algorithm catalogue was counted, not estimated.** A third probe scanned the built jar for entries under
`splitstree6/algorithms/`, kept the non-abstract classes assignable to `Algorithm`, instantiated each with its
no-argument constructor and asked it for `getFromClass()`, `getToClass()`, `getCitation()`,
`Option.getAllOptions(...)` and its `IExperimental` status. It runs in about a second and needs **no Java-side
change of any kind** — which incidentally settles the one open engineering question in the `splitstree.py`
plan's generator design (§6): discovery is a jar scan, there is no registry to read and none is needed.

Result: **102 concrete algorithm classes carrying 177 options** — 75 transformations, 12 report producers,
7 taxa filters, 6 loaders, 3 view producers, 2 marked `IExperimental`. Two cells hold two thirds of them:
`Trees2Trees` (23) and `Characters2Distances` (18, including the seven nucleotide substitution models in the
`nucleotide` subpackage). The full signature table is `20_logic.md` §4.

The unexpected number is **38 algorithms returning no citation**. `getCitation()` feeds `ExtractCitations` and
therefore the methods text, so each is a silent hole in every analysis that uses that algorithm. At most 16 of
the 102 (filters, loaders, view producers) are legitimately uncitable, so roughly 22 real algorithms are simply
missing theirs. Recorded in `50_todo.md` P3.

**Build facts checked, not copied from megan8.** `mvn -o clean compile -q` 32 s; incremental `mvn -o package -q`
8 s, and `copy-dependencies` is bound to `package`, so there is no separate invocation (`mvn clean` removes
`target/dependency`, which then has to be rebuilt before anything uses the module path). The module path
resolves as it stands — `java --module-path target/SplitsTree-1.0.0-SNAPSHOT.jar:target/dependency
--add-modules splitstreesix …` starts the tools — even though both classified and unclassified JavaFX jars are
copied; megan8 needed explicit exclusions for the same duplication, this project does not, today. The JavaFX
native classifier follows the JVM Maven itself runs on: here an x86_64 GraalVM 17 on an arm64 Mac, giving
`-mac` (Intel) jars, which is why the default `java` works while `splitstree-env` would refuse it.

**Six discrepancies recorded** in `20_logic.md` §10 and listed in `50_todo.md`. None has been put to Daniel.
Four are one-line observations (the title-string node lookup; `updateTitle` using the algorithm map for data
nodes; the source node whose purpose the code itself queries in a `// todo:`; the silent empty-input path); one
is the view-completion poll; one is D-1 above.

**What was deliberately not written.** No `2x_logic_*.md` files. Daniel asked for the overview only — "just
give an overview of the main workflow design, without detailing all the algorithms" — and writing algorithm
descriptions from the code without his review would produce exactly the kind of document that
`10_context.md` rule 2 exists to prevent. `20_logic.md` §4 lists what exists and with which signature, and the
file table in `10_context.md` says the `2x_` files are to be added one subsystem at a time.

---

## Before 2026-08-17 — carried over from session memory

Not written at the time; recorded here so the history is in one place.

**PhyloFusion tree tracing (7.2026).** PhyloFusion has an internal, non-user switch
`PhyloFusion.setTreeTracing(true)`: every reticulate edge of the reported network is tagged with the ids of the
input trees that route a lineage through it, node ids completed by upward closure, and the network printed to
stderr as extended Newick with `TT` comments (one-based tree ids), as consumed by PhyloParallelograms.

The design decision worth remembering is the one that was *rejected*. Tree tracing is Banu Cetinkaya's
contribution, living in `splitstree6/xtra/phyloFusionTreeTrace`. We first tried **reconstructing** the traces
from the per-tree hyper-sequence table the main algorithm already builds — and it over-tags reticulate nodes
whenever a taxon lands in two elements of one lane, which happened on ~65 % of random multi-reticulation
inputs. So we did the **faithful port** instead: copied her metadata-carrying hyper-sequence, SCS and network
builder into new, attributed classes in `splitstree6.compute.phylofusion` (`TracedHyperSequence`, `TracedSCS`,
`TracedNetwork`, `TreeTracing`, `TracedEdgeWeights`), leaving `xtra` untouched. Validated: 120/120 on the
disjoint+covering invariant, 118/120 byte-exact against her reference *including branch lengths* (the two
differences are two equally-optimal networks, not errors).

Branch-length fitting became the real user option `optionEdgeWeights`
(`None`/`Average`/`LeastSquares`/`LeastAbsolute`/`LeastAbsoluteZeroReticulations`, default `LeastSquares` =
NNLS via ojAlgo, falling back to Average if the solver cannot solve). Tracing is computed whenever TT output is
on **or** `optionEdgeWeights != None`. The old brute-force `NetworkUtils.setEdgeWeights` was retired from
PhyloFusion but is still used by `AutumnAlgorithm`; `optionCalculateWeights` and `optionNormalizeEdgeWeights`
were removed.

**The blank-tree-view bug (7.2026).** `TreeViewPresenter` keyed its layout debounce by `tree.get().getName()`.
`jloda.fx.util.RunAfterAWhile` is a **single process-wide static debouncer** — `apply(key, runnable)` does
`keyJobMap.put(key, job)`, so a later call with an equal key silently drops the earlier runnable. Several
TreeView panes showing equally-named trees (PhyloFusion networks are all called `N1`) therefore overwrote each
other's pending layout job and opened blank, nondeterministically. Tanglegram, TreePages and DensiTree were
immune because they key by a per-instance object. Fixed by keying on `this`. **Diagnostic heuristic worth
keeping: if some panes render blank intermittently while others are fine, suspect a shared or content-derived
`RunAfterAWhile` key first.**

**fmmm-layout (8.2026).** Native OGDF FMMM ("FM3") layout, in the separate repository `husonlab/fmmm-layout`,
reaches SplitsTree through the `jloda.graph.layout.GraphLayoutService` SPI in `jloda-core` — so `jloda-core`
stays free of any native dependency and Gluon/iOS falls back to jloda's own Java layout. SplitsTree's
`NetworkLayout` was migrated to `GraphLayouts.getService().apply(...)`; `InitialTreeLayout` was not.

Two traps that cost hours. **JDK:** the provider needs JDK 22+ (FFM/Panama); the dev `JAVA_HOME` is a Gluon
GraalVM 17, on which the provider silently fails to load and `jloda-fmm` is used instead — hence the startup
line `Graph layout: ogdf-fmmm | jloda-fmm`. Build and run desktop on JDK 23, and pass `-Pdesktop` on the
command line (the IntelliJ Maven-panel toggle does not carry to a terminal `mvn`). **IntelliJ:** after the
`fmm-layout` → `fmmm-layout` rename, re-importing silently reverted SplitsTree to `jloda-fmm` in three separate
ways — the `desktop` profile got de-selected (pin it in `.idea/workspace.xml` under `MavenProjectsManager` /
`enabledProfiles`), `.idea/misc.xml` resurrected a phantom `originalFiles` entry for the deleted pom, and an
incremental Maven reload skipped a dependent module whose pom mtime had not changed (fix: right-click that
module → Reload project, or Invalidate Caches). `mvn dependency:tree` proving the pom correct is what isolates
the fault to the IDE cache. Only edit `.idea/*.xml` with IntelliJ closed.

Naming, deliberately: OGDF's is **FMMM** (three M's); jloda's own `jloda.graph.fmm.FastMultiLayerMethod` is
**FMM** (two M's) and a different algorithm. Do not blanket-rewrite one into the other.

**`splitstree6.xtra` is read-only (7.2026).** Experimental and student work is kept there as a stable
reference. Production integrations are self-contained copies in the appropriate main package, with attribution
— never a `git mv` or an in-place refactor. This is a standing instruction from Daniel, now `10_context.md`
rule 9.
