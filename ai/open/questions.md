# The frontier — open questions

Last updated **2026-08-19**. Branch `ai-docs-and-workflow-fixes`.

Questions, not tasks. A question belongs here when we do not know the answer; a job whose answer is
already known belongs in `todo.md`. Nothing here has an owner or a date — it has a **gate**: the
measurement that would settle it, written down before it is taken.

When you take one up, open an entry from `../lab/TEMPLATE.md` (or run `/probe`), and check
`../lab/INDEX.md` first in case it has already been answered.

---

## Read before designing any experiment

Settled facts that will quietly ruin a measurement if forgotten.

- **The JavaFX boundary sits at the views, not at the workflow.** Since 2026-08-17 a whole workflow
  computes headless and synchronously (`../docs/20_logic.md` §6). Code written before that date still
  assumes otherwise — `RunWorkflow` extends `Application` only to get a toolkit — so **do not take an
  existing pattern as evidence of a current constraint.**
- **Geometry is not GUI code, despite the imports.** `SplitNetworkLayout`, `ComputeTreeLayout` and
  `NetworkLayout` import `javafx.scene.*`, but each wraps a pure routine filling a
  `NodeArray<Point2D>`. Do not conclude from a grep that a layout needs a toolkit; §6.2a names the
  four entry points that do not.
- **We have no stored baselines.** "Did this change anything?" can only be answered within a single
  session. Record what you measure in `../lab/measurements.md` or it is gone.
- **Any bootstrap number from before `b995795c` is worthless** — a non-atomic aggregation made
  support values irreproducible even with `optionRandomSeed` set.
- **The example workflows carry dead options** (`UsePreconditioner`, `UseDual`, `Normalize`,
  `ShowConfidence`). They exercise the pipeline but do not pin down current defaults.
- **Option defaults are a published interface.** They are what a user gets with no saved value, and
  what an old `.stree6` falls back to when an option is renamed — silently, with only a warning.
- **Two taxa blocks, two data blocks.** An algorithm reaching for `workflow.getInputTaxaBlock()`
  instead of the block it was handed is wrong exactly when a taxon is deselected — the case nobody
  tests.
- **One-based indexing everywhere**, matching nexus. A zero-based API will be wrong in a way that is
  hard to see.
- **`getSymbols()` is the legal characters, `getStateSymbols()` is the states.** They differ for
  nucleotide data read from phylip or FASTA. Anything counting, indexing or sizing over states wants
  `getStateSymbols()`. Getting this wrong left LogDet undefined for every pair for years.
- **A non-state symbol is either expanded or treated as missing, and which depends on the data type.**
  Nucleotide ambiguity codes are expanded; protein's `b`, `z`, `x` and the stop codon `*` map onto
  missing. In `PairwiseCompare` the gap test comes first *on purpose* — `ungulates.nex` declares
  `gap=x`.
- **The ambiguity codes are written in terms of DNA.** `AmbiguityCodes.getNucleotides` expands `y` to
  `"ct"`, wrong for RNA; use the `(code, alphabet)` overload.
- **`PairwiseCompare.getNumNotMissing()` now counts exactly the sites `getF()` normalises over.**
  Keep the invariant if you touch `calculatePairwiseCompare`: the site is counted where the mass is
  added, not where the characters are read.
- **A non-finite distance is caught centrally now, but do not rely on it.** Guard divisions at source:
  a NaN reaching `LogDet` makes `jama`'s eigenvalue decomposition **spin forever** — it hangs rather
  than returning a bad number.
- **`PairwiseCompare` throws `ArrayIndexOutOfBoundsException`** rather than reporting when an
  algorithm meets a data type it does not accept (`ProteinMLDistance` on DNA). Only reachable from
  code; the GUI gates on `isApplicable`.
- **The inline path is recursive.** Headless, `setValid(true)` cascades within the call, so a
  workflow computes depth-first on one stack. Fine for any real workflow; worth remembering for a
  generated one with a very long chain.
- **`splitstree6.xtra` is read-only.** Copy out of it, never move or edit — `../docs/10_context.md`
  rule 12.
- **`RunAfterAWhile` keys must be per-instance.** A content-derived key produced intermittently blank
  tree views in 7.2026 and took a long time to find.
- **The JavaFX native classifier follows the JVM Maven runs on**, and **fmmm-layout needs JDK 22+ and
  `-Pdesktop`** or it silently falls back. Check the startup line `Graph layout: …`.

---

## Open — worth an entry

### Q1. What leaves `System.err` replaced? (D-12)

Running the whole algorithm catalogue in sequence ends with `System.err` pointing at a discarding
stream. **No single algorithm reproduces it**, and every hide/restore call site looks correct. This is
an unfinished diagnosis, not a closed question.

**Gate:** bisect the catalogue — run halves, then quarters, until the shortest sequence that
reproduces it is found. If no *sequence* reproduces it either, the cause is ordering or concurrency
and the question changes shape. Low impact, so time-box it.

### Q2. Do the readers and writers agree with each other?

31 readers, 44 writers, and **no test that any pair round-trips**. Two known asymmetries already:
the plain `Nexus` exporter writes a bare block its own reader will not read back, and `nj.workf6`
reads as *empty* rather than failing.

**Gate:** T3 of the test plan — write every block type with every writer that claims it, read it
back, compare. The number that matters is how many of the 31×44 admissible pairs survive.

### Q3. Does `splitstree.py` ship one wheel or five?

`.github/workflows/portability-probe.yml` builds once with the macOS JavaFX jars and runs them on
ubuntu, windows and macos. Written and verified locally on 2026-08-17; **never run**.

**Gate:** the probe itself — but it needs the jloda3 headless-workflow commit pushed first, or the
"whole workflow, no toolkit" check fails for a reason that has nothing to do with portability.
Currently **parked** on that push.

### Q4. How many options are undocumented but look documented?

`Option`'s constructor falls back to `StringUtils.fromCamelCase(name)` when `getToolTip` returns
nothing useful, so an option with no tooltip still looks documented in the GUI **and in the methods
text**. We do not know how many of the 177 options this affects.

**Gate:** T2 point 5 counts them. If the count is small, fill them; if it is most of them, the
fallback is the problem, not the options.

### Q5. Why does `AlgorithmTabsManager` throw in a headless run?

Seen 2026-08-17 on the nymphoides tanglegram workflow: a stack trace from
`AlgorithmTabsManager.lambda$new$1` via `Platform.runLater`, in **one run out of three**. Output
unaffected — two runs were byte-identical apart from the creation timestamp — but a GUI listener
should not be throwing in a tool. Pre-existing.

**Gate:** it is intermittent at 1-in-3, so ten runs are enough to confirm the rate before and after
any change. Do not read a single clean run as a fix.

---

## Not questions — decisions waiting on Daniel

- **Confirm `HammingDistance.optionMatchAmbiguityCodes`'s default** (raised 2026-08-19). The rewrite
  replaced the `AmbiguousOptions` enum with two booleans and had to pick one. It is **true**, on the
  grounds that this preserves the old `Ignore` default's *behaviour* — an ambiguous site produced no
  difference then and produces none now — whereas false would silently start counting Y against C as
  a difference in every existing nucleotide dataset. One word to flip if that reasoning is wrong.
- **Read the test-suite plan** (`../lab/2026-08-17_test-suite.md`). Still a proposal with no code
  written against it.
- **Triage what the test suite will find.** Step 4 is expected to produce a list of algorithms that
  fail their smoke test or cannot be run on a canonical input. Some will be real bugs, some missing
  fixtures, some algorithms not meant to be run that way. That judgement is Daniel's.
- **The six unreadable example files** (`todo.md`) — fix the reader, fix the file, or accept it, one
  decision each.
- **The ~22 algorithms with no citation** — `getCitation()` feeds `ExtractCitations` and hence the
  methods text, so each is a silent hole in every analysis that uses it. The test suite reports the
  count; filling them needs Daniel.
