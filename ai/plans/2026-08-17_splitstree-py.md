# Plan — `splitstree.py`: the SplitsTree algorithms as a Python package

**Status:** step 1 is done — see §3.1. Daniel answered all six open questions on 2026-08-17 (§9); the one
Java-side change the plan depended on (D-1) is done; both engineering risks the plan carried — headless
workflows and headless coordinates — are measured and closed; the PyPI name is free. **The next step needs a
repository**: §9.4 puts the code in `husonlab/splitstree-py`, which does not exist yet, so step 2 is blocked
on creating it.
**Raised by:** Daniel, 2026-08-17 — "I want to make all the algorithms that I have implemented in SplitsTree
available as a Python package."
**Related:** `ai/docs/20_logic.md` (especially §2, §6, §7), `ai/docs/30_tools.md`, `ai/docs/40_testing.md`,
`ai/docs/50_todo.md` P1.

---

## 1. Why, and what makes it feasible

Twenty-five years of algorithms — neighbor-net, split decomposition, the consensus and super-network methods,
PhyloFusion, the Autumn algorithm, the distance transformations, the rooted-network machinery — are reachable
today only through a JavaFX application or a `.stree6` workflow file. Everyone who does phylogenetics
programmatically works in Python. The gap is not the algorithms; it is the doorway.

The reason this is a tractable project rather than a rewrite is a property of the existing design, and it was
**measured** on 2026-08-17 rather than assumed (`ai/docs/20_logic.md` §6, `60_agent_notes.md`):

> An algorithm is `compute(ProgressListener, TaxaBlock, S in, T out)`. It runs on any thread, with **no JavaFX
> toolkit**, on a plain classpath, in milliseconds.

That was the original finding. Two further measurements have since widened it: since the D-1 fix a **whole
workflow** runs headless and synchronously, and the **layouts report coordinates** headless too (§9.6). What
actually needs a toolkit is narrower than it looked: building views, placing labels, and drawing shapes.

Everything below follows from that. The Java side needs **no further changes** for v1.

## 2. What we are building

A package that mirrors SplitsTree's own structure — data blocks and algorithms — while reading as ordinary
Python:

```python
import splitstree as st

taxa   = st.Taxa(["Human", "Chimp", "Gorilla", "Orang", "Gibbon"])
chars  = st.read_characters("primates.fasta")
dist   = st.hamming_distance(chars)                      # Characters2Distances
splits = st.neighbor_net(dist)                           # Distances2Splits
tree   = st.neighbor_joining(dist)                       # Distances2Trees

print(splits.fit, len(splits), splits.cycle)
st.write(splits, "out.nex", format="Nexus")
```

with the object form always available underneath, because that is what carries the options:

```python
nnet = st.NeighborNet(inference_algorithm="GradientProjection", threshold=1e-9)
splits = nnet(dist)                    # or nnet.apply(taxa, dist)
print(nnet.citation)                   # Bryant & Moulton 2004; Bryant & Huson 2023
```

Three properties the API must have, in priority order:

1. **Zero one-based indices in Python.** SplitsTree is one-based throughout, matching nexus. Python is
   zero-based. The conversion happens once, at the boundary, and never leaks. A user who has to remember that
   `dist[0]` is unused has been given a Java API with Python syntax.
2. **Numpy in, numpy out** for anything matrix-shaped. A distance matrix is an `(n, n)` `float64` array. An
   alignment is a list of strings or an `(n, m)` byte array. Anything else is a toy.
3. **Discoverable options.** Every option should appear as a keyword argument with a real name, a default, a
   type, legal values where it is an enum, and the tooltip as its documentation — visible to `help()`, to an
   IDE and to a type checker. This is achievable mechanically; see §6.

## 3. What we already know — the measured facts

Everything in this table was established by probe on 2026-08-17 and is the factual basis of the plan.

| Fact | Consequence for the design |
|---|---|
| `algorithm.compute(...)` runs on the `main` thread with no toolkit; NeighborNet gave `nsplits=7, fit=100.0`, NJ gave `(((a:1,b:1):3,c:1):1,d:1,e:1)` | the algorithm layer needs no JavaFX toolkit, no display, no `Platform.startup()` |
| `Option.getAllOptions(algorithm)` works headless and reports name, type, value and legal values | the generator (§6) can read the whole option surface out of the jar |
| readers and writers work headless (`NewickReader`, `io.writers.splits.NexusWriter`) | file IO needs no special handling |
| ~~`workflow.newAlgorithmNode(...)` throws `Toolkit not initialized`~~ — **fixed 2026-08-17 (D-1)**: a whole 11-node workflow now computes headless in ~40 ms, synchronously, same answers as the toolkit path | **the layer-2 gate is gone.** The workflow API no longer needs a toolkit in the user's Python process; see the revised §5 |
| after `Platform.startup()` the workflow also runs (`nsplits=7`, `isValid()` true, no window) | unchanged, and still the path for anything that builds views |
| **7 jars suffice**: `SplitsTree.jar`, `jloda-core`, `jloda-fx`, `jloda-phylogeny`, `javafx-base`, `javafx-graphics`, `ojalgo` — **13 MB**, against 76 MB for the full dependency directory | the wheel is small; NeighborNet, NJ, LogDet and PhyloFusion (incl. NNLS edge weights) all run on it |
| `javafx-base` contains **0** native libraries; `javafx-graphics` contains 7, none of which is loaded unless the toolkit starts | a **platform-agnostic wheel is plausible** — to be verified, §8 |
| the unclassified `javafx-*.jar` artifacts are **302-byte stubs**; the classes are only in the classified ones | you cannot dodge the platform classifier by using the plain artifact |
| `DistancesBlock` has bulk `set(double[][])` / `getDistances()`, `CharactersBlock` has `getMatrix()` | matrices cross the bridge in one call, not n² calls |
| the classpath ignores `module-info` entirely, so `Option` reflection needs no `opens` | **use the classpath, not the module path** — this removes the whole JPMS problem |
| scanning the jar for non-abstract `Algorithm` subclasses, instantiating each and asking it for its own from/to classes, options, citation and `IExperimental` status **works and takes a second**: 102 classes, 177 options, 75 transformations, 38 without a citation | the generator's discovery mechanism (§6) is settled — jar scan, no Java-side registry needed |
| **the layouts report coordinates headless.** The `javafx.scene`-importing layout classes are wrappers around pure routines that fill a `NodeArray<Point2D>`; probed for split-network outline and equal angle, all four rooted layouts on a reticulate network, and the general-network SPI | plotting from Python is buildable, §9.6 |

### 3.1 The step-1 spike, run 2026-08-17

JPype 1.7.1, Python 3.14, the seven-jar bundle, JDK 23. Every number the plan asked for:

| Measured | Result | What it settles |
|---|---|---|
| same answers from Python? | `nsplits=7 fit=100.0`, NJ `(((a:1,b:1):3,c:1):1,d:1,e:1)` — **identical to Java** | the bridge does not perturb the numbers |
| JVM start-up | **~105 ms**, once per process | cheap enough to do lazily on first call and never mention again |
| 1000×1000 `float64` (8 MB) numpy → Java | 17 ms to `JArray`, 26 ms into `DistancesBlock` | bulk transfer is fine |
| back to numpy | **0.7 ms**, round-trip bit-exact (`allclose` at `atol=0`) | `getDistances()` → numpy is essentially free |
| the same element-wise | 1.9 ms for 50×50, i.e. **~0.8 s** extrapolated to 1000×1000 | **~19× slower**; §7's "never a per-element call in a loop" is now measured, not asserted |
| Python `ProgressListener` via `@JImplements` | **works** — 4 callbacks during a NeighborNet run | progress and cancellation from Python are available |
| cost of a callback | **0.06 µs/call** (100k calls in 6 ms) | free; no need to batch or throttle |

Three things the spike found that the plan had not anticipated:

- **JPype loads the JVM into the Python process, so the JVM's architecture must match the interpreter's.** On
  the development Mac, `python3` is arm64 and the `java` on `PATH` is an x86_64 GraalVM, so the default JVM
  **cannot be used at all** — `startJVM` fails before any SplitsTree code runs. See §8.
- **The first error a new user meets is `FileNotFoundError: JVM DLL not found`**, naming a path that does not
  exist (JPype's compiled-in guess: a Homebrew `openjdk@17`). It mentions neither `JAVA_HOME` nor what to
  install. This is the single most important error message in the package (§8).
- **`java.util.BitSet` is not iterable from Python**, and split indices are one-based, so
  `ASplit.getA()` is unusable as it stands. Converting a split to a `frozenset` of taxon *labels* is exactly
  the kind of thing §7 says the block layer must own, and it is now the worked example.

Also noted: algorithms print progress lines to stderr (`NNet algorithm: ActiveSet taxa: 5 ...`). Harmless in a
tool, noise in a library — the binding needs a way to silence or capture it.
| **the layouts report coordinates headless.** The `javafx.scene`-importing layout classes are wrappers around pure routines that fill a `NodeArray<Point2D>`; probed for split-network outline and equal angle, all four rooted layouts on a reticulate network, and the general-network SPI | plotting from Python is buildable, §9.6 |

## 4. The bridge

| Option | Verdict |
|---|---|
| **JPype** — in-process JNI, `jpype.startJVM(classpath=…)`, Java classes appear as Python classes | **Recommended.** Mature, actively maintained, releases the GIL during Java calls, converts `double[][]` ↔ numpy in bulk, supports Python callbacks into Java interfaces (`@JImplements`) — which is exactly what a `ProgressListener` needs. In-process means no serialisation and no second process to supervise. |
| **Py4J** — separate JVM, socket gateway | Rejected. Every array element crosses a socket; a 1000-taxon matrix is 10⁶ round trips. Process isolation is not worth that, and it adds a gateway lifecycle to get wrong. |
| **jpy** | Rejected: same model as JPype, far smaller community. |
| **GraalPy / GraalVM polyglot** | Rejected for v1. Inverts the deployment — the user's Python must run on GraalVM — so it is not `pip install`-able into an ordinary environment. |
| **subprocess around `RunWorkflow` and the tools** | Rejected as the architecture, **kept as a fallback** for the workflow layer. It needs no binding code at all and works today, but it forces every intermediate result through a file, gives no live objects, and would need a CLI per algorithm. |
| **port the algorithms to Python/C** | Rejected. Enormous, duplicative, and guaranteed to drift from the Java that Daniel actually maintains. |

**Recommendation: JPype, on the classpath, with the JVM started lazily on first use.**

The one real cost of JPype is that a process gets **one JVM, once** — it cannot be restarted or reconfigured
after `startJVM`. So heap size and extra classpath entries must be settable *before* first use:

```python
st.configure(heap="8G", threads=8, extra_classpath=[...])   # optional; must precede the first call
```

and `st.configure` after the JVM is up should raise, not silently do nothing.

## 5. Architecture — two layers, and only the first ships in v1

```
   ┌─────────────────────────────────────────────────────────────┐
   │  splitstree/                    (Python)                     │
   │    __init__.py      generated function + class surface       │
   │    _jvm.py          JVM bootstrap, jar discovery, config     │
   │    blocks.py        Taxa / Distances / Characters / Splits / │
   │                     Trees / Network  — wrappers + converters │
   │    algorithms/      GENERATED, one module per from2to        │
   │    io.py            read_* / write_*, format registry        │
   │    progress.py      ProgressListener bridge, cancellation    │
   │    errors.py        Java exception → Python exception        │
   │    jars/            the 7 jars (13 MB)                       │
   │                                                              │
   │  ── layer 2, NOT in v1 ──────────────────────────────────    │
   │    workflow.py      Workflow, .stree6 load/save/run          │
   └─────────────────────────────────────────────────────────────┘
```

**Layer 1 — algorithms.** Direct `compute()` calls. No toolkit, no `Workflow`, no `AService`. This is the whole
of v1 and it covers the actual request: the algorithms, available from Python.

**Layer 2 — workflows.** Load a `.stree6`, apply it to new data, save it back; build a pipeline and let
validity propagation drive it. This is how a user reproduces a published analysis, and it is the feature that
would make the package genuinely better than a pile of function calls.

**It was gated on D-1, and D-1 was fixed on 2026-08-17.** A workflow now computes headless *and
synchronously*: `workflow.getInputTaxaFilterNode().restart()` returns when the whole cascade is done. For a
Python binding that is close to ideal — no toolkit in the user's process, no callback into the JVM's FX
thread, no waiting on a `validProperty` listener, and a natural blocking call:

```python
wf = st.Workflow.load("analysis.stree6")
wf.load_data("other-dataset.fasta")
wf.run()                                  # blocks; whole workflow computed on return
splits = wf["Splits"]
```

Two caveats remain, and they are what keeps layer 2 out of v1 rather than in it:

- **A workflow containing view nodes still needs a toolkit**, because a `ViewBlock` holds live JavaFX objects.
  A `.stree6` saved from the GUI normally *does* contain one. So layer 2 must either strip view nodes on load
  (fine for computing, loses the saved drawing settings) or start a toolkit for them. Decide when building it;
  measure first how a workflow behaves when its `Show*` node is simply removed.
- **The synchronous cascade means no progress reporting and no cancellation from Python** unless a
  `ProgressListener` proxy is passed in through `Workflow.setHeadlessProgressListener`. That is the mechanism;
  it just needs wiring.

Still: do not build layer 2 speculatively, and do not start it before layer 1 is finished and used. A
half-working workflow API is worse than none.

## 6. The generator — the core of the proposal

Hand-writing 102 wrappers carrying 177 options between them, and maintaining them against a moving Java API,
is the way this project dies. **Generate them.** The infrastructure already exists: `Option.getAllOptions`
reports name,
type, current (= default) value and legal enum values; `listOptions()` reports which are user-visible and in
what order; `getToolTip(name)` is the human description; `getCitation()`, `getShortDescription()`,
`getFromClass()`, `getToClass()` complete the picture.

Two pieces:

1. **A Java exporter** — one small class, `splitstree6.tools.ExportAlgorithmCatalog` (or, better for v1, a
   probe that lives in the Python repo and is compiled against the jars, so `src/main` is untouched). It walks
   the algorithm classes and emits **JSON**: for each algorithm, its class name, from-class, to-class, short
   description, citation, whether it is `IExperimental`, its marker interfaces, and one record per option with
   name, `OptionValueType`, default, legal values and tooltip.

   *How it finds the algorithms* was the one open engineering question, and it is now settled: **scan the jar's
   entries** for classes assignable to `Algorithm`, skip the abstract ones, instantiate the rest with their
   no-argument constructor and interrogate them. There is no registry to read and none is needed. This was run
   on 2026-08-17 and produced the catalogue in `ai/docs/20_logic.md` §4 in about a second, with no Java-side
   change of any kind.

2. **A Python generator** that turns that JSON into `.py` modules plus `.pyi` stubs: a class per algorithm with
   typed keyword-only `__init__` parameters, `Literal[...]` for enums, the tooltip as the parameter doc, the
   short description and citation in the class docstring, plus a snake_case module-level convenience function.

Why this is the right call:

- **It cannot drift.** Regenerate as part of the release build and a renamed option is a diff, not a bug
  report.
- **It gives real Python ergonomics** — autocomplete, `help()`, type checking — from data the Java side already
  publishes.
- **It documents itself from `getToolTip`**, which turns those strings into user-facing documentation and gives
  a reason to improve the ones that are currently just the option name.
- **It surfaces gaps.** An algorithm whose citation is missing or malformed shows up as a hole in the generated
  docs. That is the same information the methods-text feature depends on (`20_logic.md` §8).

Both decisions the generator forces are now taken (§9.1, §9.2): **snake_case** for functions and options, and
**report algorithms are exposed** while loaders, taxa filters and `Show*` are not. The generator therefore owns
one canonical Java→Python name map, and must fail the build if it is ever non-injective — two options that
collide after camel→snake conversion would otherwise silently overwrite each other.

## 7. Data conversion

The conversion layer is where the quality of the package is decided, and it is the part that must be written by
hand.

| Block | Python representation | Notes |
|---|---|---|
| `TaxaBlock` | `st.Taxa` wrapping `list[str]` | usually implicit: derived from whatever produced the data |
| `DistancesBlock` | `(n, n)` `float64` numpy array | bulk `set(double[][])` / `getDistances()`; **symmetric, zero diagonal, zero-based on the Python side** |
| `CharactersBlock` | `list[str]`, or `(n, m)` `uint8`; `CharactersType` as a string | `getMatrix()` returns `char[][]`; also accept a Biopython `MultipleSeqAlignment` if §9 says so |
| `SplitsBlock` | a `Splits` object: sequence of `(frozenset[str], weight, confidence)`, plus `.cycle`, `.fit`, `.compatibility` | the taxon *labels*, not indices — this is the single biggest usability win over the Java API |
| `TreesBlock` | list of Newick strings, plus a `Trees` wrapper; `.rooted`, `.partial`, `.reticulated` | round-trip through `NewickReader`/`toBracketString`; optional ToyTree/DendroPy/ete3 export per §9 |
| `NetworkBlock` | extended Newick, and/or a `networkx.DiGraph` with node/edge attributes | GML export already exists (`io.writers.network.GMLWriter`) and carries node coordinates |
| `ReportBlock` | `str` / `list[str]` | the analysis algorithms (`DeltaScore`, `PhiTest`, `ShapleyValues`, …) |

Rules that are not negotiable:

- **Convert at the boundary, in bulk.** Never a per-element JNI call in a loop. A 1000-taxon matrix is one
  `double[][]` transfer; done element-wise it is 10⁶ crossings and the package gets a reputation for being
  slow.
- **Validate on the way in.** Non-square, asymmetric or NaN-bearing distance matrices, duplicate taxon labels,
  ragged alignments: raise a Python `ValueError` with a useful message, before the JVM sees it. A Java stack
  trace surfacing in a Python traceback is a bug in this layer.
- **Taxa travel with the data.** Almost every Java call needs `(taxaBlock, dataBlock)`. In Python the taxa
  should be an attribute of the data object, so the user writes `st.neighbor_net(dist)` and the wrapper
  supplies both. An explicit `taxa=` override stays available for the cases where they genuinely differ.
- **Round-trip tests for every block type**, both directions, including the empty and one-taxon cases.

Also to bridge:

- **Progress and cancellation.** `ProgressSilent` by default; a `progress=True` flag for `ProgressPercentage`;
  and, if it proves cheap, a JPype `@JImplements(ProgressListener)` that calls a Python callback — which is
  what makes `KeyboardInterrupt` and tqdm work. Cancellation must map `CanceledException` to something a
  Python user expects.
- **Threads.** `ProgramExecutorService.setNumberOfCoresToUse(n)` from `st.configure(threads=…)`.
- **Errors.** Java `IOException` (which is what "not applicable to given input data" arrives as) →
  `SplitsTreeError`; keep the Java message, drop the stack trace unless a debug flag is set.
- **Citations.** Expose `.citation` on every algorithm and, later, an equivalent of `ExtractMethodsText` over a
  sequence of calls. "Which papers do I cite for this analysis?" is a question this package can answer better
  than anything else in the field, and it is nearly free.

## 8. Packaging and distribution

- **PyPI name**: `splitstree`, import name `splitstree` (§9.4). **Confirmed free** — Daniel checked on
  2026-08-17, there is no official package of that name on PyPI. Register it early: the name is short and
  obvious, and it costs nothing to hold. (Re-check at registration; a name can be claimed at any time.)
- **Jars in the wheel**, under `splitstree/jars/`, discovered relative to `__file__`. 13 MB for the 7-jar set,
  which is unremarkable for a scientific wheel.
- **Platform wheels, probably not needed — and there is now a workflow that settles it.**  `javafx-base` has
  no natives; `javafx-graphics`'s 7 native libraries are never loaded because the toolkit never starts. So one
  `py3-none-any` wheel may serve every platform. `.github/workflows/portability-probe.yml` tests exactly that:
  it builds once with `-Djavafx.platform=mac` and runs the resulting jars unchanged on ubuntu, windows and
  macos. If it comes back ALL PASS, ship one wheel, and **strip the natives out of the bundled
  `javafx-graphics` jar** so the claim is enforced rather than hoped for — a future code path that does touch
  them then fails loudly instead of working on one platform only. If it does not, fall back to five platform
  wheels (`mac-aarch64`, `mac`, `linux`, `linux-aarch64`, `win`), which are still all built on one machine
  (§ above: there is nothing to compile).
- **The JVM itself is not bundled.** Require a JDK/JRE ≥ 17 and let JPype find it via `JAVA_HOME`; document
  `conda install -c conda-forge openjdk` and `install-jdk` as the easy routes. Bundling a JVM per platform
  triples the wheel size and the maintenance.

- **The JVM's architecture must match the interpreter's**, because JPype loads it into the Python process.
  This is not a theoretical caveat: on the development Mac, `python3` is arm64 while the `java` on `PATH` is
  an x86_64 GraalVM 17, so the machine's default JVM cannot be used from Python at all. `_jvm.py` must
  therefore *check* the architecture rather than take the first JVM it finds, and say so when they differ.

- **The no-JVM error message is the most important string in the package.** Measured: JPype's own is
  `FileNotFoundError: JVM DLL not found: /usr/local/Cellar/openjdk@17/.../libjli.dylib` — a compiled-in guess
  at a Homebrew path, naming neither `JAVA_HOME` nor what to install nor the architecture requirement. Replace
  it with one that says what was looked for, where, what was found and rejected (and why), and the one command
  that fixes it. This is the first thing most users will see go wrong, and it is cheap to get right now and
  painful to retrofit.
- **Licence.** SplitsTree is **GPL v3**, and a wheel that bundles the jars is a GPL v3 distribution. That is a
  real constraint on who can use the package in a closed pipeline, and it should be stated on the PyPI page
  rather than discovered later. It is Daniel's decision whether that is the intended outcome.
- **Version.** Track the SplitsTree version the jars came from (`Version.VERSION`, read from the jar manifest —
  note it reads `dev` when run from `target/classes`, so the wheel must be built from a real jar). Expose
  `st.__version__` and `st.splitstree_version`.
- **Where the code lives: `husonlab/splitstree-py`, a separate repository** (§9.4). The cost of separation is
  drift, since the generator reads a built jar rather than the sources next to it. Manage it explicitly: the
  Python repo pins the SplitsTree version it generated against, exposes it as `st.splitstree_version`, and
  regeneration lands as its own reviewable commit whose diff shows exactly what changed in the Java API. A
  scheduled workflow that regenerates against the latest SplitsTree jar and fails on an unexpected diff is the
  cheap way to notice a rename the day it happens.

## 9. Decisions — taken by Daniel, 2026-08-17

All six questions are answered. They are recorded here as the settled shape of the package; the sections above
have been updated to match.

1. **Naming: snake_case throughout the Python surface.** `st.neighbor_net(dist)`, `st.hamming_distance(...)`,
   and options as `inference_algorithm=`, not `optionInferenceAlgorithm=`. The generator therefore owns one
   canonical Java→Python name mapping (strip the `option` prefix, camel→snake) and must keep it **injective**:
   two Java names that collide after conversion are a build-time error, not a silent overwrite. Classes keep
   their Java names (`st.NeighborNet`) so that one vocabulary still connects the package to the manual and the
   GUI.

2. **Report algorithms are in.** All 12 are exposed. They currently produce text, and v1 returns that text.
   *Future work, explicitly flagged by Daniel:* have them also return values. That is a Java-side change to
   `ReportBlock` / the `*2ReportBase` classes, not something the binding should paper over by parsing strings —
   a parser would silently break every time a message is reworded. v1 returns the text; the structured version
   comes when Java grows it.

3. **Interop: numpy required, everything else an optional extra.** `pip install splitstree` pulls numpy only;
   `splitstree[bio]` adds the Biopython / DendroPy / ToyTree / ete3 / networkx conversions. Each conversion
   lives behind a lazy import so a missing extra is a clear error at the call, not at import.

4. **A separate repository, `husonlab/splitstree-py`; PyPI name `splitstree`, confirmed free on 2026-08-17.**
   This reverses the tentative recommendation in §8 for an in-repo `python/` directory. The consequence to
   manage is drift: the generator reads a *built jar*, so the Python repo must pin which SplitsTree version it
   was generated against, expose it as `st.splitstree_version`, and regenerate as a visible, reviewable commit.

5. **Make `AService` create its progress pane lazily. DONE, 2026-08-17** — and it turned out to be only half
   the fix. See `ai/docs/20_logic.md` §6 and D-1: the lazy pane fixed *construction*, but `Service.start()`
   dispatches through `Platform.runLater` and still needed the toolkit, so `AlgorithmNode.restart()` gained an
   inline execution path for when none is running. Net effect for this plan: **layer 2 is no longer gated**. A
   whole workflow computes headless and synchronously (11 nodes in ~40 ms, same answers as the toolkit path).

6. **Coordinates are in scope: aim to report them. MEASURED 2026-08-17 — they come out headless.** This was
   the last open engineering risk in the plan and it is closed. The layout classes that import
   `javafx.scene.*` turn out to be *wrappers*: each calls a pure routine that fills a `NodeArray<Point2D>` or
   `Map<Node, Point2D>` and only then builds shapes. The pure routines are public and toolkit-free. Full
   evidence in `ai/docs/20_logic.md` §6.2a; in summary:

   | Geometry | Call | Verified on |
   |---|---|---|
   | split network, outline (with the loop polygons) | `PhylogeneticOutline.apply(...)` | 6-taxon neighbor-net: 16 nodes, 11 splits, 1 loop of size 10 |
   | split network, equal angle | `EqualAngle.apply` → `assignAnglesToEdges` → `assignCoordinatesToNodes` | same network; taxon points **identical** to the outline's |
   | rooted tree / reticulate network, 4 layouts | `layout.tree.LayoutRootedPhylogeny.apply(...)` | a PhyloFusion network: 12/12 nodes placed, both reticulations |
   | general network | `GraphLayouts.getService().apply(...)` | 6/6 points |

   So a `splits.coordinates()` / `network.coordinates()` API is buildable, and `matplotlib` plotting is a
   realistic v1.5 feature rather than a hope. Three limits to design around, all of which live in the view
   layer and none of which blocks the API:

   - **edges are straight source→target segments.** The curved/circular edge shapes are control points
     computed by `layout.tree.CreateEdges`, which is view code. Either port that later or draw polylines.
   - **no label placement.** `RadialLabelLayout` and friends measure `RichTextLabel`s and need the toolkit;
     Python places its own labels, which is what a matplotlib user wants anyway.
   - **coordinates are model units, not pixels** (unit edge length ≈ 1). The view multiplies by `unitLength`
     and fits to the pane. Python should do its own scaling, so this is a feature.

   Sanity checks that the numbers are real: under `Scaling.ToScale` each edge's horizontal extent equals its
   weight to the digit, and `Scaling.EarlyBranching` gives integer levels on the same network.

## 10. Suggested order of work

Each step ends in something runnable; nothing after step 1 starts until step 1's numbers are in.

1. ~~**Spike, time-boxed to a day.**~~ **DONE 2026-08-17** — every number is in §3.1, and all four questions
   came back favourably: the answers match Java exactly, the JVM costs ~105 ms once, bulk matrix transfer is
   ~19× faster than element-wise and bit-exact both ways, and a Python `ProgressListener` works at 0.06 µs per
   callback. The spike also turned up the JVM-architecture constraint and the no-JVM error message, both now
   in §8, and one conversion the block layer must own (`BitSet` → `frozenset` of labels).
2. ~~**The JVM bootstrap and the jar set.**~~ **DONE 2026-08-17**, in `husonlab/splitstree-py` commit
   `0f7d067`: `_jvm.py` (search order, version *and architecture* checking, lazy start), `errors.py`,
   `configure()` raising after start, `tools/sync_jars.py`, `pyproject.toml`, 19 tests. Measured on macOS:
   `st.start()` 217 ms end to end, neighbor-net from Python `nsplits=7 fit=100.0`, tests 0.3 s, and the wheel
   builds **`py3-none-any`, 11.7 MB, with all seven jars inside** — which is the packaging half of the
   one-wheel question answered. *Still to verify: that it runs on Linux and Windows*, which
   `.github/workflows/portability-probe.yml` covers for the jars and a `pip install` will have to cover for
   the Python.

   **Know what a clean runner does and does not prove.** GitHub's runners install a known-good JDK via
   `setup-java`, so they demonstrate that the code is portable and prove nothing whatever about the thing most
   likely to go wrong in the field: a machine where the JVM is missing, is the wrong version, lives behind a
   `JAVA_HOME` containing spaces (`C:\Program Files\...`), or where Python is conda rather than system and
   JPype therefore looks for `jvm.dll` / `libjvm.so` somewhere else. Runners are clean; users are not. That
   gap is what a real machine belonging to somebody else is for — see step 6.
3. **The block layer.** `Taxa`, `Distances`, `Characters`, `Splits`, `Trees`, `Network` wrappers with bulk
   converters, input validation and zero-based indexing. *Verification: round-trip tests for every block type
   in both directions, including empty, one-taxon and ragged/invalid inputs; large-matrix timing within the
   step-1 budget.*
4. **The IO layer.** `read_*` / `write_*` over `ImportManager` / `ExportManager`, with the format lists read
   from Java rather than hard-coded. *Verification: read every file in `examples/` that matches a supported
   format and check the taxon and dimension counts; write each block type in each of its formats and read it
   back.*
5. **The generator.** JSON exporter, Python code generator, generated modules and `.pyi` stubs, checked in so
   the diff is reviewable. *Verification: every algorithm in the catalogue instantiates; every option round
   trips Python → Java → Python; a spot-check of ten algorithms against hand-written calls gives identical
   results.*
6. **Docs, examples and packaging.** README, a Jupyter notebook reproducing one figure from
   `examples/publications/`, the wheel build, and a GitHub Actions workflow that installs and tests the wheel
   on ubuntu/macos/windows. Note that **nothing here needs a build matrix**: a wheel bundling jars has nothing
   to compile, so `python -m build` on one machine produces the artefact for every platform. The matrix is for
   *running* the tests, where the platform differences are real and silent — the classpath separator is `;` on
   Windows and `:` elsewhere, JVM discovery differs, and so do default encodings. *Verification: `pip install`
   into a clean environment on all three platforms and run the notebook.*

   **This is the point at which somebody else's computer earns its keep.** Give a colleague with a real
   Windows or Linux machine a five-minute script: `pip install`, run three lines, paste the output. Do not ask
   them to debug, and do not ask before there is something installable — a person's goodwill is a scarcer
   resource than a runner minute. What they test that no runner can is an *unprepared* machine, and the top
   support issue for a JVM-backed Python package is always the JVM: absent, too old, or not where JPype
   looked. The error message for that case is worth as much design attention as any API in this plan.

That is v1. Two follow-ons, both now decided in principle (§9.6, §9.2) but neither scoped:

7. **Coordinates** (§9.6). The probe is done and the answer is yes, so this is now ordinary work: wrap the
   four pure entry points listed in §9.6 as `coordinates()` on the splits, trees and network objects, returning
   node points plus, for the outline, the loop polygons. *Verification: a matplotlib plot of the six-taxon
   split network whose outline the probe already computed — 16 nodes, 1 loop of size 10 — visually matching the
   shape SplitsTree draws.* Do **not** reimplement any geometry in Python; if something is missing (curved
   edges), propose separating it out of the view layer as its own Java-side change.
8. **Layer 2, the workflow API** (§5). No longer gated on D-1. The remaining question is what to do with view
   nodes in a loaded `.stree6`; measure first.

And one Java-side item this plan surfaced but does not own: **report algorithms returning values as well as
text** (§9.2). That belongs in SplitsTree, not in the binding.

## 11. Risks and non-goals

- **The generator is the project.** If it is skipped "just to get something working", the package will be ten
  hand-written wrappers that rot. Build it in step 5, before the API surface is wide.
- **JPype's single-JVM-per-process rule will surprise users.** Anything configurable must be configured before
  first use, and `st.configure` after the fact must raise. Notebook users will hit this; the error message is
  the fix.
- **Do not reimplement anything in Python.** The moment a distance transformation or a split-weight
  optimisation is "quickly done in numpy", there are two implementations and one of them is wrong. The Python
  side converts, validates and presents; it does not compute.
- **Do not expose one-based indices**, not even "for consistency with the manual". Convert once, at the
  boundary.
- **Do not chase feature parity with the GUI.** Views, tabs, interactive selection and the workflow editor are
  not in scope and probably never will be.
- **This is not a substitute for the test suite** (`50_todo.md` P3) — but it is the best available *excuse* for
  one. The step-3 to step-5 verifications are, in effect, the first end-to-end tests the algorithms have ever
  had, written in a language with a working test runner. If they find Java bugs, those get fixed in Java, and
  the finding goes in `60_agent_notes.md`.
- **The Java side may still need small changes**, and each one must be justified separately rather than smuggled
  in: a catalogue exporter in `splitstree6.tools`, possibly a public accessor or two, possibly D-1 in jloda3.
  None is required for step 1.
