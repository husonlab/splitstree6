# 20 — Purpose and the workflow design

What SplitsTree computes, the vocabulary the code is written in, and the design of the **workflow** — the
machinery that holds the data, runs the algorithms and decides what is recomputed when.

This file deliberately says **nothing about individual algorithms**. Neighbor-net, split decomposition,
consensus networks, PhyloFusion and the rest each deserve their own `2x_logic_*.md`, written when work touches
them; this file is the map that says where they plug in. §4 lists what exists and with which signature.

Verify the code against this file whenever you touch it, and report what does not match (§10).

---

## 1. What the program is for

A phylogenetic analysis starts from data about a set of **taxa** and ends with a **tree**, a **network** or a
statistic about them. The data may be an alignment, a distance matrix, a set of splits, a set of trees, a
graph, or whole genomes; the result may be unrooted (splits, split networks) or rooted (rooted trees,
reticulate networks).

SplitsTree's answer to "what is an analysis?" is:

> **an analysis is a directed acyclic graph whose nodes alternate between data and algorithms.**

Everything follows from that. The graph is the provenance of the result, so it can be displayed, edited,
saved, replayed on other data, and read out as a methods paragraph with citations. A parameter change is a
local edit to one node, and the machinery works out exactly what has to be recomputed. Nothing in the program
holds a result that is not a node of some workflow.

The user does not have to see it: menu items build and edit the workflow behind the scenes, and a casual user
never opens the workflow tab. But there is no second, hidden path — the menu items go through the same graph.

---

## 2. Vocabulary and data model

| Term | Class | Meaning |
|---|---|---|
| **data block** | `splitstree6.workflow.DataBlock` | a typed container of data: taxa, distances, splits, trees, … |
| **algorithm** | `splitstree6.workflow.Algorithm<S,T>` | a computation from a block of type `S` to a block of type `T`, parameterised by its options |
| **data node** | `splitstree6.workflow.DataNode<D>` | a workflow node holding one data block |
| **algorithm node** | `splitstree6.workflow.AlgorithmNode<S,T>` | a workflow node holding one algorithm, plus the JavaFX service that runs it |
| **workflow** | `splitstree6.workflow.Workflow` | the DAG, plus the named handles onto its distinguished nodes |
| **option** | `splitstree6.options.Option<T>` | a named, typed, reflected parameter of an algorithm (§7) |
| **view** | `splitstree6.view.IView` inside a `ViewBlock` | an interactive drawing; a leaf of the workflow |

### 2.1 The data blocks

All in `splitstree6.data`. Every one extends `DataBlock` and must implement `size()`, `getBlockName()` (the
nexus block keyword) and `createTaxaDataFilter()` (§3.2).

| Block | Holds | Key API |
|---|---|---|
| `TaxaBlock` | the taxa, one-based, as `Taxon` objects with labels and optional traits/sets | `getNtax()`, `get(t)`, `getLabel(t)`, `indexOf(label)`, `addTaxaByNames(...)` |
| `CharactersBlock` | an alignment: `char[ntax+1][nchar+1]`, a `CharactersType`, state labels, per-character weights | `getNtax()`, `getNchar()`, `get(t,pos)`, `getMatrix()` |
| `DistancesBlock` | a symmetric distance matrix, plus optional variances | `getNtax()`, `get(i,j)`, `set(i,j,v)`, `setBoth(s,t,v)`, `getDistances()` |
| `SplitsBlock` | a list of `ASplit`, a `Compatibility` value, a fit, and the circular ordering | `getNsplits()`, `get(i)`, `splits()`, `getCycle()`, `getFit()` |
| `TreesBlock` | an `ObservableList<PhyloTree>`, with `rooted` / `partial` / `reticulated` flags | `getNTrees()`, `getTree(t)`, `getTrees()` |
| `NetworkBlock` | one `PhyloGraph` plus per-node and per-edge data maps and a `Type` | `getGraph()`, `getNodeData(v)`, `getEdgeData(e)`, `getNetworkType()` |
| `GenomesBlock` | whole genomes or references to them, for the Mash/genome-context path | |
| `SourceBlock` | the list of input file names — the workflow's connection to the file system | `getSources()` |
| `ViewBlock` | an `IView`: an interactive drawing and its tab | `getView()`, `getViewTab()` |
| `ReportBlock` | text output of an analysis algorithm | `getLines()`, `setText(...)` |
| `TraitsBlock`, `SetsBlock` | traits per taxon, and named taxon/character sets; attached to the `TaxaBlock` rather than standing alone | |

Two of these are not data in the ordinary sense and are worth flagging: **`ViewBlock` holds live JavaFX
objects**, and `ReportBlock` holds text. Both are legitimate leaves of the workflow, which is what makes "the
drawing" a first-class, recomputable part of an analysis — and also what makes a workflow hard to run without
a JavaFX toolkit (§6).

`TreesBlock.getTrees()` is an `ObservableList` and `TaxaBlock.getTaxa()` likewise: mutating them from a
background thread while a view is bound to them is a bug. See §5.3.

### 2.2 Algorithms

`Algorithm<S extends DataBlock, T extends DataBlock>` carries its input and output types **as class objects**
(`getFromClass()`, `getToClass()`), because the type parameters are erased and the workflow needs to check
compatibility at run time. Every concrete algorithm implements exactly one method:

```java
public abstract void compute(ProgressListener progress, TaxaBlock taxaBlock, S inputData, T outputData)
        throws IOException;
```

Note the shape: **the taxa are always passed alongside the input**, because almost every computation needs
labels and `ntax`; and **the output block is passed in, not returned**, because the workflow owns it and the
views are already bound to it.

Four subtypes get special handling in `Algorithm.compute(progress, Collection, Collection)`, the bridge from
the generic jloda node to the typed method (`Algorithm.java:72`):

| Kind | Signature | Where |
|---|---|---|
| ordinary algorithm | `(taxa, S) → T` | everywhere |
| `Taxa2Taxa` | `(inputTaxa) → workingTaxa` | the taxa filter |
| `DataTaxaFilter<S,T>` | `(inputTaxa, workingTaxa, S) → T` | the input data filter |
| `DataLoader<S,T>` | `(SourceBlock) → (workingTaxa, T)` | the loaders, which produce a taxa block as well as data |

The same method also enforces three policies, in this order: an **empty input** prints
`Note: <algorithm>: input <block> is empty, nothing to compute` and returns without computing (except for a
view-producing algorithm, which still runs so the drawing clears rather than going stale) — deliberately not
an error, because an upstream filter that matched nothing is a legitimate state; a non-empty input that fails
`isApplicable(taxa, input)` throws `"Algorithm is not applicable to given input data"`; and an `IExperimental`
algorithm shows a one-time warning.

---

## 3. The shape of a workflow

Every workflow has the same skeleton at its top, created by `Workflow.setupInputAndWorkingNodes(...)`:
**seven nodes** — source, input taxa, input data, working taxa, working data, taxa filter, data filter —
**plus a loader** where the data came from a file, making eight. Everything the user adds hangs below it.

```
   SourceBlock                      "Input Source"        (file names)
        │
   [ Loader ]                       "Input Data Loader"   DataLoader<SourceBlock,X>
        ├──────────────► TaxaBlock  "Input Taxa"
        └──────────────► XBlock     "Input X"
                             │
   TaxaBlock "Input Taxa" ──►│
        │                    │
   [ TaxaFilter ]            │      "Taxa Filter"         Taxa2Taxa
        │                    │
        ▼                    │
   TaxaBlock                 │      "Working Taxa"
        │                    │
        ├────────────────────┤
        │                    ▼
        │           [ XTaxaFilter ] "Input Data Filter"   DataTaxaFilter<X,X>
        │                    │
        │                    ▼
        │              XBlock       "Working X"
        │                    │
        └──────► [ algorithm ]  ──► YBlock ──► [ ShowY ] ──► ViewBlock
                                       │
                                       └────► [ algorithm ] ──► ZBlock ──► …
```

Two things about this picture are load-bearing:

**3.1 There are two taxa blocks and two data blocks, and confusing them is the classic bug.** "Input" is what
was read from the file and never changes; "Working" is what everything downstream sees. Deselecting a taxon in
the GUI edits the `TaxaFilter`, which recomputes the working taxa, which recomputes the working data through
the data filter, which invalidates everything below. **An algorithm must use the taxa block it is handed** —
which is the working one — and must never reach for `workflow.getInputTaxaBlock()`. The named accessors on
`Workflow` (`getInputTaxaNode`, `getWorkingTaxaNode`, `getInputDataNode`, `getWorkingDataNode`, …) hold these
nodes **by reference**, assigned by `setupInputAndWorkingNodes` and carried across by `shallowCopy`, so they
survive a user renaming a node; the titles above remain what the GUI and the workflow file show, and are the
accessors' fall-back for a workflow assembled some other way (§10 D-3).

**3.2 Every data block type must know how to restrict itself to a subset of taxa**, which is what
`createTaxaDataFilter()` returns. This is the only reason `DataBlock` has an abstract method beyond `size()`
and `getBlockName()`, and it is what makes taxon selection work uniformly across characters, distances,
splits, trees and networks.

**3.3 The source node is not decoration.** `SourceBlock` holds the input file names, the acceptable
extensions and the multi-file and input-editor flags; it is the input data block of every `DataLoader`; and
**validating it is how loading is triggered** — `WorkflowSetup` calls `getSourceNode().setValid(true)` and the
cascade restarts the loader. It is hidden in both workflow displays, which is why it can look superfluous. A
workflow loaded from a `.stree6` file has a source node with **no** children, because there is no loader in
that case: `WorkflowNexusInput` has already parsed the input blocks and `WorkflowDataLoader` later writes new
data straight into the input nodes. That is intended (§10 D-5).

`WorkflowSetup.apply(fileName, ...)` builds the skeleton plus a sensible default pipeline for the detected
input type: characters → `PDistance` → `NeighborNet` → `ShowSplits`; genomes → `Mash` → `NeighborNet` →
`ShowSplits`; distances → `NeighborNet` → `ShowSplits`; splits → `ShowSplits`; trees → `ShowTrees`; network →
`ShowNetwork`. A host application can preempt all of this by returning `true` from
`IAppProfile.setupWorkflow(...)`.

---

## 4. The type system, and what exists

An algorithm's signature is its `(fromClass, toClass)` pair, and this is what the GUI uses to decide what may
be attached where: `AttachAlgorithm` and the data-node context menu offer exactly those algorithms whose
`getFromClass()` accepts the block in hand and whose `isApplicable(taxa, block)` returns true. Package layout
mirrors the signature — `algorithms/<from>/<from>2<to>/` — and each such package holds one abstract base class
named `<From>2<To>` and the concrete algorithms below it.

The catalogue as it stands, obtained by scanning the built jar for non-abstract `Algorithm` subclasses,
instantiating each and asking it for its own `getFromClass()`/`getToClass()` (2026-08-17):

| From ↓ To → | Taxa | Chars | Dist | Splits | Trees | Network | Genomes | Report | View |
|---|---|---|---|---|---|---|---|---|---|
| **Source** | | 1 | 1 | 1 | 1 | 1 | 1 | | |
| **Taxa** | 1 | | | | | | | | |
| **Characters** | | 2 | **18** | 3 | 1 | 1 | | 3 | |
| **Distances** | | | 1 | 3 | 4 | 3 | | 1 | |
| **Splits** | | | | 5 | 1 | | | 3 | 1 |
| **Trees** | | | 1 | 10 | **23** | | | 5 | 1 |
| **Network** | | | | | | 2 | | | 1 |
| **Genomes** | | | 1 | | | | 1 | | |

**102 concrete algorithm classes**, carrying **177 options** between them, made up of:

| | count |
|---|---|
| transformations — the algorithms proper | **75** |
| loaders (`Source2*`) | 6 |
| per-block taxa filters (`DataTaxaFilter`) + the `TaxaFilter` itself | 6 + 1 |
| view producers (`ShowSplits`, `ShowTrees`, `ShowNetwork`) | 3 |
| report producers | 12 |
| marked `IExperimental` | 2 |
| **returning no citation** | **38** |

Those 38 are worth knowing about: `getCitation()` feeds the methods text (§8), so an algorithm without one
contributes a silent hole to every analysis that uses it. Some are legitimately uncitable (filters, loaders,
views), but not 38 of them.

The counts are the interesting part of the table. **More than half of the 75 transformations sit in two
cells**: `Trees2Trees` (23) — the rooted-network machinery, PhyloFusion, Autumn, cluster and blob networks, the
normalisers, and a family of tree filters — and `Characters2Distances` (18), the distance transformations
including the seven nucleotide substitution models in the `nucleotide` subpackage. That is where the code is,
and it is where a `2x_logic_*.md` would earn its keep first.

There are 183 java files under `splitstree6.algorithms`; the rest are abstract bases, helpers and
`algorithms.utils`. The two large engines the algorithms delegate to — `compute.phylofusion` and
`compute.autumn` — live outside the algorithm packages because of their size.

Beyond the from/to pair, a handful of **marker interfaces state a property of the output**, so that downstream
algorithms can require it rather than testing for it:

| Marker | Asserts | Used by |
|---|---|---|
| `IToCircularSplits` | the splits produced are circular, and a cycle is set | `NeighborNet`, `SplitDecomposition`; required by the outline/circular layouts |
| `IToCompatibleSplits` | the splits produced are compatible (a tree) | |
| `IToSingleTree` | the trees block produced holds exactly one tree | `TreeSelector` and friends |
| `IExperimental` | not ready for users | warns once in the GUI |

`Algorithm.isApplicable(taxa, datablock)` defaults to `taxa.size() > 0 && datablock.size() > 0`; an algorithm
with a real precondition (a rooted trees block, a circular splits block, an ordered set of characters)
overrides it, and **that override is the thing that keeps a nonsensical pipeline from being offered.**

---

## 5. The execution model

This is the part to understand properly. It is small, it is entirely reactive, and it is JavaFX.

### 5.1 Validity propagates down

Every `WorkflowNode` has a boolean `valid`, true on construction. Two rules, and nothing else:

- **A data node** is valid iff all of its parents are valid (`DataNode.createParentValidListener`). It has at
  most one parent, its producing algorithm node.
- **An algorithm node** watches its parents: when a parent becomes valid and *all* parents are now valid, it
  **restarts itself**; if any parent goes invalid, it sets itself invalid
  (`AlgorithmNode.createParentValidListener`).

An algorithm node's own validity is driven by its service: when the service is `SCHEDULED` it **clears every
child data block**, and `valid` is set to `state == SUCCEEDED`.

So: invalidate one node and the invalidity floods down to the leaves, clearing data on the way; each algorithm
node then recomputes as soon as its inputs are all valid again, and the wave of recomputation follows the same
paths. There is no scheduler, no topological sort, no dependency list — it is one listener per edge. That is
the whole design, and it is why editing a parameter deep in a workflow does the right thing without anybody
writing code for that case.

The `Workflow` itself is valid iff it is non-empty and every node is valid, which is the "everything has
finished" signal; `RunWorkflow` waits on exactly that (`workflow.validProperty()`), having kicked the wave off
with `workflow.getInputTaxaFilterNode().restart()`.

### 5.2 One service per algorithm node

`jloda.fx.workflow.AlgorithmNode` owns an `AService<Boolean>` (a `javafx.concurrent.Service`) whose callable
collects the parent data blocks and the child data blocks and calls
`algorithm.compute(progressListener, inputData, outputData)`. The service supplies the `ProgressListener`,
which is wired to a `ProgressPane` in the main window's bottom bar, and gives cancellation for free: setting a
node invalid cancels its running service.

Consequences worth knowing:

- **Algorithms run off the FX application thread**, one thread per node, several nodes possibly at once.
- **`service.restart()` must be called on the FX thread.** `RunWorkflow` does
  `Platform.runLater(() -> workflow.getInputTaxaFilterNode().restart())` for exactly this reason.
- **When no toolkit is running there is no service at all.** `AlgorithmNode.restart()` then takes an inline
  path that computes on the calling thread and drives the same transitions by hand — synchronously, so the
  whole subtree is finished when it returns. See §6.1; this is the difference between "the workflow is a GUI
  thing" and "the workflow is a library".

### 5.3 Threading rules

- Compute in the algorithm, on the background thread; touch the scene graph only in `Platform.runLater`.
- A data block's contents may be replaced wholesale on the background thread — that is what algorithms do —
  but incremental mutation of an `ObservableList` that a control is bound to must be on the FX thread.
- `RunAfterAWhile` keys must be per-instance objects; see `10_context.md`.
- **A view's own jobs outlive its algorithm node.** A view starts heavy work (the tanglegram optimisation) only
  after its node has already succeeded, so `workflow.isValid()` does not mean the views have finished. Such a
  job must register with `splitstree6.utils.RunningJobs.track(service)`, and anything waiting for a complete
  result calls `RunningJobs.awaitAll(grace, timeout)`. Forgetting to track is a silent race, which is why
  `track` exists rather than add/remove by hand (§10 D-2).

---

## 6. Where the JavaFX dependency starts — measured

This determines what can be reused outside the desktop application, so it is stated as a measurement rather
than as an opinion. Every row was probed against `target/classes` plus `target/dependency` on the **plain
classpath** (no module path, no `--add-modules`).

**As of the D-1 fix of 2026-08-17, nothing in the computational path needs a toolkit.** That was not true
before, and the "before" column is kept because it explains the design of `RunWorkflow` and of anything else
written against the old constraint.

| What | Toolkit needed? | Evidence |
|---|---|---|
| Construct data blocks, fill them, read them | no | probe ran on the `main` thread |
| `new NeighborNet().compute(new ProgressSilent(), taxa, distances, splits)` | no | 7 splits, fit 100 %, on `main`, no `Platform.startup()` |
| `new NeighborJoining().compute(...)` | no | `(((a:1,b:1):3,c:1):1,d:1,e:1)` |
| `Option.getAllOptions(algorithm)` reflection | no | listed `InferenceAlgorithm : Enum = ActiveSet legal=[GradientProjection, ActiveSet, APGD, SplitsTree4]` |
| `io.readers.*` / `io.writers.*` / `io.nexus.*` | no | nexus splits block written to stdout |
| `new Workflow(null)` | no | constructed fine |
| `workflow.newAlgorithmNode(...)` | **no** — *was* YES | before: `IllegalStateException: Toolkit not initialized` from `AService.<init>` → `ProgressPane.<init>` → `Control.<clinit>`. After: constructs fine |
| `workflow.getInputTaxaFilterNode().restart()`, whole cascade | **no** — *was* YES | before: `Toolkit not initialized` from `Service.start()` → `Platform.runLater`. After: an 11-node workflow computes in **36–43 ms**, `isValid()` true, `nsplits=7`, same NJ tree |
| the same after `Platform.startup()` | works, unchanged | 9 nodes, `nsplits=7`, `workflow.isValid()` true, no window shown |
| scanning the jar and instantiating all 102 algorithms | no | §4's catalogue, about a second |

### 6.1 How the headless path works

`AService.isToolkitRunning()` probes once with `Platform.runLater(() -> {})` and caches a true result; a
toolkit cannot be shut down and restarted, so the answer only ever goes one way. `AlgorithmNode.restart()`
branches on it:

- **toolkit present** — `service.restart()`, exactly as before. The GUI path is untouched.
- **no toolkit** — `runInline()`: clear the child data blocks, `setValid(false)`, run the callable on the
  calling thread via `AService.runInline(progress)`, `setValid(true)`. These are by hand the same transitions
  that the service state listener drives, so validity propagation behaves identically.

The inline path is **synchronous and therefore recursive**: `setValid(true)` validates the child data nodes,
which restarts the algorithm nodes below them, which run inline in turn. When `restart()` returns, the whole
subtree beneath the node has been computed. That is exactly what a headless caller wants — no latch, no
polling, no completion listener — and it is why a headless run needs no equivalent of `RunWorkflow`'s
machinery. The cost is stack depth proportional to the length of the longest chain in the workflow, which for
any real workflow is a non-issue.

`Workflow.setHeadlessProgressListener(...)` supplies the progress listener for the inline path; the default is
silent, since there is no progress pane to report to.

Both changes are in **jloda3** (`jloda.fx.util.AService`, `jloda.fx.workflow.AlgorithmNode`,
`jloda.fx.workflow.Workflow`), so megan8, tegula and phylosketch2 inherit them. They only alter behaviour
where no toolkit is running — which is precisely where the code used to throw — so nothing that worked before
can change.

### 6.2 What still needs the toolkit

- **Views.** A `ViewBlock` holds live JavaFX objects; a `Show*` algorithm running headless builds nothing
  useful. This is why `RunWorkflow` is still an `Application`: it exports view state and must construct views.
- **`AService.start()`** used directly (the static `AService.run(...)` helper), which is GUI-only code.
- **Anything that touches a scene graph**, obviously.

### 6.2a Geometry is *not* on the toolkit side of the line — measured 2026-08-17

It would be reasonable to assume the layouts belong with the views. They do not. In every case the
JavaFX-heavy class is a **wrapper**: it calls a pure routine that fills a `NodeArray<Point2D>` or a
`Map<Node, Point2D>`, and only then builds `Shape`s and `RichTextLabel`s from those points. The inner routine
is directly callable and needs no toolkit. Probed on the plain classpath, no `Platform.startup()`:

| Geometry | Pure entry point | Wrapper that needs the toolkit | Result |
|---|---|---|---|
| split network, **outline** | `layout.splits.algorithms.PhylogeneticOutline.apply(progress, useWeights, taxa, splits, graph, nodePointMap, usedSplits, loops, rootSplit, rootAngle)` | `SplitNetworkLayout` | 6-taxon neighbor-net: 16 nodes, 16 edges, 11 splits used, **1 loop of size 10** |
| split network, **equal angle** | `EqualAngle.apply` → `assignAnglesToEdges` → `assignCoordinatesToNodes` | `SplitNetworkLayout` | 16 nodes, 19 edges; the six taxon points are **identical to the outline's** |
| rooted tree / **reticulate network** | `layout.tree.LayoutRootedPhylogeny.apply(tree, layout, scaling, averaging, optimizeReticulateEdges, random, nodeAngleMap, nodePointMap)` | `ComputeTreeLayout` | all four layouts (Rectangular, Circular, Radial, Triangular) placed 12/12 nodes of a PhyloFusion network, both reticulations included |
| general network | `jloda.graph.layout.GraphLayouts.getService().apply(graph, edgeWeights, result)` | `NetworkLayout` | 6/6 points, provider `jloda-fmm` |

`layout.tree.LayoutRootedPhylogeny` imports no `javafx.scene` at all. `PhylogeneticOutline` and `EqualAngle`
import only `javafx.geometry.Point2D` — a plain value class — and `GeometryUtilsFX`, which is arithmetic. So
the seven-jar set of §6.3 is unchanged.

Sanity checks that the numbers are right rather than merely produced: under `Scaling.ToScale` the horizontal
extent of every edge equals its weight to the digit (a zero-weight edge gives a zero-length segment, which is
what the degree-2 root chain of a PhyloFusion network produces), and `Scaling.EarlyBranching` on the same
network gives integer levels 0…5.

Three things the pure routines do **not** give you, all of which live in the view layer:

- **curved edges.** `layout.tree.CreateEdges` computes the control points for circular and quadratic edge
  shapes. Headless you get node coordinates, so edges are straight source→target segments.
- **label placement.** `RadialLabelLayout`, `LayoutLabelsCircular/Rectangular` measure `RichTextLabel`s and
  need the toolkit. A caller places its own labels.
- **scaling to a pane.** The coordinates are in model units (unit edge length ≈ 1), not pixels; the view
  applies `unitLength` and fits to width and height.

### 6.3 Practical consequences

1. **Any algorithm can be driven directly, headless, from anything on the JVM.** `RunPhyloFusion`
   (`splitstree6.tools`) is the worked example: read a Newick file with `NewickReader`, `new PhyloFusion()`,
   set three options, `compute(new ProgressSilent(), taxa, trees, out)`, print the network.
2. **A whole workflow can now be driven the same way** — build it, `restart()` the taxa filter, read the
   result — as long as it contains no view nodes. This is what makes the workflow engine unit-testable
   (`ai/plans/2026-08-17_test-suite.md`) and what removes the main obstacle from the `splitstree.py` workflow
   layer (`ai/plans/2026-08-17_splitstree-py.md` §5, whose layer 2 was gated on exactly this).
3. **A binding that wants only the algorithms still needs `javafx.base` and `javafx.graphics` on the
   classpath** — the options are `javafx.beans.property.*` and `jloda.fx.workflow` uses `javafx.concurrent` —
   but never their natives, because the toolkit never starts. Seven jars, 13 MB.

---

## 7. Options

An algorithm's parameters are **JavaFX properties named `optionXxx`**, declared as fields:

```java
private final ObjectProperty<InferenceAlgorithm> optionInferenceAlgorithm =
        new SimpleObjectProperty<>(this, "optionInferenceAlgorithm", InferenceAlgorithm.ActiveSet);
```

with the conventional `getOptionInferenceAlgorithm()` / `setOptionInferenceAlgorithm(v)` /
`optionInferenceAlgorithmProperty()` triple. `Option.getAllOptions(carrier)` finds them **by reflection over
method names**: any public no-argument method matching `option*Property` becomes an `Option`, whose name is
the method name stripped of `option` and `Property`. This is why the algorithm packages need `opens` lines in
`module-info.java`.

- **`OptionValueType`** classifies the property as `String`, `Integer`, `Double`, `Boolean`, `Enum` or an array
  thereof, and does the string conversion in both directions. An enum option automatically publishes its legal
  values, which is what makes the GUI show a combo box and what a binding should expose as a choice.
- **`listOptions()`** returns the names of the options to show **and in what order**; an option that exists but
  is not listed is deliberately hidden from users (`NeighborNet` hides `optionThreshold`,
  `optionCircularOrdering` and `optionActiveCleanup` this way). Hidden is not the same as absent: it is still
  settable through the API and still written to a workflow file.
- **`getToolTip(optionName)`** supplies the human description. It is what the GUI shows and what
  `ExtractOptionsText` puts into the methods paragraph, so it is documentation, not decoration.
- **`OptionIO`** reads and writes the `OPTIONS` section of an `ALGORITHM` nexus block; `Algorithm.reset()`
  restores every option to the value a freshly constructed instance has.

**The defaults are a published interface.** They are baked into every `.stree6` file only when they are
written out; changing one silently changes results for everyone who has not saved an explicit value.

---

## 8. Citations and the methods text

`IHasCitations.getCitation()` returns a string of `short citation;full citation;` pairs — the format matters,
because `splitstree6.cite.ExtractCitations` splits on it. `ExtractMethodsText` walks the workflow and produces
a prose methods paragraph naming each algorithm, its non-default options (via `ExtractOptionsText`) and its
citations; `IgnoredInMethodsText` marks the nodes that should not appear (filters that did nothing, views).

This is a genuinely unusual feature and it is cheap to break: an algorithm added without a citation, or with a
citation in the wrong format, silently degrades the methods text of every analysis that uses it.

---

## 9. Input, output and persistence

Three different things are called "reading a file" here and they are worth separating.

**9.1 Import.** `io.readers` holds one package per target block type; each reader extends `DataReaderBase<S>`
and declares its file extensions and a `isApplicable`-style content sniff. `ImportManager` (a singleton) holds
the list, and answers: what data type is this file (`determineInputType`), which readers could read it
(`getReaders`), what formats exist (`getAllFileFormats`). A reader is registered by being **added to the
`ImportManager` constructor** — there is no service loader — so a new format is one line there plus the class.
31 reader files today: FastA, MSF, Clustal, Stockholm, Phylip, CSV, Newick, Nexml, GML, and the per-block
nexus readers.

**9.2 Export.** `io.writers`, symmetrical: one package per source block type, `DataBlockWriter` subclasses,
`ExportManager` as the registry, `exportFile(writer, taxaBlock, dataBlock, exporterName)` as the entry point.
44 writer files. `IHasPrependTaxa` marks the writers that can emit a taxa block ahead of the data.

**9.3 Nexus.** `io.nexus` is the canonical form: one `<Block>NexusInput` / `<Block>NexusOutput` pair per data
block, plus `AlgorithmNexusInput`/`Output` for algorithms with their options. These are the classes that define
what a SplitsTree nexus file *is*, and they are used both by the nexus reader/writer and by the workflow file.

**9.4 The workflow file (`.stree6`).** `io.nexus.workflow.WorkflowNexusOutput` writes the whole workflow — the
`SPLITSTREE6` block, every data block, every algorithm block with its options, and the node connectivity — and
`WorkflowNexusInput` reads it back, reconstructing the DAG and instantiating algorithms by class name.
`WorkflowNexusInput.isApplicable(path)` is the test for "is this a workflow file". A `.stree6` file is
therefore both a saved analysis *and* a re-runnable program, which is what `RunWorkflow -w` exploits.

**9.5 Loading data into an existing workflow.** `WorkflowDataLoader.load(workflow, inputFile, format)` replaces
the contents of the input nodes without touching the structure. This is the operation that "apply this analysis
to that dataset" is made of, and the one a binding will want most.

---

## 10. Discrepancies and cross-cutting concerns

Things where the code and a reasonable reading of this description do not line up, or where a design decision
has a cost worth naming. Each is stated so it can be checked. All seven were put to Daniel on 2026-08-17 and
answered; items marked **FIXED** were repaired the same day. Per-item measurements are in `60_agent_notes.md`.

**D-1 — `AService` built a JavaFX `Control` in its constructor, which forced the toolkit. FIXED
(2026-08-17), and the premise was only half right.** `AService.<init>` did `new ProgressPane(this)`, and
loading `javafx.scene.control.Control` initialises the platform stylesheet, so *constructing* an algorithm
node threw `Toolkit not initialized`. The pane is now created on first use — it is a *display* of progress,
and a service that never shows it should not pay for it — which fixed construction. **It did not fix
execution**: `Service.restart()` goes through `Platform.runLater`, which needs the toolkit whatever jloda
does. So `AlgorithmNode.restart()` now takes an inline path when `AService.isToolkitRunning()` is false: it
clears the outputs, invalidates, calls `AService.runInline(progress)` on the calling thread, and validates —
driving by hand exactly the transitions the service state listener drives. Because `setValid(true)` cascades,
the inline path is **synchronous over the whole subtree**: when `restart()` returns, everything below it has
been computed, which is precisely what a headless run wants. Measured: an 11-node workflow computes in ~40 ms
with no toolkit and gives the same answers as the toolkit path (7 splits, fit 100 %, identical NJ tree); the
toolkit path is unchanged; a `RunWorkflow` run is byte-identical to before. `Workflow.setHeadlessProgressListener`
supplies the progress listener for the inline path (silent by default). Both changes are in **jloda3**, so
megan8, tegula and phylosketch2 have them too; they only alter behaviour where there is no toolkit, i.e. where
the code previously threw.

**D-2 — "the workflow is valid" did not mean "the views have finished". FIXED (2026-08-17).**
`RunWorkflow` held a `public static ObservableList` that views were expected to add themselves to, and polled
it in 500 ms steps; only `DoTanglegram` ever did, guarded by a null check, and it reached backwards from
`splitstree6.view` into `splitstree6.tools` to do so. Replaced by `splitstree6.utils.RunningJobs`: a registry
with `track(service)` — one call, wired to `runningProperty`, so a job cannot be half-registered — and
`awaitAll(graceMillis, timeoutMillis)`, which blocks on a monitor instead of polling and cannot hang forever.
Measured: a job ending at 700 ms is awaited in 705 ms (the old code's granularity was 500 ms); a 300 ms
timeout returns false at 302 ms. A tanglegram workflow run through `RunWorkflow` produces byte-identical
output across runs. *Not done, deliberately:* the completion signal was **not** put on `ViewBlock`. Views
launch their jobs from deep inside presenters, so funnelling every one through the block would be a large
refactor for a weaker guarantee — a global registry catches jobs started anywhere, not only by views.

**D-3 — the distinguished nodes were found by title string. FIXED (2026-08-17).** `getInputTaxaNode()` and
its siblings filtered on `getTitle().equals("Input Taxa")` and friends, and the GUI lets a user rename a node.
`Workflow` now holds the eight distinguished nodes by reference, assigned in all three
`setupInputAndWorkingNodes` overloads, carried across by `shallowCopy` (which is how a loaded document reaches
the live workflow), dropped when a node is deleted, and cleared by an overridden `clear()`. The accessors fall
back to the old title matching when the reference is absent, so a workflow assembled by some other route still
resolves. Measured: after renaming all six visible skeleton nodes, every accessor still finds its node; before
the fix each would have returned null.

**D-4 — `Workflow.updateTitle` looked up data-node titles in the algorithm map. FIXED (2026-08-17), and it
was not harmless.** The `DataNode` branch took its uniqueness list from `algorithmNameTitleMap` while the
node-removal listener put the title back into `dataBlockNameTitleMap`, so a data-node title was registered in
one map and freed from the other and was therefore **never** freed. Deleting a data node and creating another
of the same type gave `Splits-2`, not `Splits`. Now measured after the fix: delete-then-create reuses
`Splits`, and a second live node correctly gets `Splits-2`.

**D-5 — the source node. ANSWERED (2026-08-17): it cannot safely be removed.** Daniel asked for removal if it
were safe. It is not: `SourceBlock` is the input data block of every one of the six `DataLoader`s, it holds
the file names, the acceptable extensions and the multi-file and input-editor flags, and validating it is how
loading is triggered (`WorkflowSetup` does `getSourceNode().setValid(true)`, and the cascade restarts the
loader). It is hidden from both workflow displays, which is why it looks superfluous. What *was* wrong has
been fixed: the `// todo: what is the purpose of the source node?` is replaced by a comment that answers it,
the no-file overload now creates a source node too so `getSourceNode()` is never null, and the third
overload's childless source node — correct, because a loaded `.stree6` has no loader and `WorkflowDataLoader`
writes straight into the input nodes — is now documented as intended rather than looking like an oversight.

**D-6 — an empty input was swallowed silently. FIXED (2026-08-17).** `Algorithm.compute` now prints
`Note: <algorithm>: input <block> is empty, nothing to compute` before returning. It still does not throw:
an upstream filter that matched nothing is a legitimate state, and throwing would put an error dialog in front
of the user every time they deselect the last taxon. But an empty result in a headless run no longer looks
exactly like a computed one. The view-refresh behaviour is unchanged.

**D-8 — `ProgramProperties`'s static initialiser asks JavaFX for a font. NEW 2026-08-17, not yet put to
Daniel.** `jloda.fx.util.ProgramProperties.<clinit>` calls `javafx.scene.text.Font.font("Arial", 12)`, so
merely *constructing* an algorithm that tracks an option — `PhyloFusion` does, in an instance initialiser —
attempts to start a JavaFX graphics pipeline. The call is wrapped in `catch (Exception ignored)`, so it is
harmless, and the field is simply left null. But it costs a pipeline start-up attempt, it can spawn a
QuantumRenderer thread, and when the natives cannot load it prints a wall of `UnsatisfiedLinkError` to stderr
that looks exactly like a fatal error and is not. This is **D-1 again**: eager JavaFX in a static
initialiser, in the same jloda class family, fixable the same way by making the font lazy. Found while
running algorithms from Python with deliberately mismatched natives; the computation was unaffected, which is
itself the evidence.

**D-7 — there is no test suite at all.** Daniel: a plan is needed for generating unit tests of all features.
Written: `ai/plans/2026-08-17_test-suite.md`. Its centrepiece is that D-1 makes the *workflow itself* unit-
testable headless and synchronously, and that the catalogue scan of §4 lets one data-driven test exercise all
102 algorithms and all 177 options rather than 102 hand-written classes. Awaiting review; no test code written.
