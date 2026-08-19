# Plan — a unit-test suite for SplitsTree6

**Status:** proposed, awaiting review.
**Raised by:** Daniel, 2026-08-17 — "We need to create a plan for generating unit testing of all features"
(answering D-7 in `../docs/20_logic.md`).
**Related:** `../docs/40_testing.md`, `../docs/20_logic.md` §4 and §6, `ai/open/todo.md` P3.

---

## 1. Why now

There is no `src/test`, no JUnit dependency, and no workflow that compiles the code on a push. Every claim about this codebase
rests on a probe someone wrote by hand and threw away.

Two things changed on 2026-08-17 that make a real suite cheap rather than expensive, and they are the reason
this plan is worth acting on now:

1. **The workflow runs headless.** Fixing D-1 removed the eager `ProgressPane` from `AService` and gave
   `AlgorithmNode` an inline execution path for when no toolkit is running. A whole workflow — taxa filter,
   data filter, algorithms, the validity cascade — now computes on the calling thread, **synchronously**, with
   no `Platform.startup()`. Measured: an 11-node workflow in ~40 ms, same answers as the toolkit path. That
   means workflow behaviour is testable in an ordinary JUnit method, with no toolkit, no window, no latch and
   no sleep. Before today it was not.
2. **The algorithm catalogue is machine-readable.** Scanning the jar for non-abstract `Algorithm` subclasses
   and interrogating each one takes about a second and needs no Java-side change (`../docs/20_logic.md` §4). So the
   suite does not have to be 102 hand-written test classes: most of it can be **data-driven from the
   catalogue**, which is also the only way it stays complete as algorithms are added.

## 2. What "all features" actually means

The inventory, so that coverage is a number rather than a feeling:

| Surface | Count | Testable how |
|---|---|---|
| concrete algorithms | 102 | data-driven from the catalogue (§5, T2) |
| algorithm options | 177 | round-trip each through `OptionIO`, data-driven (T2) |
| readers | 31 | read every matching file in `examples/`, check dimensions (T3) |
| writers | 44 | write → read → compare, data-driven over the reader×writer matrix (T3) |
| data block types | 12 | construct, copy, clear, taxa-filter, size (T1) |
| pure computational helpers | ~20 classes in `algorithms.utils`, `splits`, `utils` | hand-written, answers derivable on paper (T1) |
| the workflow engine | 1 | now unit-testable headless (T4) |
| `.stree6` persistence | 1 | round-trip, plus the `examples/` corpus (T5) |
| GUI: views, tabs, layouts, dialogs | ~200 classes | **out of scope**, see §8 |

Full coverage of the first eight rows is a realistic goal. The ninth is not, and pretending otherwise would
produce tests that execute code without asserting anything — which is worse than no test.

## 3. Mechanics, and the one question to settle first

- **JUnit 5** (`junit-jupiter`) plus `maven-surefire-plugin`, both `<scope>test</scope>`. New source root
  `src/test/java/splitstree6/…`, mirroring the main package layout.
- **Run the tests on the classpath, not the module path** — `<useModulePath>false</useModulePath>`. This is
  settled rather than open: every probe written on 2026-08-17 ran on the plain classpath against
  `target/classes` plus `target/dependency`, including `Option` reflection over algorithm packages, which on
  the module path would need `opens`. The classpath makes `module-info.java` irrelevant to the tests and makes
  package-private classes reachable from a test in the same package. **Confirm this in step 1 and stop
  thinking about it.**
- **A JavaFX toolkit must never be started by a test.** After D-1 nothing needs one. A test that finds itself
  wanting `Platform.startup()` is a test of the GUI and belongs in §8, not in the suite.
- **`mvn test` must stay green and fast.** Target: the whole suite under 30 seconds, T1+T2 under 10. Anything
  that needs a large dataset belongs in the separate regression run (T5), not in `mvn test`.
- **No production code changed for testability without asking.** If a test needs a seam, propose it. Note that
  most things are already reachable: algorithms have public no-argument constructors and a public `compute`,
  readers and writers are public, and a test in the same package sees package-private members.

## 4. The principle that decides whether this suite is worth having

**Every expected value must be derivable on paper, or recorded deliberately with a reason.** The failure mode
of a suite written after the fact is that it encodes today's behaviour as correct — including today's bugs —
and then blocks the fix. Two consequences:

- for T1, the test must be able to say *why* the answer is what it is (a five-taxon matrix whose neighbor-net
  splits you can enumerate by hand, a compatibility test on splits you drew);
- for anything golden-file based (T2's numeric outputs, T5), the file is a **characterization** fixture, must
  be labelled as such in the test, and a diff is a question ("did we mean to change this?"), not a failure to
  be silenced by re-recording. Re-record only in the same commit as the change that justifies it, with the
  numbers in `journal.md`.

## 5. The tiers

### T1 — pure functions (hand-written, highest value per line)

No fixtures, no files, milliseconds. In priority order:

| Target | What to assert |
|---|---|
| `splits.ASplit`, `splits.Compatibility` | compatible/incompatible/weakly-compatible/circular classification on hand-drawn split sets; split normalisation; `intersect2`; the empty and singleton edge cases |
| `algorithms.utils.SplitsBlockUtilities` | fit computation against a matrix whose fit you can compute; cycle validity; removal of trivial and zero-weight splits |
| `algorithms.utils.GreedyCircular` / `GreedyCompatible` / `GreedyWeaklyCompatible` | that the result is maximal and has the claimed property; ties broken deterministically |
| `splits.SplitNewick` | round-trip a split system to text and back |
| `utils.TreesUtils`, `utils.ClusterUtils` | cluster ↔ tree correspondence, LSA/ancestor relations on a small hand-built tree |
| `algorithms.utils.TreeMutualRefinement` | refinement of two small trees whose refinement you worked out |
| `data.*Block` | `copy`, `clear`, `newInstance`, `size`, one-based accessor bounds; that `createTaxaDataFilter` restricts to a taxon subset correctly, per block type |
| `utils.RunningJobs` | track/add/remove/await, including the timeout path (already probed on 2026-08-17: a job ending at 700 ms was awaited in 705 ms; a 300 ms timeout returned false at 302 ms) |

### T2 — every algorithm, data-driven from the catalogue

One JUnit `@TestFactory` (or `@ParameterizedTest` over a provider) that scans the jar exactly as the 2026-08-17
probe did, and for **each of the 102 algorithms** asserts:

1. it instantiates through its public no-argument constructor;
2. `getFromClass()`/`getToClass()` are consistent with the package it lives in and with the abstract base it
   extends;
3. every option in `Option.getAllOptions(...)` round-trips: read the value, write it through `OptionIO`, read
   it back, compare — this is 177 assertions for free, and it is what catches a renamed or retyped option;
4. `listOptions()` names only options that exist;
5. `getToolTip(name)` returns something other than the bare option name for every listed option **(this will
   fail today for many; see §7 — it is a documentation debt the suite should make visible, so start it as a
   reported warning rather than a failure)**;
6. `getCitation()` is either null or parses into `short;full;` pairs — **38 algorithms return null today**
   (`../docs/20_logic.md` §4), so assert the *format* and report the count, do not fail on absence yet;
7. given a canonical input of its `fromClass` (§6), either `isApplicable` is false, or `compute` runs without
   throwing and produces a non-empty output.

Point 7 is the one that earns its keep: it is a real smoke test of every algorithm in the program, from about
two hundred lines of test code, and it grows automatically when an algorithm is added. Where an algorithm needs
something more specific than the canonical input — rooted trees, circular splits, DNA rather than protein — the
fixture registry (§6) holds a per-algorithm override, and an algorithm with neither a canonical input nor an
override is **listed in the test output as uncovered**. That list is the coverage metric.

On top of the smoke test, a small number of algorithms get real numeric assertions by hand: `NeighborNet`,
`NeighborJoining`, `BioNJ`, `UPGMA`, `SplitDecomposition`, `HammingDistance`, `ConsensusNetwork`,
`PhyloFusion`. These are the ones people cite; their answers on a five- or six-taxon input can be worked out
independently.

### T3 — readers and writers, data-driven over the matrix

- **Readers**: for every reader, every file in `examples/` that it claims to accept — read it and assert the
  taxon count and the block dimensions. `ImportManager.determineInputType` must agree with the reader that
  actually succeeds. This also pins down format auto-detection, which nothing tests today.
- **Writers**: for every (block type, writer) pair, write a canonical block and read it back with the matching
  reader where one exists; assert the data survives. 44 writers × the blocks they accept is a loop, not 44
  test methods.
- Explicitly assert the **one-based** conventions at the boundary: a distance matrix written and read back must
  have the same `get(i,j)` for all one-based i, j.

### T4 — the workflow engine, now that it is headless

This tier is new and is the reason to do the work now. All of it in plain JUnit, no toolkit:

- the standard skeleton has the expected shape, and the distinguished-node accessors find their nodes
  **after the nodes have been renamed** (the D-3 fix — probed on 2026-08-17, worth freezing as a test);
- validity propagation: invalidating a node invalidates exactly its descendants; recomputation reaches the
  leaves; `workflow.isValid()` becomes true;
- the taxa filter: disabling a taxon changes the working taxa and propagates through the data filter to every
  downstream block, for each data block type;
- an empty input produces an empty output and the note, not an exception (the D-6 behaviour);
- an inapplicable non-empty input throws `"Algorithm is not applicable to given input data"`;
- data-node title allocation and reuse after deletion (the D-4 fix, probed on 2026-08-17);
- `shallowCopy` preserves structure and the distinguished-node references.

### T5 — persistence and the regression corpus

- **`.stree6` round trip** in `mvn test`: build a workflow, `WorkflowNexusOutput`, `WorkflowNexusInput`, and
  assert the structure, the algorithms and **all 177 options** come back identical. This is the test that
  protects every user's saved analysis and there is nothing like it today.
- **The `examples/publications/` corpus**, as a script rather than as JUnit: each dataset is paired with the
  `.stree6` that reproduces a published figure. `testing/run-examples.sh` should run each through
  `RunWorkflow`, dump the resulting blocks and diff against stored output, exiting non-zero on any difference.
  This needs no new data, catches whole-pipeline regressions, and is the closest thing to "did we still get
  the published answer". Keep it out of `mvn test` — it is minutes, not seconds.

## 6. Fixtures

One class, `splitstree6.TestFixtures`, providing a canonical instance of each block type, all small enough to
reason about:

- `taxa(n)` — n taxa named `a`, `b`, …;
- `distances5()` — the five-taxon matrix already used in the 2026-08-17 probes, whose neighbor-net gives 7
  splits at fit 100 % and whose NJ tree is `(((a:1,b:1):3,c:1):1,d:1,e:1)`;
- `characters()` — a short DNA alignment; a protein one; one with ambiguity codes; one with gaps;
- `splits()` — a circular split system with its cycle; a compatible one; an incompatible one;
- `trees()` — an unrooted pair; a rooted pair; a reticulate network; a partial-taxa set;
- `network()`, `genomes()`, `traits()`, `sets()`.

Plus a registry mapping an algorithm class to a non-default input where the canonical one will not do. Keeping
the fixtures tiny is what makes T2's failures diagnosable: when a smoke test fails on a six-taxon input you can
print the whole input in the failure message.

## 7. Order of work

1. **Add `src/test/java`, junit-jupiter and surefire; one trivial test; `mvn test` green.** Confirm the
   classpath setting. Time-box: an hour. *Verification: `mvn test` runs and reports 1 test.*
2. **T1 for the split machinery** — `ASplit`, `Compatibility`, `SplitsBlockUtilities`. These are the
   mathematical core and the answers are checkable by hand. *Verification: each expected value has a comment
   saying how it was derived.*
3. **`TestFixtures`**, driven by what step 2 needed. *Verification: the five-taxon fixture reproduces the
   probe numbers exactly.*
4. **T2, the catalogue-driven smoke test.** Start with points 1–4 and 7; report points 5 and 6 as warnings
   with counts. *Verification: the number of algorithms exercised equals 102 minus the reported uncovered
   list, and that list is short and explained.*
5. **T4, the workflow tests.** These are the newly-possible ones and the ones that protect today's fixes.
   *Verification: the D-3, D-4 and D-6 probes of 2026-08-17 become tests and pass.*
6. **T5's `.stree6` round trip.** *Verification: all 177 options survive.*
7. **T3, readers and writers.** *Verification: every file in `examples/` that some reader claims is read.*
8. **The `examples/publications/` regression script**, and only then a GitHub Actions workflow triggered
   `on: push` that runs `mvn -o package` and `mvn test`. The repository has no push-triggered workflow at all
   today: `deploy-docs.yml` fires only on changes under `docs/`, and the installer workflows in
   `build-installers` are `workflow_dispatch`, i.e. started by hand to cut a release.

## 8. Risks and non-goals

- **The GUI is out of scope.** Views, tabs, layouts, dialogs, the workflow editor: no toolkit in the test JVM,
  no TestFX, no screenshots. This is a deliberate line, and it means the suite will not catch a class of real
  bugs — the intermittent blank tree view of 7.2026 among them. Say so rather than implying coverage we do not
  have. (One thing it *would* catch cheaply: the headless `AlgorithmTabsManager` exception seen intermittently
  during a `RunWorkflow` tanglegram run on 2026-08-17, which does not affect the output but should not happen.)
- **Do not chase a coverage percentage.** 102 algorithms smoke-tested and 20 pure functions properly asserted
  is worth more than 60 % line coverage of the view package.
- **Do not encode current behaviour as correct** — §4. Several plausible assertions would have passed on the
  bugs fixed today.
- **Golden files rot.** Every one needs an owner comment saying what it is and when it was recorded.
- **The suite must not need a display, a network, or `examples/` to be green.** `mvn test` on a fresh clone
  with no data must pass; the corpus run is separate and may need more.
- **T2 will find failures on day one.** That is the point, but it means step 4 should be expected to produce a
  list of broken or unrunnable algorithms rather than a green bar, and that list needs Daniel's triage before
  anything is "fixed".
