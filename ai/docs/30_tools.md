# 30 — Tools

What the project is built with, how to run it, and what to reach for when working on it.

## Build

**Maven.** `jloda3` is a dependency and is resolved from the local repository, so the order matters:

```bash
cd ~/IdeaProjects/apps/jloda3 && mvn -o install -DskipTests
```

```bash
cd ~/IdeaProjects/apps/splitstree6 && mvn -o compile
```

`-o` (offline) is normally fine and much faster; drop it after changing dependencies. **A change in `jloda3` is
not visible to `splitstree6` until `jloda3` has been re-installed** — which matters here because the workflow
engine itself (`jloda.fx.workflow`), the graph and tree classes (`jloda-phylogeny`) and every utility live
there.

Timings on this machine (2026-08-17): `mvn -o clean compile -q` 32 s, an incremental `mvn -o package -q` 8 s.

To produce the jar plus the dependency directory that the command-line launchers and the probe harnesses need:

```bash
mvn -o package
```

`copy-dependencies` is bound to the `package` phase, so this fills `target/dependency` (80 jars) as a side
effect — no separate `dependency:copy-dependencies` invocation is needed. Note that `mvn clean` removes it, so
after a `clean compile` you must `package` again before running anything that uses the module path.

Running the GUI from Maven:

```bash
mvn -o javafx:run
```

Coordinates:

| | |
|---|---|
| Group / artifact / version | `org.husonlab:SplitsTree:1.0.0-SNAPSHOT` |
| Language level | Java 17 (`maven.compiler.release`) |
| JPMS module name | `splitstreesix` (note: **not** `splitstree6`) |
| Main class | `splitstree6.main.SplitsTree6` |
| JavaFX | 22.0.2 |
| Plugins | `maven-compiler-plugin`, `javafx-maven-plugin`, `maven-jar-plugin`, `maven-dependency-plugin` |
| Released version | `Version.VERSION` is read from the **jar manifest** (`Implementation-Version`), so it is `dev` in any run from `target/classes`. The current release line is 6.9.x; see `release-notes/`. |

### The JavaFX platform trap

Unlike megan8, this pom does **not** pin `javafx.platform`. The JavaFX artifacts resolve their own native
classifier from the JVM that *Maven itself* is running on. On this machine the default `mvn` runs on an
**x86_64 GraalVM 17**, so `target/dependency` gets `javafx-*-22.0.2-mac.jar` (Intel). Build with an arm64 JDK
and you get `mac-aarch64` jars instead — and then the x86_64 JVM cannot run them, and vice versa. If JavaFX
fails to start with a native-library error, this mismatch is the first thing to check:

```bash
ls target/dependency | grep javafx-graphics
```

Both the classified and the unclassified jars are copied. That does **not** break the module path here — the
tools start fine with `--module-path target/SplitsTree-1.0.0-SNAPSHOT.jar:target/dependency` (verified
2026-08-17) — but it is the same duplication that needed explicit exclusions in megan8, so do not be surprised
if adding a JavaFX dependency breaks module resolution.

### The `desktop` profile

The `desktop` profile adds `org.husonlab:fmmm-layout` at **runtime** scope: the native OGDF FM3 graph layout,
discovered through the `jloda.graph.layout.GraphLayoutService` SPI in `jloda-core`. It is desktop-only, because
Gluon/iOS builds cannot load native code and must fall back to jloda's own Java layout.

Two traps, both of which have cost hours before:

- **fmmm-layout needs JDK 22+** (it uses the FFM/Panama API). Built or run on JDK 17 the provider silently
  fails to load and the Java fallback is used. Use `export JAVA_HOME=$(/usr/libexec/java_home -v 23)` and pass
  `-Pdesktop` **on the command line** — the IntelliJ Maven-panel profile toggle does not carry to a terminal
  `mvn`.
- SplitsTree prints `Graph layout: ogdf-fmmm` or `Graph layout: jloda-fmm` at startup. That line is how you
  find out which one you actually got.

Note the spelling: OGDF's algorithm is **FMMM** (three M's, "FM3"); jloda's own default is a *different*
algorithm, `jloda.graph.fmm.FastMultiLayerMethod` (**FMM**, two M's). Do not blanket-rename one to the other.

## Dependencies

- **jloda3** — `jloda-core`, `jloda-fx`, `jloda-phylogeny`, `jloda-connect`, all `1.0.0-SNAPSHOT` from the
  local repository. The utilities (`Basic`, `StringUtils`, `NumberUtils`, `FileUtils`, `CollectionUtils`,
  `IteratorUtils`, `Single`, `Pair`, the progress listeners, `ProgramExecutorService`, `AService`,
  `RunAfterAWhile`), the graph and tree classes (`PhyloTree`, `PhyloGraph`, `PhyloSplitsGraph`, `Node`, `Edge`,
  `NodeArray`, `EdgeArray`) and the generic workflow engine all come from here. **Prefer a jloda utility over
  writing your own.**
- **JavaFX 22.0.2** — base, graphics, controls, fxml, web, media. Unlike MEGAN, this is a JavaFX-native
  application: the whole GUI, and — importantly — the workflow engine's `AService` (`20_logic.md` §6).
- **richtextfx / flowless / undofx / reactfx** — the text editor used by the input editor and the text views.
- **jackson-databind** 2.22.1.
- **commons-collections4** 4.5.0, **commons-math4-legacy**, **commons-numbers-gamma** — statistics for the
  models and the analyses.
- **ojalgo** 57.0.0 — the LP/NNLS solvers, used by the PhyloFusion branch-length fitting
  (`compute/phylofusion/TracedEdgeWeights.java`).
- **sqlite-jdbc** — the genome-context database.
- **fmmm-layout** — runtime only, `desktop` profile (above).
- **jama** — a small local source copy under `src/main/java/jama`, not a Maven dependency.

The project is a **JPMS module**. A new dependency needs a `requires` in `module-info.java`; a package that is
reflected over (any algorithm package, because of `Option`) needs an `opens`; a package a host application or a
binding uses needs an `exports`.

## Running

### The desktop application

```bash
mvn -o javafx:run
```

or, from the built artefacts, exactly as the installed launcher does:

```bash
java --module-path target/SplitsTree-1.0.0-SNAPSHOT.jar:target/dependency --add-modules splitstreesix splitstree6.main.SplitsTree6
```

### The command-line tools

Sources in `splitstree6.tools`, one class per tool with an `ArgsOptions` block. There are two copies of the
launcher scripts, and the difference matters:

- **`splitstree6-tools/tools/`** — the working scripts. They source `splitstree6-tools/lib/splitstree-env`,
  which handles both an installed tools package (`lib/jars/*.jar`) and a Maven checkout (`target/*.jar` plus
  `target/dependency/`), locates a Java ≥ 17, refuses an x86_64 JVM on Apple silicon with an explicit fix, and
  exports `ST_JAVA`, `ST_MODULE_PATH`, `ST_MODULE`, `ST_UPDATER_MODULE`.
- **`tools/`** at the repo root — the installer's templates of the same scripts, carrying
  `${installer:sys.preferredJre}` placeholders and reading `SplitsTree.vmoptions`. **These are not runnable
  from a checkout**; do not debug them as if they were.

```bash
splitstree6-tools/tools/workflow-run -h
```

**The JDK on this machine's `PATH` is an x86_64 GraalVM 17 on an arm64 Mac**, which `splitstree-env` refuses
outright. Because the JavaFX jars currently in `target/dependency` are the Intel `-mac` ones, that JVM is in
fact the one that works — so for verification runs, bypass the launcher and invoke the module directly:

```bash
java --module-path target/SplitsTree-1.0.0-SNAPSHOT.jar:target/dependency --add-modules splitstreesix splitstree6.tools.RunWorkflow -h
```

The tools:

| Tool | Class | Use |
|---|---|---|
| `workflow-run` | `RunWorkflow` | apply a saved `.stree6` workflow to one or many input files; the main headless path |
| `workflow-export` | `ExportWorkflow` | extract a workflow from a document, or a block from a workflow |
| `genome-context` | `GenomeContext` | genome database queries |
| `mash-sketches` | `ComputeMashSketches` | precompute Mash sketches for the genome path |
| `sample-trees` | `SampleTrees` | subsample a trees file |
| `bfilter-tool` | `BloomFilterTool` | Bloom-filter utilities |
| `webserver` | `tools.server.*` | the HTTP server |
| `splitstree6` | `main.SplitsTree6` | the desktop application |

Not scripted, but present and useful as worked examples of headless algorithm use: `RunPhyloFusion`
(untracked, in `splitstree6.tools`), `Extractor`, `CheckContainment`, `ArgToExtendedNewick`, `Mash`.

### `RunWorkflow` in practice

```bash
java --module-path target/SplitsTree-1.0.0-SNAPSHOT.jar:target/dependency --add-modules splitstreesix splitstree6.tools.RunWorkflow -w examples/large_DNA_phyml/10taxaExample.stree6 -i examples/large_DNA_phyml/nucleic_M2573_346x897_2006.phy -n Splits -e Nexus -o out.nex
```

`-n` names a data node by **title** and `-e` an exporter; give both or neither (neither writes the whole
workflow as `.stree6`). `-i` accepts a directory with `-x`/`-r`; `-o` accepts a directory, `stdout`, or a `.gz`
name. Legal exporters are listed by `-h`: Clustal, FastA, GML, NeXML, Newick, Nexus, Phylip, PlainText,
NexusWithTaxa.

**Two things to know before trusting the output**, both observed on the run above:

- **A saved `TaxaFilter` is applied by name to the new data.** `10taxaExample.stree6` disables `tax11`…`tax100`;
  the 346-taxon input file happens to use the same naming scheme, so 90 taxa were silently dropped and the
  analysis ran on 256. Check the taxon count the tool prints against the one you expect.
- **Unknown options in an old workflow file are skipped with a warning, not an error**
  (`WARNING: skipped unknown option for 'NeighborNet': 'UsePreconditioner= false'`). An option that has been
  renamed or removed therefore reverts to its current default without failing the run.

## Example data

`examples/` is checked in and is the closest thing this project has to a test corpus:

| Directory | Contents |
|---|---|
| `examples/large_DNA_phyml` | the phyml benchmark alignments, 306–1566 taxa, in `.phy`, plus `10taxaExample.stree6` / `100taxaExample.stree6` workflows |
| `examples/trees` | `grasses.nex`, `full-1001.tree`, `trees-10000x100.tre`, `viridinutans_species.trees` — the large trees inputs |
| `examples/rootednetworks` | `nine.tre`, `new-world-monkey.stree6`, and `bad-layout-example.tre` (kept because it lays out badly) |
| `examples/genomes` | `domestic-mtdna.fasta` + workflow |
| `examples/publications` | datasets reproducing published figures, each with its `.stree6` — the best regression material in the repository |
| `examples/programs` | interoperability samples for SplitsTree4, Dendroscope3, PopART, BEAST2, phangorn, phytools |

`docs/` holds the user manual (`manual.md` in the gh-pages branch, `manual.pdf` here) and its figures.

## Working across the three repositories

Since 2026-08-18 a change can span **jloda3 → splitstree6 → splitstree-py**, and each stage has to be rebuilt
before the next one sees it. The full loop, in order:

```bash
cd ~/IdeaProjects/apps/jloda3 && mvn -o install -DskipTests -q
```

```bash
cd ~/IdeaProjects/apps/splitstree6 && mvn -o package -q
```

```bash
python3 ~/PycharmProjects/splitstree-py/tools/sync_jars.py ~/IdeaProjects/apps/splitstree6
```

Skipping the last step is the classic mistake: the Python package keeps running the **previous** jars, so a
fix looks as though it did nothing. `sync_jars.py` prints the jar count and total size, which is the cheapest
confirmation that it ran.

`razornet-splitstree-bridge` is a fourth consumer, rebuilt with `mvn -o compile` in its own directory. It
depends on the installed `SplitsTree` artifact, so `mvn -o package` here is not enough — splitstree6 must be
`mvn -o install`ed for the bridge to see a change.

## The Python package

`husonlab/splitstree-py`, checked out at `~/PycharmProjects/splitstree-py`. It runs the SplitsTree algorithms
in-process through JPype. `ai/lab/2026-08-17_splitstree-py.md` is the design; what follows is only how to run
it.

**The JVM's architecture must match the Python interpreter's**, because JPype loads it into the same process.
This bites immediately on this machine: `python3` is arm64 and the `java` on `PATH` is an **x86_64 GraalVM 17**,
so the default JVM cannot be used from Python at all — though it remains the right one for `mvn` and for
plain `java` runs, which is what makes the mismatch confusing.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 23 -a arm64)
```

Then, from the package directory:

```bash
python3 -m venv .venv && .venv/bin/pip install -e ".[dev]"
```

```bash
SPLITSTREE_EXAMPLES=~/IdeaProjects/apps/splitstree6/examples .venv/bin/python -m pytest -q
```

412 tests, about 9 seconds. `$SPLITSTREE_EXAMPLES` is optional: without it the corpus sweep skips and
everything else still runs, which is deliberate so a fresh clone is green.

**Regenerate after changing any algorithm or option:**

```bash
.venv/bin/python tools/generate.py
```

`splitstree/algorithms.py` is generated from the jar and checked in, so **a renamed or retyped option shows up
as a signature diff rather than as a bug report** — which is the whole point of generating it. It has happened
already: an enum replaced by two booleans in `HammingDistance` surfaced exactly that way. The generator
compiles its own output before installing it, and replaces an unparseable module with a placeholder so that it
can always run again — necessary, because it imports the package it is regenerating.

Two shell traps, both of which cost time here:

- **`pip install -e "$R[dev]"` silently installs nothing in zsh**, because `$R[dev]` is array subscripting.
  Write `"${R}[dev]"`.
- **`git stash pop` runs in the current directory's repository.** With four checkouts in play, `cd` into the
  right one first; a pop in the wrong repo reports "No stash entries found" and leaves the stash where it was.

## Profiling

The heavy paths are neighbor-net's weight optimisation, the network layouts, and PhyloFusion's search. Java
Flight Recorder is the tool of choice:

```bash
java -XX:StartFlightRecording=filename=run.jfr --module-path target/SplitsTree-1.0.0-SNAPSHOT.jar:target/dependency --add-modules splitstreesix splitstree6.tools.RunWorkflow -w <workflow> -i <input> -o stdout
```

```bash
jfr print --events jdk.ExecutionSample run.jfr | grep -A 6 stackTrace | head -100
```

`PeakMemoryUsageMonitor` is already wired into the tools, so every run prints peak memory on exit; `-t`/`--time`
on `RunWorkflow` sets a wall-clock limit.

For a single algorithm, do not profile through the workflow at all — drive it directly from a probe
(`40_testing.md`), which removes the toolkit, the views and the service machinery from the measurement.

## IDE

IntelliJ IDEA; `.idea/` is **not** checked in here (it is in `.gitignore`, unlike megan8). Two known traps:

- IntelliJ may compile `jloda3` from source as a module dependency rather than using the installed jar, so an
  IDE run and a Maven run can disagree about which version of the library is in play. When a change seems not
  to take effect, check which of the two is being used.
- After a dependency rename, re-importing silently reverts profile selections and can keep a stale Maven model.
  Symptoms and fixes are recorded in `../lab/journal.md` under the fmmm-layout entry. Only edit `.idea/*.xml`
  with IntelliJ closed.

`CLAUDE.md` is in `.gitignore`; the standing instructions live in this directory instead.

## Version control

`git`, one repository per project, main branch `main`. Commit messages in the existing style: one short
imperative line, no trailing period ("Put node coordinates into network GML output", "Read label-less distance
matrices, and export networks as GML"). **Do not commit unless asked.**

Watch what is untracked before you do. As of 2026-08-17 the working tree carries `cactus/complete-graph.py`,
`cactus/use-existing.zsh`, `examples/programs/phytools-cophylo/`,
`examples/publications/ScornavaccaZickmannHuson2011/simple.stree6`, `release-notes/v6.9.6.md` and
`src/main/java/splitstree6/tools/RunPhyloFusion.java` — the last of which is a compiled, working tool that
several notes refer to.

Release notes are one file per release in `release-notes/`, currently up to `v6.9.6.md`.
