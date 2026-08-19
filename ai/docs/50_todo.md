# 50 — Current tasks, open questions and concerns

Last updated: **2026-08-19**. Branch `main`.

## Standing task — always

**Check that the code still does what these files say it does**, and that they still say what we mean. Every
session that touches the workflow machinery or an algorithm ends with either "checked, consistent" or a new
entry in the discrepancy section of the relevant file. Do not let the two drift apart silently. When you change
something, the change is not finished until the logic file, the class javadoc, the in-code comments, this file
and `60_agent_notes.md` all say the new truth.

---

## Priority 1 — decisions needed from Daniel

- [ ] **`splitstree.py` step 5, the generator.** Steps 1–4 are done in `husonlab/splitstree-py` (spike, JVM
      bootstrap, block layer, IO — 83 tests). Step 5 is the big one: one data-driven generator producing the
      whole algorithm surface from the jar scan. Both decisions that were blocking it are taken (2026-08-18):
      `quiet` defaults to **true**, capturing Java's stderr into a buffer `java_messages()` returns rather
      than discarding it — Daniel's reasoning being that Java users do not see that output either unless they
      open the message window; and the jars **stay gitignored**, synced by `tools/sync_jars.py`.

- [ ] **Confirm the `HammingDistance.optionMatchAmbiguityCodes` default** (raised 2026-08-19). The rewrite
      replaced the `AmbiguousOptions` enum with two booleans and had to pick a default for the ambiguity flag,
      which Daniel did not specify. It is **true**, on the grounds that this preserves the old `Ignore`
      default's *behaviour* — an ambiguous site produced no difference then and produces none now — whereas
      false would silently start counting Y against C as a difference in every existing nucleotide dataset.
      One word to flip if that reasoning is not what is wanted. See `60_agent_notes.md`, 2026-08-19.

- [ ] **Let more Java exceptions propagate instead of being printed** (SplitsTree-side, raised 2026-08-18).
      With `quiet` on, anything jloda `Basic.caught`s and prints goes into the capture buffer rather than to a
      user. That is no worse than the GUI, where it goes to a message window nobody opens, but the right fix
      is for those paths to throw rather than print. Worth a pass when someone is next in that code.

- [ ] ~~**`splitstree.py`: decide whether to start.**~~ Started. `ai/plans/2026-08-17_splitstree-py.md` is no longer
      blocked — Daniel answered all six questions in §9 (snake_case; report algorithms in; numpy required with
      the rest as extras; separate repo `husonlab/splitstree-py`, PyPI name `splitstree`; make `AService` lazy,
      done; report coordinates, probed and possible). Both risks the plan was carrying are now measured and
      closed: the workflow runs headless, and so do the layouts. The PyPI name `splitstree` is free (checked
      2026-08-17). **Nothing is outstanding except the decision to start**, plus one build-shape question that
      `.github/workflows/portability-probe.yml` answers on its own (see below).

- [ ] **Read the test-suite plan.** `2026-08-17_test-suite.md` is still a proposal with no code written
      against it.

- [ ] **Triage what the test suite will find.** Step 4 of the test plan is expected to produce a list of
      algorithms that fail their smoke test or cannot be run on a canonical input. That list needs Daniel's
      judgement — some will be real bugs, some will be missing fixtures, some will be algorithms that are
      simply not meant to be run that way.

---

## Priority 2 — planned work, awaiting review of the plan

- [ ] **The `splitstree.py` package.** `ai/plans/2026-08-17_splitstree-py.md`.
- [ ] **The unit-test suite.** `ai/plans/2026-08-17_test-suite.md`. Tiered: pure functions by hand, then one
      data-driven test over all 102 algorithms and 177 options, then readers/writers, then the workflow engine
      (newly possible), then `.stree6` round trips and the `examples/publications/` corpus. GUI explicitly out
      of scope.

---

## Priority 3 — infrastructure

- [ ] **Run the portability probe.** `.github/workflows/portability-probe.yml`, added 2026-08-17. It builds
      once with the macOS JavaFX jars and runs them on ubuntu, windows and macos, which decides whether
      `splitstree.py` ships one wheel or five. **It needs the jloda3 headless-workflow commit pushed first** —
      the workflow checks jloda3 out from GitHub, and without that commit the "whole workflow, no toolkit"
      check fails for a reason that has nothing to do with portability. Never run; the YAML and every command
      in it were verified locally, but no Actions run has happened.

- [ ] **Six files in `examples/` cannot be read by the readers that claim them.** Found 2026-08-18 by reading
      the whole corpus from Python (171/177 succeed). Each needs a decision — fix the reader, fix the file, or
      accept it: `genomes/domestic-mtdna.fasta` is ragged at line 482; three Nexml/BEAST2 XML files give
      "No trees found" (two are BEAST2 *input* XML that the detector over-claims);
      `Gruenstaeudl2019/.../ML-trees-rerooted.tree` is Newick the reader rejects at position 81; and
      `.../nj.workf6` is a SplitsTree5 workflow file claimed as Nexus characters, which then **reads as empty
      rather than failing** — the worst kind. Listed in `tests/test_io.py::KNOWN_UNREADABLE` in splitstree-py.

- [ ] **`ExportManager` leaks the prepend-taxa flag.** `write(..., "NexusWithTaxa", ...)` sets
      `optionPrependTaxa` on the **shared** Nexus writer instance and never clears it, so every later
      plain-Nexus write in the same process also prepends taxa. Found 2026-08-18. Also worth noting that the
      plain `Nexus` exporter writes a bare block that SplitsTree's own reader will not read back — correct for
      the GUI, which concatenates blocks, but a trap for anything writing standalone files.

- [ ] **D-12: find what leaves `System.err` replaced.** Running the whole algorithm catalogue in sequence ends
      with `System.err` pointing at a discarding stream; no single algorithm reproduces it and every
      hide/restore call site looks correct. See `20_logic.md` §10 D-12. Low impact, but it is an unfinished
      diagnosis rather than a closed question.

- [x] ~~**The bootstrap separation.**~~ **Done 2026-08-19, all three** — `BootstrapSplits` `b995795c`,
      `BootstrapTreeSplits` `f4e4e885`, `BootstrapTree` `0cd540cb`, plan
      `ai/plans/2026-08-19_bootstrapping.md`. Each keeps its workflow entry point, which now reads the
      alignment and the pipeline out of the workflow and delegates to a form taking both explicitly. **The
      thing to remember from it:** `b995795c` also fixed a non-atomic aggregation that made support values
      irreproducible even with `optionRandomSeed` set, so **any bootstrap baseline recorded before that commit
      is worthless**.

      Also resolved along the way: `MedianJoining`, `MinSpanningNetwork`, `MinSpanningTree` and
      `ExternalDistance2Network` (`f2ed09c2`, via `IUsesCharacters`), and `RazorHaplotypeNetwork` in the bridge
      repo (`4b37433`, via an explicit `BitSet` of active sites).

- [ ] **D-8: make `ProgramProperties`'s font lazy** (jloda3). Its static initialiser calls
      `javafx.scene.text.Font.font(...)`, so constructing almost any algorithm tries to start a graphics
      pipeline. Caught and harmless, but it prints alarming `UnsatisfiedLinkError` noise when the natives do
      not match, and it is the same shape of bug as D-1 in the same class family. See `20_logic.md` §10 D-8.

- [ ] **Nothing checks that a commit compiles.** The only GitHub Actions workflow in this repository,
      `deploy-docs.yml`, rebuilds the Pages site when `docs/` changes. The installer workflows in
      `build-installers` are `workflow_dispatch` — Daniel starts them by hand to cut a release — so nothing
      runs on a push. A workflow triggered `on: push` that does no more than `mvn -o package` would catch a
      commit that does not compile, which is the cheapest possible check and the one this repository lacks.
      Do this with step 8 of the test plan.

- [ ] **Stored baselines.** There is no recorded reference output for any dataset, so "did this change
      anything?" can only be answered within a single session. `examples/publications/` — datasets paired with
      the `.stree6` that reproduces a published figure — is the natural corpus; T5 of the test plan covers it.

- [ ] **`RunPhyloFusion.java` is untracked.** It compiles, it works, and both `20_logic.md` and `40_testing.md`
      cite it as the worked example of headless algorithm use. Either commit it or stop citing it.

- [ ] **Reader/writer round trips are unchecked.** 31 readers, 44 writers, no test that any pair agrees.
      T3 of the test plan.

- [ ] **38 of the 102 concrete algorithms return no citation.** `getCitation()` feeds `ExtractCitations` and
      hence the methods text, so each one is a silent hole in every analysis that uses it (`20_logic.md` §8).
      Filters, loaders and view producers are legitimately uncitable — but that accounts for 16 at most, so
      roughly 22 real algorithms are missing theirs. The test suite reports the count (T2, point 6); filling
      the gaps needs Daniel.

- [ ] **Tooltips default to the option name.** `Option`'s constructor falls back to
      `StringUtils.fromCamelCase(name)` when `getToolTip` returns nothing useful, so an option with no tooltip
      still looks documented in the GUI and in the methods text. T2 point 5 will count these.

- [ ] **`AlgorithmTabsManager` throws intermittently in a headless `RunWorkflow` run.** Seen on 2026-08-17
      running the nymphoides tanglegram workflow: a stack trace from
      `AlgorithmTabsManager.lambda$new$1` via `Platform.runLater`, in one run out of three. It does **not**
      affect the output — two runs were byte-identical apart from the creation timestamp — but a GUI listener
      should not be throwing in a tool. Pre-existing; not introduced by the 2026-08-17 fixes.

---

## Concerns worth keeping in view

- **The JavaFX boundary now sits at the views, not at the workflow.** Since 2026-08-17 a whole workflow
  computes headless and synchronously (`20_logic.md` §6). Code written before that date still assumes
  otherwise — `RunWorkflow` extends `Application` only to get a toolkit — so do not take an existing pattern
  as evidence of a current constraint. Re-read §6 before answering any question about servers, bindings,
  automated builds or headless use.
- **Geometry is not GUI code, despite the imports.** `SplitNetworkLayout`, `ComputeTreeLayout` and
  `NetworkLayout` import `javafx.scene.*`, but each is a wrapper around a pure routine that fills a
  `NodeArray<Point2D>`. Do not conclude from a grep that a layout needs a toolkit; see `20_logic.md` §6.2a for
  the four entry points that do not.
- **The inline path is recursive.** Headless, `setValid(true)` cascades within the call, so a workflow is
  computed depth-first on one stack. Fine for any real workflow; worth remembering if one is ever generated
  with a very long chain.
- **Option defaults are a published interface.** They are what a user gets when they have not saved an explicit
  value, and they are what an old `.stree6` file falls back to when an option has been renamed — silently, with
  only a warning. Changing one changes everybody's answers.
- **`PairwiseCompare.getNumNotMissing()` now counts exactly the sites `getF()` normalises over**, fixed
  2026-08-19. With unit character weights it equals the sum of the states-by-states block of `fCount`, so
  `proportion * getNumNotMissing()` is a valid count of sites — it was not before, and that is what made
  `HammingDistance` wrong. Keep the invariant if you touch `calculatePairwiseCompare`: the site is counted
  where the mass is added, not where the characters are read.
- **A non-state symbol is either expanded or treated as missing, and which one depends on the data type.** The
  nucleotide ambiguity codes are expanded into the bases they stand for; protein's `b`, `z`, `x` and the stop
  codon `*` are mapped onto the missing index, there being no machinery to expand them. In `PairwiseCompare`
  the gap test comes first on purpose - `ungulates.nex` declares `gap=x`, and there `x` is a gap, not missing.
- **LogDet needs every state observed in a pair.** With 20 protein states that often fails on skewed
  alignments: 99.1 % of pairs in `ungulates.nex` come back undefined, because 5 of its 156 156 characters are
  `l`. That is the data, not a defect - unlike the structural singularity fixed on 2026-08-19, where rows
  existed that no data could ever fill.
- **`getSymbols()` is the legal characters, `getStateSymbols()` is the states.** They differ for nucleotide
  data read from phylip or FASTA, whose readers set the symbols from the observed alphabet and so include the
  ambiguity codes; a nexus file declaring `symbols="acgt"` makes them identical, which is why this was
  invisible on the nexus examples for years. Anything counting states, indexing states, or sizing a frequency
  vector wants `getStateSymbols()`. Getting this wrong left LogDet undefined for every pair and put `h` where
  a model expected π_T.
- **The ambiguity codes are written in terms of DNA.** `AmbiguityCodes.getNucleotides` expands `y` to `"ct"`,
  which is wrong for RNA. Use the `getNucleotides(code, alphabet)` overload, which maps `t` onto `u` for an
  alphabet that has `u` and no `t`; `HammingDistance` folds the other way, `u` onto `t`, because its bitmask
  is indexed over `acgt`. Getting this wrong used to make every RNA sequence containing a code unusable.
- **`PairwiseCompare` still throws `ArrayIndexOutOfBoundsException`** rather than reporting, when an algorithm
  meets a data type it does not accept (`ProteinMLDistance` on DNA). Only reachable from code; the GUI gates
  on `isApplicable`.
- **A non-finite distance is now caught centrally, but do not rely on it.** `FixUndefinedDistances` treats any
  NaN or infinity as undefined, not just `-1`. Guard divisions at source anyway: a NaN reaching `LogDet` makes
  `jama`'s eigenvalue decomposition spin forever, and the algorithm hangs rather than returning a bad number.
- **Two taxa blocks, two data blocks.** An algorithm that reaches for `workflow.getInputTaxaBlock()` instead of
  the taxa block it was handed will be wrong exactly when a taxon is deselected — which is to say, in the case
  nobody tests.
- **One-based indexing everywhere**, matching nexus. A new API that takes zero-based indices will be wrong in a
  way that is hard to see.
- **`splitstree6.xtra` is read-only.** Copy out of it, never move or edit; see `10_context.md` rule 9.
- **`RunAfterAWhile` keys must be per-instance.** A content-derived key produced intermittently blank tree
  views in 7.2026 and took a long time to find.
- **The JavaFX native classifier follows the JVM Maven runs on**, and this machine's default `java` is an
  x86_64 GraalVM 17 on an arm64 Mac. A build with an arm64 JDK produces jars that the default JVM cannot run.
- **fmmm-layout needs JDK 22+ and `-Pdesktop` on the command line**; on JDK 17 it silently falls back to
  jloda's own layout. The startup line `Graph layout: …` says which one you got.
- **The example workflows carry dead options** (`UsePreconditioner`, `UseDual`, `Normalize`, `ShowConfidence`),
  which load with a warning. They exercise the pipeline but do not pin down current defaults.

---

## Recently completed

- 2026-08-17 — **the layouts were probed and do report coordinates headless** (`20_logic.md` §6.2a): outline,
  equal angle, all four rooted layouts on a reticulate network, and the general-network SPI. Outline and equal
  angle agree on every taxon coordinate. This closes the last open engineering risk in the `splitstree.py`
  plan (§9.6). Not available headless, by nature: curved edge control points, label placement, pane scaling.
- 2026-08-17 — **D-1 to D-7 answered by Daniel and addressed.** Six code fixes and one plan; details and
  measurements in `20_logic.md` §10 and `60_agent_notes.md`. In short: the workflow now runs headless and
  synchronously (jloda3: lazy `ProgressPane` plus an inline execution path in `AlgorithmNode`); view jobs are
  tracked through the new `splitstree6.utils.RunningJobs` instead of a polled static list on `RunWorkflow`;
  the distinguished workflow nodes are held by reference so a rename cannot detach them; data-node titles are
  registered in the data-node map, so a deleted title is actually freed; an empty input is reported instead of
  swallowed; the source node was found to be load-bearing and is now documented rather than removed. All
  verified against a `RunWorkflow` regression that came out byte-identical.
- 2026-08-19 — **Protein ambiguity treated as missing, and the RNA ambiguity-code list completed.** `b`, `z`,
  `x` and the stop codon `*` are no longer states, so protein has 20 rather than 24; `PairwiseCompare` maps
  them onto the missing index instead of expanding them, the gap test still winning so that `ungulates.nex`'s
  `gap=x` keeps its meaning. **LogDet on protein went from undefined for every pair to a working matrix** on
  `buttercups-cytochromeC.fasta` (mean 0.134, 1 of 4950 pairs at the replacement value); on `ungulates.nex` it
  stays 99.1 % undefined, which is the alignment's skew and not a defect. `RNAwithAmbiguityCodes` was
  `acgury`, two of the eleven codes, so RNA using `w` or `n` was typed `Unknown` and then failed with
  `invalid character 't'`; it is now `acguryswkmbdhvn` and such data runs end to end. Details in
  `60_agent_notes.md`.
- 2026-08-19 — **Ambiguity codes are no longer counted as states.** `getSymbols()` was serving both as the
  legal-character list and as the state list; for nucleotide data read from phylip or FASTA those differ.
  New `CharactersType.getNonStateSymbols()` and `CharactersBlock.getStateSymbols()`, used by
  `PairwiseCompare`, `NucleotideModel.computeFreqs`, `BaseFreqDistance`, `PhiTest` and `LogDet.isApplicable`.
  **LogDet on the 346-taxon phyml alignment went from undefined for all 59 685 pairs to a working matrix**
  (mean 0.111), and base frequencies from a vector of length 11 with π_T = 0 to a correct four. Two further
  defects fixed in `computeFreqs`: loops that skipped the first taxon, the first site and the last site of
  every base frequency ever reported, and a denominator counting characters the numerator did not. Nexus
  output unchanged, verified by round trip. Details in `60_agent_notes.md`.
- 2026-08-19 — **RNA with ambiguity codes made to work.** `AmbiguityCodes` expands codes to DNA bases, so `y`
  in RNA data gave `t`, which is not one of RNA's symbols: `PairwiseCompare` either indexed `fCount[-1][-1]`
  or reported `invalid character 't'` for a character not in the data, making every such sequence unusable.
  New `AmbiguityCodes.getNucleotides(code, alphabet)` overload holds the fold; RNA now returns exactly the
  numbers equivalent DNA returns. Details in `60_agent_notes.md`.
- 2026-08-19 — **`PairwiseCompare.getNumNotMissing()` redefined to agree with `getF()`**: it counts the sites
  that reach the states-by-states block of `fCount`, not every site whose characters were neither gap nor
  missing. On the phyml alignment the two disagreed for 1098 of 1770 pairs, worst case by 43 sites, and the
  old `HammingDistance` formula `round(p * numNotMissing)` was wrong for 402 of them; both are now 0. No
  current algorithm's numbers change — the method has no callers outside the class.
- 2026-08-19 — **Four divisions by zero fixed**, in `PairwiseCompare` (`getF`, `mlDistance`, `bulmerVariance`)
  and `GeneSharingDistance`, plus `FixUndefinedDistances` now treating any non-finite entry as undefined. The
  NaN these produced was not merely a bad number: it made `LogDet` hang forever inside `jama`'s eigenvalue
  decomposition, and it silently turned every undefined entry of the same matrix into NaN as well. It reached
  shipped data — 35 non-finite Gene Sharing entries on `dolphins_binary.nex`, now 0. Everything else
  unchanged over three alignments. Details in `60_agent_notes.md`.
- 2026-08-19 — **`HammingDistance` (and so `PDistance`) rewritten.** It was not computing a Hamming distance:
  the unnormalized branch rescaled a proportion by a mismatched site count, and the `MatchStates` branch
  charged a fractional cost for compatible codes, ignored `optionNormalize` and dropped all RNA. It now counts
  the sites at which two sequences differ, leaving out gap, missing and whole-alphabet codes (`n`, `x`), with
  `optionMatchAmbiguityCodes` (default true) and `optionMatchGapToGap` (default false) replacing the
  `AmbiguousOptions` enum. Verified against an independent implementation over 8 option combinations on three
  alignments, 0 mismatches; unchanged on data without codes. Details and numbers in `60_agent_notes.md`.
- 2026-08-17 — `ai/docs` written (this file set), following the structure adopted in `megan8/ai`; `ai/plans`
  established, with the `splitstree.py` and test-suite plans as its entries. The JavaFX boundary was
  **measured**, not assumed: see `60_agent_notes.md`.
- 2026-08 — `splitstree6.io.writers` and `splitstree6.options` exported from the module (`d3773136`); node
  coordinates written into network GML output (`4e793c35`); label-less distance matrices read, networks
  exported as GML (`f9d924e4`).
- 2026-08 — a general `StretchFilter` (Network2Network) for lossy network sparsification (`2cb54eb3`);
  length/distortion reported through network→network filters (`997c103c`).
- 2026-08 — update check uses runtime program identity rather than compile-time constants (`09376161`);
  `Version.WEBSITE_URL` made non-final so a host app can repoint the manual link (`36aa37a4`).
- 2026-08 — the fmmm-layout dependency renamed from `fmm-layout` (`556f5726`), and the native OGDF FM3 layout
  wired in as a `GraphLayoutService` provider behind the `desktop` profile.
- 2026-07 — PhyloFusion tree tracing and branch-length fitting: a faithful port of Banu Cetinkaya's
  metadata-carrying hyper-sequence/SCS code into `splitstree6.compute.phylofusion`, with `optionEdgeWeights`
  as the user-facing option. 118/120 byte-exact against her reference including branch lengths.
