# 10 — Context, team and working rules

Read this file first. It says what the project is, who does what, how code and documentation are to be
written, and what the other files in this directory contain, so that you can decide which of them to read.

## The project

**SplitsTree App** (`splitstree6`, also called SplitsTree6, sixth reimplementation of SplitsTree) computes and
visualises **phylogenetic trees and networks**, both unrooted and rooted. Its input is a set of taxa together
with characters (an alignment), distances, splits, trees, a network or whole genomes; its output is a tree, a
split network, a rooted network, a report or a drawing.

What distinguishes it from its predecessors — and from most programs in the field — is that **an analysis is an
explicit object**. Data and algorithms are nodes of a bipartite DAG, the **workflow**; changing a parameter
invalidates the affected subtree and recomputes it; the workflow can be saved to a `.stree6` file and re-run on
other data, from the GUI or from the command line. The workflow is also read back out as prose: a methods
paragraph with the citations of every algorithm that contributed (`splitstree6.cite`). `20_logic.md` describes
this machinery, which is the thing to understand before anything else.

The program has three faces:

- a **JavaFX desktop application** (`splitstree6.main.SplitsTree6`), which is what most users see: a main
  window per document, a workflow tab, an algorithm tab per algorithm node, and a view tab per view node;
- a set of **command-line tools** in `splitstree6.tools` (`RunWorkflow`, `ExportWorkflow`, `GenomeContext`,
  `ComputeMashSketches`, `SampleTrees`, `BloomFilterTool`, the `tools.server` webserver), launched through the
  shell scripts in `splitstree6-tools/tools` (installed layout) or `tools/` (installer templates);
- an **embeddable engine**. `IAppProfile` (`splitstree6.main`) lets a host application filter the algorithm
  list, restrict the readers, override the default workflow and trim the menu bar. RazorNet is such a host.
  Anything a host can do, a language binding can do; this is the seam the `splitstree.py` plan builds on
  (`ai/lab/2026-08-17_splitstree-py.md`).

There is one architectural fact that governs almost every decision in this codebase, and it is worth stating
before anything else:

> **The computation is plain Java and runs anywhere; only the views are JavaFX.**
> `algorithm.compute(progress, taxaBlock, input, output)` runs on any thread with no toolkit started, and
> since 2026-08-17 so does a whole workflow: with no toolkit, `AlgorithmNode.restart()` computes inline and
> synchronously instead of going through a `javafx.concurrent.Service`. What still needs a toolkit is anything
> that builds a `ViewBlock`. Measured, both before and after; see `20_logic.md` §6.
>
> Before that date the boundary sat at `workflow.newAlgorithmNode(...)`, which threw
> `IllegalStateException: Toolkit not initialized`. Code written against the old constraint — `RunWorkflow`
> being a `javafx.application.Application`, above all — still reflects it.

Related repositories, all by the same author, whose conventions this code follows:

- **jloda3** (`~/IdeaProjects/apps/jloda3`) — `jloda-core`, `jloda-fx`, `jloda-phylogeny`, `jloda-connect`
  (and `jloda-swing`, `jloda-megan`, used by other apps). `jloda-phylogeny` holds `PhyloTree`, `PhyloGraph`,
  `PhyloSplitsGraph` and the tree/graph algorithms; `jloda-core` holds the utilities (`Basic`, `StringUtils`,
  `NumberUtils`, `FileUtils`, `CollectionUtils`, `IteratorUtils`, `Single`, `Pair`, `ProgressListener` and its
  implementations, `ProgramExecutorService`, the `GraphLayoutService` SPI); `jloda-fx` holds the generic
  workflow engine (`jloda.fx.workflow.Workflow`, `WorkflowNode`, `DataNode`, `AlgorithmNode`) and the JavaFX
  controls. **A change to the graph or tree data structures lives in jloda3, not here**, and is not visible to
  `splitstree6` until jloda3 has been re-installed.
- **fmmm-layout** (`husonlab/fmmm-layout`) — the native OGDF FM3 graph layout, discovered at runtime through
  the `jloda.graph.layout.GraphLayoutService` SPI. Desktop-only, pulled in by the `desktop` Maven profile.
- **megan8**, **tegula/teguladesign**, **phylosketch2**, **razornet** — the other applications sharing the
  jloda style. RazorNet embeds SplitsTree6 through `IAppProfile`.
- **build-installers** — the installer builds, one `apps/<app>/config.json` per application.

The consequence is a rule you must respect: **generic graph, tree and utility code goes in jloda3; data
blocks, algorithms, IO formats, views and the workflow specialisation go in `splitstree6`.** Do not copy a
graph algorithm into `splitstree6` to avoid the split.

## The team

**Daniel Huson — algorithms and software design.** Owns the mathematics, decides what is to be built and how it
should look, and has the final say on design and on what counts as correct. Wrote SplitsTree, MEGAN,
Dendroscope, jloda and the surrounding programs, whose conventions this code follows. 

**David Bryant** is the
co-author of SplitsTree4/6 and of the NeighborNet work, and several algorithms here carry his name in their
citation; he is not a day-to-day contributor to this repository. Parts of `splitstree6.xtra` are student work
(see the rule below).

**Banu Cetinkaya** is a PhD student who is working on the PhyloFusion algorithm and its implementation.
She does not work on other parts of the code.

**AI agent (you) — implementation, verification and documentation.** You write and change code, you verify it,
and you keep these documents current. You do not invent algorithms and you do not invent phylogenetics. If the
implementation and a `2x_logic_*.md` file disagree, **say so**; do not quietly rewrite the description to match
the code, and do not quietly change the code to match a description you are unsure of. Ask.

## Working rules for the AI agent

The documentation is in three layers, and the layer a fact goes in is decided by **how sure we are of it**:
`docs/` is what we believe true, `../open/` is what we do not yet know, `../lab/` is the record of what was
tried. See `../README.md` for the map.

1. **Start here.** Read this file, then whichever of the numbered files below are relevant. If you are
   touching anything that runs inside the workflow, `20_logic.md` is always relevant. Then read
   `../open/questions.md` and `../open/todo.md`.
2. **Search the ledger before investigating anything.** `../lab/INDEX.md` has one row per question ever asked
   here, with its verdict. A refuted idea is recorded precisely so that it is not tried twice; if what you are
   about to pursue is already there, say so rather than repeating it.
3. **Verify against the description.** When you work on something a logic file describes, check that the code
   actually does what is written there, and report **every** discrepancy you find, even the ones you are not
   fixing. Each logic file ends with a discrepancy section; add to it.
4. **Ask a question, not a plan.** Research work starts from something that could turn out to be false — "does
   the layout report coordinates with no toolkit?", not "make it headless". Open an entry from
   `../lab/TEMPLATE.md` (or run `/probe`), and **write down what would settle it before you measure**. A plan
   is what an entry becomes once the answer is "build this"; it is an outcome of investigation, not its
   precondition. Small, local fixes need no entry — `../open/todo.md` is enough.
5. **Verify by running, not by reading.** See `40_testing.md`. Reading the code is how you form a hypothesis;
   compiling a ten-line probe against `target/classes` and looking at the splits it produces is how you find
   out. This project has no unit tests, so this rule carries more weight here than usual — and note that the
   JavaFX boundary, the single most consequential fact in this codebase, was established by measurement and
   contradicted what the imports suggested.
6. **Record the outcome, including the failures.** An idea is finished when `../lab/INDEX.md` has a row with a
   verdict — `confirmed`, `refuted`, `inconclusive`, `shipped`, `reverted` or `parked` — and every number
   worth comparing later is in `../lab/measurements.md`. "We tried it and it did nothing" is a result, and the
   most expensive kind to rediscover.
7. **Change the documentation with the code.** A change is not finished until the relevant logic file, the
   class javadoc and the in-code comments say the new truth, and `../open/todo.md`, `../lab/journal.md` and
   `../lab/INDEX.md` have been updated.
8. **Be specific.** "The splits look wrong" is useless. Name the file, the method, the line, the option, the
   dataset, the measured numbers, and the case that fails. When you report a result, give the number — and the
   size of the sample it came from.
9. **Report honestly.** If something is half-done, broken, slower than before, or was fixed for a different
   reason than you first claimed, say so plainly.
10. **Keep the diff minimal.** Do not reformat, re-indent or "tidy" code you were not asked to change. A
    whitespace-only diff hides the real change.
11. **Never silently change a default.** Every `optionX` default is a published parameter that users' results
    depend on, and it is written into every `.stree6` file ever saved. Changing one changes everybody's
    answers and can change how an old file reloads.
12. **Do not move or edit anything under `splitstree6.xtra`.** That package holds experimental and student
    work (for example `xtra/phyloFusionTreeTrace`, contributed by Banu Cetinkaya) and is kept as a stable
    reference. When a feature needs code from there, **copy** it into a new class in the appropriate main
    package — as `compute/phylofusion/Traced*.java` did — keeping the attribution. Never `git mv` out of
    `xtra`, never refactor it in place.

## Coding guidelines

The code follows the conventions of SplitsTree, jloda and Daniel Huson's other programs. Match them.

### File header and class documentation

Every source file begins with the GPL header, whose first line names the file:

```java
/*
 *  NeighborNet.java Copyright (C) 2024 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  ... (GPL v3 text) ...
 */
```

Most files say 2024; newer and revised files say 2025 or 2026. Use the current year on a new file; leave an
existing header alone unless you are substantially rewriting the class.

Every class carries a javadoc: one lowercase line saying what it is, optionally a `<p>` paragraph saying
**why** it works the way it does, and a final line with author and date (`Daniel Huson, 10.2021`). When you
substantially revise an old class, append the new date rather than replacing the old one. A class copied out of
`xtra` or contributed by someone else names that person in the javadoc.

Methods get a short lowercase javadoc. `@return` is used where the return value needs explaining; `@param`
only for parameters that are not obvious from the name.

### Comments explain why, not what

This is the most valuable habit in the existing code. Record the reason a constant has its value, the trap a
branch avoids, and the alternative that was tried and failed. Good existing examples:

- `Algorithm.compute` — the paragraph explaining why an empty input runs a view-producing algorithm anyway
  (`// ... so that the view refreshes to an empty display rather than keeping a stale drawing`);
- `Version.WEBSITE_URL` — `// non-final: a host app (e.g. RazorNet) can repoint the "Open User Manual" item`;
- the `desktop` profile in `pom.xml` — the whole comment block saying why fmmm-layout is runtime-scoped and
  why Gluon/iOS must not get it;
- `splitstree-env` — the "NOTE ON JavaFX" paragraph explaining why the tools' jar set differs from the app's.

Write comments of that kind. When you fix a subtle bug, leave behind the sentence that will stop the next
person reintroducing it. A `// todo:` comment is acceptable and is used in this codebase (there is one in
`Workflow.setupInputAndWorkingNodes` asking what the source node is for), but if you add one, also add it to
`../open/todo.md`.

### Style

- **Java 17** (`maven.compiler.release` is 17). Records, `var`, switch expressions, text blocks and pattern
  matching for `instanceof` are available and are used throughout — `Algorithm.compute` is a chain of
  `instanceof` patterns, `NeighborNet.compute` a switch expression.
- **Indentation is tabs** throughout `src/main/java/splitstree6`. Keep it that way.
- `var` is the norm in this codebase, much more so than in megan8. Match the file you are editing.
- Prefer a jloda utility over writing your own: `Basic`, `StringUtils`, `NumberUtils`, `FileUtils`,
  `CollectionUtils`, `IteratorUtils`, `Single`, `Pair`, `ProgramExecutorService`, `ProgressListener`,
  `RunAfterAWhile`, `AService`, `GraphLayouts`.
- **`RunAfterAWhile.apply(key, runnable)` is a process-wide static debouncer**: a later call with an equal key
  drops the earlier runnable. Keys must be per-instance objects (`this`, the pane, a dedicated `new Object()`),
  **never** a content-derived value that two instances can share. A content-derived key caused
  intermittently-blank tree views in 7.2026.
- **Cancellation and progress.** Every algorithm takes a `ProgressListener` as its first argument, and any loop
  worth showing must call `progress.setProgress(...)` / `progress.incrementProgress()` and let a
  `CanceledException` propagate. Use `ProgressSilent` in probes and in headless code that has nothing to report.
- **Threading.** Algorithm code runs off the FX thread, inside an `AService`. Anything touching a JavaFX scene
  graph or an `ObservableList` that a control is bound to must go through `Platform.runLater`. Conversely, do
  not block the FX thread waiting for a computation.
- The project is a **JPMS module** (`module-info.java`, module name `splitstreesix`). A new dependency needs a
  `requires`; a package that is reflected over needs an `opens`; a package a host app uses needs an `exports`.
  The `opens` lines on the algorithm packages are what make `Option` reflection work.

### Naming conventions

| Kind | Convention | Examples |
|---|---|---|
| Data block | `<Thing>Block` extending `splitstree6.workflow.DataBlock`, in `splitstree6.data` | `TaxaBlock`, `DistancesBlock`, `SplitsBlock`, `TreesBlock`, `CharactersBlock`, `NetworkBlock` |
| Algorithm base | `<From>2<To>`, abstract, one per ordered pair of block types | `Distances2Splits`, `Trees2Trees`, `Characters2Distances` |
| Algorithm package | `splitstree6.algorithms.<from>.<from>2<to>`, all lowercase | `algorithms/distances/distances2splits` |
| Concrete algorithm | named for the method, not the transformation | `NeighborNet`, `BioNJ`, `SplitDecomposition`, `PhyloFusion`, `ConsensusNetwork` |
| Filter | `<Thing>Filter` / `<Thing>TaxaFilter` | `SplitsFilter`, `TreesFilter`, `DistancesTaxaFilter` |
| Loader | `<Thing>Loader`, a `DataLoader<SourceBlock, X>` in `algorithms.source.source2<x>` | `CharactersLoader`, `TreesLoader` |
| View algorithm | `Show<Thing>`, producing a `ViewBlock` | `ShowSplits`, `ShowTrees`, `ShowNetwork` |
| Report algorithm | named for the statistic, extending `<From>2ReportBase` | `DeltaScore`, `PhiTest`, `ShapleyValues` |
| Option | `optionXxx` JavaFX property + `getOptionXxx` / `setOptionXxx` / `optionXxxProperty` | `optionInferenceAlgorithm`, `optionEdgeWeights` |
| Reader | `<Format>Reader` in `io.readers.<blocktype>` | `io.readers.trees.NewickReader` |
| Writer | `<Format>Writer` in `io.writers.<blocktype>` | `io.writers.splits.NexusWriter` |
| Nexus IO | `<Block>NexusInput` / `<Block>NexusOutput` in `io.nexus` | `SplitsNexusInput`, `TaxaNexusOutput` |
| Interface | `I`-prefixed | `IOptionsCarrier`, `IHasCitations`, `IView`, `IAppProfile`, `IToCircularSplits` |
| Marker interface | states a property of the output, used to gate downstream algorithms | `IToCircularSplits`, `IToCompatibleSplits`, `IToSingleTree`, `IExperimental` |
| Utility class | `<Thing>Utils` / `<Thing>Utilities` | `SplitsBlockUtilities`, `TreesUtils`, `NetworkUtils` |
| Command-line tool | `<Verb><Thing>` with a `main` and an `ArgsOptions` block, in `splitstree6.tools` | `RunWorkflow`, `ExportWorkflow` |
| Launcher script | `kebab-case` after the tool | `workflow-run` → `splitstree6.tools.RunWorkflow` |

Standard variable names, used consistently — please keep using them:

| Name | Meaning |
|---|---|
| `taxaBlock`, `taxa` | the `TaxaBlock` in force; **which one** matters, see `20_logic.md` §3 |
| `inputTaxaBlock` / `workingTaxaBlock` | the taxa as read from the file / after the taxa filter |
| `distancesBlock`, `splitsBlock`, `treesBlock`, `charactersBlock`, `networkBlock` | the block of that type |
| `progress` | the `ProgressListener` |
| `workflow` | the `Workflow` |
| `dataNode`, `algorithmNode` | workflow nodes; `node` when the type is obvious |
| `t`, `s` | taxon indices, **one-based** |
| `ntax`, `nchar`, `nsplits`, `ntrees` | dimensions, matching the nexus keywords |
| `cycle` | the circular ordering, `int[ntax+1]`, one-based, `cycle[0]` unused |
| `tree`, `graph` | a `PhyloTree` / `PhyloGraph` |
| `v`, `e` | a `Node` / an `Edge` |

**One-based indexing is pervasive and is not negotiable.** Taxa, characters, splits and trees are numbered
from 1, matching the nexus format; `DistancesBlock.get(i,j)` and `CharactersBlock.get(t,pos)` take one-based
arguments and hide a zero-based array. A new API that takes zero-based indices will be wrong in a way that is
hard to see. Say so explicitly in the javadoc when you add one.

### Documentation rules

- **Class javadoc says what and why; these files say the mathematics.** Do not put a page of algorithm theory
  into a javadoc — put it in a `2x_logic_*.md` and reference the file from the javadoc.
- **Every option is documented in three places** and all three must agree: the property's default value, the
  `getToolTip(optionName)` string (which is what the GUI shows and what `ExtractOptionsText` puts into the
  methods paragraph), and — for anything non-obvious — the logic file.
- `getCitation()` is not decoration. It is parsed by `splitstree6.cite.ExtractCitations` into the methods text,
  in the format `short citation;full citation;` repeated. Keep the format.
- An algorithm that is not ready for users implements `IExperimental`, which makes the GUI warn once.

### Structuring and file naming

- Package equals directory; the root package is `splitstree6`; the JPMS module is `splitstreesix`.
- `splitstree6.data` — the data blocks, plus their nexus format records; `data.parts` the pieces (`Taxon`,
  `CharactersType`, the state labelers).
- `splitstree6.workflow` — `Workflow`, `DataNode`, `AlgorithmNode`, `Algorithm`, `DataBlock`, `DataLoader`,
  `DataTaxaFilter`, `WorkflowSetup`, `WorkflowDataLoader`.
- `splitstree6.algorithms.<from>.<from>2<to>` — one package per ordered pair of block types, holding the
  abstract base and every concrete algorithm with that signature. `algorithms.utils` holds the shared
  computational helpers.
- `splitstree6.compute` — the large standalone engines that algorithms delegate to (`compute.phylofusion`,
  `compute.autumn`), kept out of the algorithm packages because of their size.
- `splitstree6.splits` — `ASplit`, `Compatibility`, `SplitNewick` and the split utilities; `splitstree6.models`
  the substitution models; `splitstree6.utils` general helpers.
- `splitstree6.io` — `io.readers` (one package per target block type, plus `ImportManager`), `io.writers` (the
  same, plus `ExportManager`), `io.nexus` (the canonical per-block nexus reader/writer pair), and
  `io.nexus.workflow` (the `.stree6` workflow file).
- `splitstree6.layout` — turning a block into coordinates (`layout.splits`, `layout.tree`, `layout.network`);
  `splitstree6.view` — the interactive views; `splitstree6.tabs`, `splitstree6.window`,
  `splitstree6.contextmenus`, `splitstree6.dialog`, `splitstree6.workflowtree` — the rest of the GUI.
- `splitstree6.tools` — one class per command-line tool; `tools.server` the webserver.
- `splitstree6.xtra` — experimental and student code. **Read-only** (see working rule 9).
- Launcher scripts live in `splitstree6-tools/tools`, one per tool, named in `kebab-case`, all sourcing
  `splitstree6-tools/lib/splitstree-env`. `tools/` at the repo root holds the installer's own templates of the
  same scripts, with `${installer:...}` placeholders.
- **These documents live in `splitstree6/ai/`, in three directories by epistemic status** — `docs/` (what we
  believe true), `open/` (what we do not yet know), `lab/` (what was tried). `../README.md` is the map. Files
  in `docs/` are named `NN_name.md`, two digits, lowercase, underscore; the tens digit is the section
  (10 context, 20 logic, 30 tools, 40 testing), and logic files for individual algorithms or subsystems take
  `21`, `22`, … . Keep the numbering stable; add new files with the next free number **and list them below**.
- **`ai/lab/`** holds one file per question investigated, named `YYYY-MM-DD_short-name.md`, from the skeleton
  in `../lab/TEMPLATE.md`. It is append-only: entries are never deleted or rewritten to match how things turned
  out, and abandoned entries stay. Every entry gets a row in `../lab/INDEX.md` when it closes. Durable
  conclusions are *copied* into a logic file; the entry remains as the record of how they were reached.

## The three layers

|                   | Directory  | Holds                                                         | Written how                                             |
|-------------------|------------|---------------------------------------------------------------|---------------------------------------------------------|
| **What we know**  | `ai/docs/` | The workflow design, the tools, the testing.                  | Rewritten in place; one current version.                |
| **What we don't** | `ai/open/` | Open questions with their gates; tasks whose answer is known. | Rewritten in place; items leave when settled.           |
| **What we tried** | `ai/lab/`  | One entry per question, the ledger, the numbers, the journal. | Append-only; nothing is deleted, including wrong turns. |

## The files

### `ai/docs/` — what we believe true

| File | Contents | Read it when |
|---|---|---|
| `20_logic.md` | **The workflow design**: data blocks, algorithms, the shape of a workflow, the reactive execution model, the type system, options, citations, IO and persistence, and — measured — exactly where the JavaFX dependency starts. Deliberately does *not* describe individual algorithms. | Always, before touching anything that runs inside the workflow. It is the map to the `2x_` files. |
| `30_tools.md` | Build, dependencies, the module system, the command-line tools and their launchers, the example data, profiling, IDE, version control. | Before building, running or writing a harness. |
| `40_testing.md` | How changes are verified in a project with no test suite: the probe pattern, the example datasets, workflow round-trips, and the known gaps. | Before claiming that anything works. |

There are **no `2x_logic_*.md` files yet**. Write them one subsystem at a time, as work touches them,
following the megan8 pattern: the mathematics, the parameter table, and a discrepancy section at the end.
The obvious first candidates are named in `../open/todo.md`.

### `ai/open/` — what we do not yet know

| File                   | Contents                                                                                                                                                                             | Read it when                                                        |
|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| `../open/questions.md` | The frontier. Open questions, each with the **gate** that would settle it, plus the standing hazards that will ruin a measurement if forgotten. Nothing here has an owner or a date. | At the start of every session, and before designing any experiment. |
| `../open/todo.md`      | Jobs whose answer is already known and only the work is outstanding.                                                                                                                 | At the start and end of every session.                              |

### `ai/lab/` — what was tried

| File                                 | Contents                                                                                                                                                                                                   | Read it when                                                                   |
|--------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| `../lab/INDEX.md`                    | **The ledger.** One row per question ever investigated, with a verdict from a closed vocabulary and the number that justifies it.                                                                          | **Before starting anything.** It is the answer to "have we tried this?"        |
| `../lab/TEMPLATE.md`                 | The skeleton for a new entry. Copy it to `YYYY-MM-DD_short-name.md`, or run `/probe`.                                                                                                                      | When an idea is worth more than ten minutes.                                   |
| `../lab/measurements.md`             | Every number worth comparing to a later number, with the instrument and the conditions that produced it.                                                                                                   | Before quoting a figure, and after taking one.                                 |
| `../lab/2026-08-17_splitstree-py.md` | **`splitstree.py`** — exposing the SplitsTree algorithms as a Python package. Steps 1–4 done in `husonlab/splitstree-py`; step 5, the generator, is the big one.                                           |
| `../lab/2026-08-17_test-suite.md`    | **Unit tests for all features** — tiered, mostly data-driven from the algorithm catalogue. Proposed, no code written against it.                                                                           |
| `../lab/2026-08-19_bootstrapping.md` | **Bootstrapping** — separating the computation from the workflow. All three done 2026-08-19.                                                                                                               |
| `../lab/journal.md`                  | The chronological archive of past work, findings and dead ends, newest first. Superseded as the primary record by `../lab/INDEX.md` and the entries, but everything before 2026-08-19 lives there in full. | When you need the history of a decision. Not required reading before starting. |
