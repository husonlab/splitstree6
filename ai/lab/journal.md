# Journal

You don't need to read this before starting work. It is the chronological archive of past work and findings —
newest entries first.

Since 2026-08-19 the primary record of *investigations* is `INDEX.md` plus the per-question entries beside
this file. This journal is for what does not fit a single question: dead ends, surprises, decisions and why
they went the way they did, and everything from before the ledger existed.

---

## 2026-08-19 — `ai/` restructured into three layers by epistemic status

Same restructure as `megan8/ai`, and for the same reason: `docs/` + `plans/` came from software engineering,
where the plan precedes the work. Here the work is exploratory, and the most consequential fact in this
codebase — that the computation is plain Java and only the views are JavaFX — was established by a **probe**
that contradicted what the imports said. A structure whose primary artefact is a plan cannot record that.

The new shape sorts documentation by how sure we are of it: `docs/` (what we believe true, unchanged except
that `10_context.md` was rewritten around the new working rules and file tables), `open/` (what we do not
know — `50_todo.md` split into `../open/questions.md`, which holds open questions with **gates** rather than owners,
and `../open/todo.md`, which holds jobs whose answer is already known), and `lab/` (what was tried — `plans/` renamed,
`60_agent_notes.md` became `journal.md`, plus `TEMPLATE.md`, the ledger `INDEX.md`, and `measurements.md`).

The old "concerns worth keeping in view" list became "read before designing any experiment" at the top of
`../open/questions.md`, since that is the moment it matters.

`INDEX.md` was back-filled with 14 rows from this journal and the plan files. Putting the numbers in one place
made two things visible that were scattered before: that **171 of 177** example files read, and that **any
bootstrap baseline predating `b995795c` is worthless** — the latter now carries a warning at the top of
`measurements.md`, where a baseline would otherwise be quoted in good faith.

Also added: a root `CLAUDE.md` (there was none, so nothing pointed Claude Code at this directory) and
`/probe`, `/wrap`, `/tried` in `.claude/commands/`.

## 2026-08-18/19 — splitstree.py built to step 5, and five defects it found

Daniel asked for the Python package. Steps 1-5 of `ai/lab/2026-08-17_splitstree-py.md` are done and live in
`husonlab/splitstree-py`: the JPype spike, the JVM bootstrap, the block layer, IO, and the generated algorithm
surface. 412 tests, about 9 seconds. How to build and run it is in `../docs/30_tools.md`; what follows is what the work
found out about SplitsTree, which is the part worth keeping.

**Running the whole catalogue from outside the application is a good bug detector.** Nothing had ever
constructed every algorithm and fed each one a canonical input. Doing so found, in one afternoon, the five
things below - none of which is a Python problem, and all of which affect the Java side.

- **D-9. The report algorithms could not run outside the GUI.** All four report bases called
  `Platform.runLater` unconditionally and dereferenced `getViewTab()`. Fixed; the GUI path is byte-identical,
  because `setText` mutates an `ObservableList` and stays on the FX thread whenever a view exists.
- **D-10. `ProgressMover` kept the JVM alive for ever.** `while (true) { try { sleep } catch (Exception
  ignored) {} }` on a non-daemon thread swallowed the interrupt from `shutdownNow()`. Running the Autumn
  algorithm meant the process never exited: over seven minutes before I killed it, 0.46 s after.
- **D-11. Every parallel algorithm cost 60 seconds at exit** (jloda3). `ProgramExecutorService` handed out a
  cached pool with non-daemon workers, which linger 60 s when idle. Measured 60.4 s for a one-second consensus
  network; 0.38 s after. **What hid both of these: the GUI and every command-line tool call `System.exit`.**
- **`ExportManager` writes Nexus that its own reader rejects**, and leaks the prepend-taxa flag between writes.
  Both in `../open/todo.md`.
- **The bootstrap support values were not reproducible**, even with `optionRandomSeed` set - see below.

**Two jars were missing from what I had believed was the minimal set**, and both were found by running rather
than reading: `javafx-controls`, because `NotificationManager` extends a JavaFX control and readers call it on
their *warning* paths, so `NexmlReader` died with `NoClassDefFoundError`; and `commons-collections4`, for
Autumn's `LRUMap`. Nine jars, 16 MB. The lesson generalises: the minimum dependency set cannot be derived from
the imports of the happy path.

**The bootstrap reproducibility bug is the one to remember.** `BootstrapSplits` aggregated into two shared maps
with `map.put(k, map.getOrDefault(k, 0) + 1)` - a read-modify-write, not atomic - so increments were lost
whenever two workers hit the same split. The same build, the same seed and the same data gave one split 90, 95
and 100 percent support across three runs. Now `merge`, and five consecutive runs are byte-identical.

I found it only because I needed a stable baseline to check a refactor against. My first before/after diff
showed differences, and the honest next step was to ask whether the *unchanged* build was self-consistent. It
was not. **When a before/after comparison differs, check that "before" is deterministic before concluding
anything about the change** - that habit is the whole reason this was found, and it applies to every numeric
change in this codebase.

**The pattern behind five of these fixes.** `getNode()` is null for a data block that was never put in a
workflow, so any algorithm reaching back through the workflow for context throws before computing anything.
Daniel's construct, now applied five times: the old method does the workflow archaeology and delegates to a new
one that takes the context explicitly. `MedianJoining` needed no new method at all - its base already had the
characters and was asking the workflow to re-derive them. `MinSpanningNetwork` / `MinSpanningTree` /
`ExternalDistance2Network` got `IUsesCharacters`. `RazorHaplotypeNetwork`, in the bridge repo, wanted the
`AlignmentView`'s active sites rather than characters, so it takes a `BitSet`. `BootstrapSplits` wanted the
alignment *and* the pipeline to replay, so it takes a `PathSupplier`. Four different kinds of extra context
across five algorithms, which suggests the general statement is "an algorithm may need context beyond its
declared input", not anything about characters specifically.

**Still open**, all in `../open/todo.md`: `BootstrapTreeSplits` and `BootstrapTree` (plan:
`ai/lab/2026-08-19_bootstrapping.md`); D-8, the eager `Font.font()` in `ProgramProperties`; D-12, something
in the catalogue leaves `System.err` replaced by a discarding stream and **I did not isolate it** - no single
algorithm reproduces it and every hide/restore site looks correct, so the Python side re-installs its capture
stream instead. That one is an unfinished diagnosis, not a closed question.

**One thing I would tell the next session before it starts.** The docs in this directory were written on
2026-08-17 from the code, and several of their central claims have since been *narrowed by measurement*: the
JavaFX boundary moved from "the workflow needs a toolkit" to "only the views do", and the claim that the
JavaFX natives are never loaded turned out to be false (they are loaded, they just fail harmlessly). Both were
corrected in place. Treat a confident statement in `../docs/20_logic.md` as a hypothesis with a date on it, and re-run
the probe rather than citing it.

---

## 2026-08-19 (night) — protein ambiguity treated as missing, and the RNA code list completed

The two items the previous entry left open, both on Daniel's instruction.

**Protein.** `CharactersType.Protein` is `arndcqeghilkmfpstwyvbzx*`, so `getNumStates()` said 24 where there
are 20 residues: `b` is D or N, `z` is E or Q, `x` is any residue, and `*` is a stop codon that
`ProteinMLDistance` already says in a comment it wants to ignore. They are now listed in
`getNonStateSymbols()`, and — the part that differs from the nucleotide fix — `PairwiseCompare` maps a legal
non-state character onto the **missing** index rather than expanding it, because there is no machinery for
expanding a protein ambiguity. The gap test still comes first, which matters: `ungulates.nex` declares
`gap=x`, and there `x` must stay a gap rather than becoming missing by the generic rule.

Measured on a synthetic pair, `numStates` 24 → 20, and the sites carrying `b`, `z` and `*` drop out of the
comparison: `compared` 6 → 3. On a clean protein pair with none of them, `ProteinMLDistance` returns
**0.2335 before and after** — as expected, since `mlDistance` was already surviving by copying the top-left
20×20 sub-matrix and rescaling, the twenty residues happening to come first in the symbol string. That was
luck rather than design, and it is no longer load-bearing.

**LogDet on protein was broken in exactly the way it was broken on nucleotides**, which the previous entry did
not notice because it had no protein data in the regression. At HEAD it returned the replaced value 1.0 for
every pair of both protein examples. Now, on `buttercups-cytochromeC.fasta` (100 taxa, 364 sites): mean
0.13426012, max 0.24178202, and **1 pair of 4950 at the maximum**, so the matrix is genuinely defined
throughout.

**Where it is still undefined, and why that is not a bug.** On `ungulates.nex`, 2899 of 2926 pairs (99.1 %)
are still the replaced value. That is the data, not the code: the alignment is extremely skewed — 51 284 `g`
and 32 716 `p` against 5 occurrences of `l` in the whole matrix — so most pairs never observe all twenty
residues, `F` is singular for an honest reason, and the saturation test fires. LogDet with twenty states needs
twenty observed states. The difference from before is that the singularity used to be **structural**, four
rows that could never be filled whatever the data, and is now a property of the alignment. Worth stating
plainly rather than claiming LogDet on protein now works.

**RNA.** `RNAwithAmbiguityCodes` listed `acgury` — the bases and two of the eleven codes. So an RNA alignment
using `w` or `n` was not recognised at all: `guessType` returned `Unknown` for `acguw`, `acgun` and `acguwn`,
and the alignment then failed with `SplitsException: invalid character 't'`, naming a character not present in
the data. The list is now `acguryswkmbdhvn`, matching what `DNAwithAmbiguityCodes` already carried. All three
alphabets now come back as `RNAwithAmbiguityCodes`, and such an alignment runs end to end — `numStates` 4,
LogDet 0.2094 on the probe pair. DNA typing is untouched, and there is no risk of capturing protein, which
always contains residues outside this set.

**Verification.** The nucleotide and binary regression is unchanged from the run before this change, and the
protein regression differs from HEAD in the two LogDet lines only. All the standing checks still pass: 23
Hamming cases, 0 mismatches over 8 option combinations, the `numNotMissing == Fsum` invariant at 0 violations,
the RNA and NaN/hang probes clean, and all four nucleotide types reporting 4 states. `src/main/java` compiles.

**What is left.** Nothing known in this area. The one thing not exercised is a protein alignment that actually
contains `b`, `z` or `*` — none of the three examples in the repository has any, so that path is covered by a
synthetic pair only.

---

## 2026-08-19 (evening) — ambiguity codes were being counted as states

Daniel's call: sequence handling has never been rigorously reviewed, so flush the inconsistencies out. This is
the root cause behind the LogDet finding recorded below, and it reached further than that finding suggested.

**The confusion.** `CharactersBlock.getSymbols()` was doing two different jobs. It is the list of characters
that may legally appear — which the readers validate against and which nexus writes as `SYMBOLS=` — and it was
*also* being read as the list of states. For the nucleotide types those are not the same list, because an
ambiguity code is a legal character but is not a state: it stands for a set of bases and is expanded into them
before it reaches any frequency matrix. Six places meant "states" and asked for the wrong one.

**Which files were affected, and why it was easy to miss.** A nexus file that declares `symbols="acgt"` in its
FORMAT line comes out with symbols and states identical, so `primates.nex` and `100taxaExample.nex` behave the
same before and after. The phylip and FASTA readers instead set the symbols from the alphabet they observe, so
`nucleic_M2573_346x897_2006.phy` arrives with `symbols='acghkmrstwy'` — the codes included. Everything below
is invisible on the nexus examples and wrong on the phylip ones.

**What it broke.**

- **LogDet was undefined for every pair** on such data. `r = getNumStates()` was 11 here (15 for the enum's own
  `DNAwithAmbiguityCodes` symbol list), so `F` was an 11×11 matrix in which seven rows can never be filled;
  every zero eigenvalue tripped the saturation test. Measured on the 346-taxon file: **mean 1.0, the replaced
  value, for all 59 685 pairs → now mean 0.11100813, max 0.41899542.** `r` also appears in the distance
  formula itself, `-(1-PiSum)/(r-1) * (x - logPi)`, so it had to be the true state count for the number to
  mean anything.
- **Base frequencies were nonsense.** `NucleotideModel.computeFreqs` returned an array as long as the symbol
  list, and a model reading π[0..3] got the frequencies of `a`, `c`, `g` and **`h`** — a code occurring once
  in the whole file. **π_T came out as 0.0000** because `t` was sitting at index 8. Length 11 rather than 4
  also means `optionBaseFrequencies` could not survive a `.stree6` round trip, `OptionIO` reading a
  `doubleArray` back at the length it already has.

**Two more defects in the same method**, found while fixing it and worth stating separately because they hit
every dataset, not just the ones with codes:

- The loops read `for (i = 1; i < getNtax(); i++)` over a **0-based** matrix and `for (k = 1; k < getNchar())`.
  So the first taxon, the first site and the last site were left out of **every base frequency this program
  has ever reported**. On `primates.nex`: `[0.3230, 0.3066, 0.1059, 0.2645]` → `[0.3255, 0.3033, 0.1052,
  0.2660]`.
- `numNotMissing` counted every non-gap non-missing character but only recognised states landed in a bin, so
  ambiguous characters quietly scaled all four frequencies down. Same shape as the `PairwiseCompare` defect in
  the entry below. The denominator now counts exactly what the numerator counts, and a sequence with nothing
  observed returns a flat distribution instead of dividing by zero.

**The fix.** `CharactersType.getNonStateSymbols()` names the symbols that are not states — the ambiguity codes,
for the four nucleotide types — and `CharactersBlock.getStateSymbols()` returns the symbols with those removed,
so a `SYMBOLS=` override from a file is still respected. Six consumers now ask for it: `PairwiseCompare`
(`numStates` and the state lookup, which is what fixes LogDet and every model-based distance),
`NucleotideModel.computeFreqs`, `BaseFreqDistance`, `PhiTest`, and `LogDet.isApplicable`. `getSymbols()` itself
is untouched, so parsing, validation, colouring and nexus output all behave exactly as before — verified by a
round trip, which still writes `symbols="acghkmrstwy"` for the phylip file.

`PhiTest` changes behaviour on data with codes, deliberately: a code is no longer a state of its own, so it
falls through to the test's `missing` value. Treating an ambiguity as a distinct fifth state made sites look
more polymorphic than they are, and treating it as missing is what PhiPack does. `BaseFreqDistance` likewise
no longer gives each code its own bin in a base-frequency vector.

**Verification.** Regression over `primates.nex`, `dolphins_binary.nex` and the 346-taxon phyml alignment,
seven algorithms each: exactly one number moved, LogDet on the phylip file, from broken to working. Standard
(binary) data is untouched, `primates.nex` is untouched, and all the earlier checks still pass — 23 Hamming
cases, 0 mismatches against the independent implementation over 8 option combinations, the `numNotMissing ==
Fsum` invariant at 0 violations, RNA and the NaN/hang cases all clean. `src/main/java` compiles.

**Deliberately not changed.** Protein's `b`, `z` and `x` are ambiguity codes too and `*` is a stop codon, so
`Protein("arndcqeghilkmfpstwyvbzx*")` has the same defect: 24 states where there are 20. It is not the same
easy fix, because `PairwiseCompare` has no way to expand a protein ambiguity, so dropping those four from the
state list would make it throw on protein alignments that work today — `mlDistance` currently survives by
taking the top-left 20×20 sub-matrix, the 20 amino acids happening to come first. Left alone and written into
`../open/todo.md`. Also still open: `RNAwithAmbiguityCodes`'s enum symbol list is `acgury`, only two of the eleven
codes, so RNA data using `w` or `n` may not be typed at all by `guessType`.

---

## 2026-08-19 (later) — PairwiseCompare: a hang, a miscount, and RNA that never worked

Follow-up to the entry below, at Daniel's request. Three divisions by zero in `PairwiseCompare`, one in
`GeneSharingDistance`, a safety net in `FixUndefinedDistances`, `getNumNotMissing()` redefined to agree with
`getF()`, and RNA-with-ambiguity-codes made to work at all.

**Why it mattered more than it looked.** The write-up below called this "a NaN passes into the matrix". Running
it says worse. Building a pair whose comparable sites all carry character weight 0 — so `fCount` stays empty
while `numNotMissing` is positive — and feeding it to each of the seven algorithms that still use
`PairwiseCompare`:

- `NeiLiRestrictionDistance` and `UpholtRestrictionDistance` put a **NaN** straight into the distance matrix;
- `LogDet` **hangs**. `Matrix.eig` on a NaN matrix never converges in `jama.EigenvalueDecomposition.hqr2`,
  which has no iteration cap. 24 s of CPU and still spinning when the thread dump was taken. Inside an
  `AService` that is a frozen computation, not a wrong answer;
- `JaccardDistance`, `DiceDistance` and `GeneSharingDistance` happened to escape, because `NaN > 0.0` is false
  and their guards therefore fell through to the `-1` path. Luck, not design.

**And it fires on shipped data.** Running the seven algorithms over `primates.nex`, `dolphins_binary.nex` and
the 346-taxon phyml alignment, exactly one number moved: **Gene Sharing Distance on
`examples/programs/splitstree4/dolphins_binary.nex` had 35 non-finite entries**, now 0. That NaN is not from
`PairwiseCompare` at all — it is `GeneSharingDistance`'s own, and it was found only because the safety net
caught it.

**The four fixes.**

- `PairwiseCompare.getF()` returns null (the class's existing "undefined" signal) when `Fsum <= 0`, instead of
  filling the matrix with `0.0/0.0`. Testing `numNotMissing > 0` was never sufficient: that counts a site
  whenever neither character is gap or missing, while `fCount` only receives sites that actually contributed —
  not sites skipped as ambiguous, and not sites of weight 0. All six `getF()` callers already null-check and
  fall back to `-1`, so this widens an existing path rather than adding one.
- `PairwiseCompare.mlDistance()` returns `-1` when the copied sub-matrix sums to 0, which the rescale
  `F[i][j] /= k` would otherwise turn entirely into NaN. Both callers already initialise `dist = -1.0` and
  only overwrite on success, matching the `if (fullF == null) return -1;` already above it.
- `PairwiseCompare.bulmerVariance()` guards `getNumNotMissing() == 0`. Currently uncalled, but it was a
  division by zero waiting for its first caller.
- `GeneSharingDistance` guards the divisor rather than `2a+b+c`. The two are not the same: `2a+b+c` is positive
  whenever either taxon has a gene, but `min(a+b, a+c)` is 0 as soon as `a` is 0 and one of `b`, `c` is 0.

**And the net.** `FixUndefinedDistances` tested `== -1` only, so any non-finite value from anywhere went
through untouched — and worse, a single NaN destroyed the replacement value it computes, `Math.max(x, NaN)`
being NaN, so `Math.log10` of it made `largeValue` NaN as well and *every* undefined entry in that matrix came
out NaN. It now treats any non-finite entry as undefined via a small `isUndefined` helper.

**Verification.** With the fixes, all seven algorithms return finite values on the weight-0 input, `LogDet`
completes instead of hanging, and an NaN injected directly into a matrix is replaced (3.0) while the finite
entries are left alone (2.0). On real data the regression over three alignments is byte-identical to before
apart from the 35 Gene Sharing entries. Fixing `GeneSharingDistance` at source produced numbers identical to
letting the net catch it, which is the expected result and the reason both were done. The whole of
`src/main/java` compiles, and the Hamming checks from the entry below still pass unchanged: 23 hand-computed
cases, and 0 mismatches against the independent implementation over all 8 option combinations.

**`getNumNotMissing()` fixed too**, on Daniel's instruction, in the same pass. It now counts exactly the sites
that reach the states-by-states block of `fCount` — the only part `getF()` sums over — instead of every site
whose two characters happened to be neither gap nor missing. The increment moved from the top of the site loop
to the places where the mass is actually added, guarded by `stateX < numStates && stateY < numStates` so that
a site whose weight lands in the gap or missing row is not counted. `getF()` correspondingly dropped its
`numNotMissing > 0` gate and now guards on `Fsum` alone, so it depends only on `fCount`.

The invariant this establishes is worth stating, because it is what "matches `getF()`" means and it is
directly checkable: **with unit character weights, every compared site adds exactly 1 to the states block, so
`getNumNotMissing()` equals that block's sum.** Measured over the first 60 taxa of the 346-taxon phyml
alignment, 1770 pairs, with `isIgnoreAmbiguous` on:

| | numNotMissing != Fsum | `round(p * numNotMissing)` wrong |
|---|---|---|
| before | 1098 of 1770 pairs, worst case off by 43 sites | 402 of 1770 |
| after | 0 | 0 |

That second column is the old `HammingDistance` formula, checked against an independent count of differing
sites: it was giving the wrong answer for 23 % of pairs, and now gives the right one for all of them. With
`isIgnoreAmbiguous` off — which is what all eight remaining callers use — the invariant already held, and on
`primates.nex` (no ambiguity codes) both versions were already exact. That is why the change moves no current
algorithm's numbers: the regression over `primates.nex`, `dolphins_binary.nex` and the phyml alignment is
byte-identical to the run before it. `getNumNotMissing()` has no callers outside this class.

**RNA with ambiguity codes now works at all**, fixed in the same pass. It did not before: `AmbiguityCodes`
expands the codes to DNA bases, so in RNA data `y` became `"ct"`, and `states.indexOf('t')` is -1 against RNA's
`acgu`. The `si.equals(sj)` branch then indexed `fCount[-1][-1]` — an `ArrayIndexOutOfBoundsException` — and
the other branch threw `SplitsException: invalid character 't'`, naming a character that does not occur
anywhere in the data. Every RNA sequence carrying a code was unusable by any algorithm built on this class.

The fold now lives in one place, a new `AmbiguityCodes.getNucleotides(char code, String alphabet)` that maps
the expansion's `t` onto `u` when the alphabet has `u` and no `t`; `PairwiseCompare` passes the block's own
symbols. The `si.equals(sj)` branch also got the `statei >= 0` check its two neighbours already had, so a code
genuinely outside the alphabet now throws a `SplitsException` naming the code, the base it expanded to and the
alphabet, instead of an index error. `HammingDistance` keeps its own mask table, which folds `u` onto `t`
because its bitmask is indexed over `acgt`; same fact, opposite direction, and it is separately verified.

The check that this is right: RNA `y/c` now returns exactly the numbers the equivalent DNA `y/c` returns
(`1 - sum F[x][x] = 0.1000`, LogDet `0.0849`), which is what must happen when `u` and `t` are the same base.
DNA is untouched, and a code against an alphabet that really lacks it (`y` against `Standard`'s `01`) still
throws, as it should.

**A separate defect this uncovered, not fixed — needs Daniel.** `CharactersType.DNAwithAmbiguityCodes` has
symbols `acgtryswkmbdhvn`: the four bases **and all eleven codes**. `numStates` is therefore 15, so `fCount`
carries 15 states while `PairwiseCompare` expands every code into bases — the eleven code rows and columns can
never receive any mass. `F` is singular by construction, and every algorithm that reads `getNumStates()` gets
a matrix that cannot be inverted or log-determined. Measured: **`LogDet` on the 346-taxon
`nucleic_M2573_346x897_2006.phy`, which is typed `DNAwithAmbiguityCodes`, returns the replaced value 1.0 for
every one of its 59 685 pairs** — the whole matrix is undefined. On `primates.nex`, typed plain `DNA` with
`numStates` 4, the same algorithm gives a mean of 0.2996 and works. `RNAwithAmbiguityCodes` (`acgury`, 6
states, 2 of them dead) has the same shape of problem. This is pre-existing and unchanged by anything here —
both the before and after regression runs show 1.0 — but it means LogDet and the nucleotide models are
effectively broken on any alignment the reader typed as carrying ambiguity codes. The fix is not local: it
means deciding whether `getSymbols()` for these types should report only the bases, which touches parsing,
`guessType`, the state labelers and the colouring. See `../open/todo.md`.

**Still not fixed.** `PairwiseCompare` throws `ArrayIndexOutOfBoundsException` rather than a clean message when
an algorithm meets a data type it does not accept (`ProteinMLDistance` on DNA); the GUI gates that through
`isApplicable`, so it is only reachable from code.

---

## 2026-08-19 — HammingDistance rewritten: what it counts, and what it leaves out

`HammingDistance` did not compute a Hamming distance. Rewritten from scratch against Daniel's specification;
`PDistance` (the same class with `optionNormalize` on) follows it.

**What was wrong.** The unnormalized branch computed `Math.round(p * seqPair.getNumNotMissing())`, where `p`
came out of `PairwiseCompare.getF()`. Those two quantities have different denominators: `getF()` normalises
over the sites that landed in the states-by-states block of `fCount`, while `numNotMissing` counts every site
where neither character is the gap or the missing character — **including the sites that were skipped as
ambiguous**. Wherever ambiguity codes occur, the count is inflated by exactly the ratio between the two. On
the 87-taxon rhabdovirus alignment (`razornet/examples/rhabdoviruses/DImmSV_ADAR.fasta`, 40 % `n`) the mean
pairwise count fell from **2.444 to 1.808** and the maximum from **12 to 9**: the old numbers were 35 % too
high. The normalized values on that file are unchanged to six decimals, which is the tell — the proportion was
right all along, only the rescaling into a count was wrong.

The `MatchStates` branch was a second algorithm entirely. It scored Y against C as `1 - 2/3 = 0.333` rather
than 0 (a partial-overlap cost, not "compatible states do not differ"), it ignored `optionNormalize`
completely, and its `ALLSTATES = "acgt" + AmbiguityCodes.CODES` silently dropped every RNA site, `u` not being
in it.

**What it does now.** A site is compared only where both sequences have an observed character. Gap, missing,
and a code covering the whole alphabet (`n` for nucleotides, `x` for protein) all count as unobserved and stay
out of both numerator and denominator, so each pair is scored on its own overlap. Unnormalized is that count
of differing sites; normalized divides by the number of sites compared.

Three decisions Daniel took on 2026-08-19:

- **`n` is missing, not an ambiguity code.** It stands for all four bases, so with matching on it can never
  produce a difference but would still enlarge the denominator — a sequence with a long unsequenced run would
  come out *closer* to everything. Partial codes (`y`, `r`, …) stay real characters.
- **No overlap stays `-1`**, and goes to `FixUndefinedDistances`, which warns and substitutes a large value.
  The obvious divide-by-one guard yields 0, which reports two sequences sharing no observed site as identical.
- **gap-to-gap is off by default**, behind `optionMatchGapToGap`. On, a site where both sequences are gapped
  counts as a match — a shared deletion is shared information — and so enters the denominator; off, it is
  skipped. A gap against a base is ignored either way.

**Options.** The `AmbiguousOptions` enum (`Ignore` / `AverageStates` / `MatchStates`) is gone, replaced by two
booleans: `optionMatchAmbiguityCodes` (default **true**) and `optionMatchGapToGap` (default false). The first
defaults to true because that is what preserves the old default's *behaviour*: under `Ignore` an ambiguous
site contributed no difference, and matching keeps it that way, where false would silently start counting Y
against C as a difference in every existing dataset. This is the one default choice Daniel has not explicitly
confirmed — see `../open/todo.md`. Old `.stree6` files carrying `HandleAmbiguousStates` load with the usual
skipped-unknown-option warning; there is one in the tree, `razornet/examples/other/dusky_dolphins.stree6`.

Ambiguity matching uses a 128-entry bitmask table over `acgt`, built once per `compute` from
`AmbiguityCodes.getNucleotides`, rather than a per-site `AmbiguityCodes.codesOverlap` call, which allocates two
strings each time and would be called ntax²/2 × nchar times. RNA's `u` is folded onto `t` when the table is
built, `AmbiguityCodes` being written in terms of DNA; without the fold, `y` against `u` is a difference.

**Verification.** 23 hand-computed cases (gaps, missing, `n`, `x`, the ambiguity combinations, RNA, character
weights, both flags, no-overlap) all pass. The whole matrix was then recomputed by an independent naive
implementation calling `codesOverlap` directly, for all 8 option combinations on three files — 346×897 with
codes and gaps, 87×1281 at 40 % `n`, primates 12×898: **0 mismatches, max delta 0**. The mask table agrees
with `codesOverlap` on all 625 letter pairs that reach it. As the "nothing else moved" check,
`primates.nex`, `dusky_dolphins.nex` and the 256-taxon `streptococcus-agalactiae.nex` (1125 missing
characters, no ambiguity codes) come out identical to the old code to six decimals, the only difference being
that the old code returned `-0.000000` where the new one returns exact 0. All 733 files of `src/main/java`
compile. The two large files also ran faster: 1.75 s against 3.47 s wall, JVM start and parsing included.

**Not checked:** nothing was run through the GUI or a workflow round-trip, and the option rename was reasoned
about from `OptionIO.parseOptions` rather than exercised by actually loading an old `.stree6` file.

---

## 2026-08-17 (evening) — the splitstree.py spike: JPype works, and the numbers are good

Step 1 of `ai/lab/2026-08-17_splitstree-py.md`, the time-boxed spike. Full table in that plan's §3.1;
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
give coordinates without a toolkit? **Yes, all of them.** Recorded in `../docs/20_logic.md` §6.2a.

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

Daniel answered all seven discrepancies inline in `../docs/20_logic.md`. Six led to code changes, one to a plan.
The resolutions are in `../docs/20_logic.md` §10; what follows is what was measured and what was learned.

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
in `../open/todo.md` rather than chased, because it is a GUI listener misbehaving in a tool and is unrelated to
anything changed today.

**D-7 produced `ai/lab/2026-08-17_test-suite.md`.** Its two load-bearing observations: D-1 makes the
workflow engine unit-testable (headless, synchronous, no toolkit, ~40 ms), which it was not this morning; and
the catalogue scan means one data-driven test can exercise all 102 algorithms and all 177 options instead of
102 hand-written classes. No test code written — the plan is a proposal.

---

## 2026-08-17 — `ai/docs` written; the JavaFX boundary measured

Daniel asked for a documentation set mirroring the one adopted in `megan8/ai`, plus an `ai/lab` directory
with a planning document for a Python package. Six files in `ai/docs` and one plan were written from the code.

**What was measured rather than assumed.** The single most consequential fact about this codebase is where its
JavaFX dependency actually begins, because it decides what can be reused headless — by a server, by an
automated build, by a language binding. It is stated in `../docs/20_logic.md` §6 as a table of probe results rather than
as an opinion,
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
now `../docs/20_logic.md` D-1 and `../open/todo.md`'s highest-value open question, because fixing it in jloda3 would
make
the entire workflow engine headless — with knock-on benefits for `RunWorkflow` (which only extends
`Application` to get a toolkit), for servers, and for the Python binding.

**An end-to-end `RunWorkflow` run was also recorded**, on the module path, as the verification recipe in
`../docs/40_testing.md`: `10taxaExample.stree6` applied to `nucleic_M2573_346x897_2006.phy` → workflow with 8 data
nodes and 5 algorithms, 346 input taxa, neighbor-net on **256** taxa in 0.3 s, 2.2 s total, `SPLITS` block with
`ntax=256 nsplits=517 fit=99.9`.

That 346 → 256 is the useful part, and it was not expected. The cause: the saved workflow's `TaxaFilter`
carries `DisabledTaxa = 'tax100' 'tax11' … 'tax99'`, and the 346-taxon file happens to use the same `taxN`
naming, so **90 taxa were matched by name and silently dropped**. Confirmed by counting: exactly 90 taxon names
in the phylip file match `tax11`…`tax100`. A saved taxa filter is applied by name to whatever dataset you point
the workflow at, which is reasonable behaviour and a very good way to verify the wrong thing. Recorded in both
`../docs/30_tools.md` and `../docs/40_testing.md`. The same run also showed that unknown options in an old workflow file
are
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
`nucleotide` subpackage). The full signature table is `../docs/20_logic.md` §4.

The unexpected number is **38 algorithms returning no citation**. `getCitation()` feeds `ExtractCitations` and
therefore the methods text, so each is a silent hole in every analysis that uses that algorithm. At most 16 of
the 102 (filters, loaders, view producers) are legitimately uncitable, so roughly 22 real algorithms are simply
missing theirs. Recorded in `../open/todo.md` P3.

**Build facts checked, not copied from megan8.** `mvn -o clean compile -q` 32 s; incremental `mvn -o package -q`
8 s, and `copy-dependencies` is bound to `package`, so there is no separate invocation (`mvn clean` removes
`target/dependency`, which then has to be rebuilt before anything uses the module path). The module path
resolves as it stands — `java --module-path target/SplitsTree-1.0.0-SNAPSHOT.jar:target/dependency
--add-modules splitstreesix …` starts the tools — even though both classified and unclassified JavaFX jars are
copied; megan8 needed explicit exclusions for the same duplication, this project does not, today. The JavaFX
native classifier follows the JVM Maven itself runs on: here an x86_64 GraalVM 17 on an arm64 Mac, giving
`-mac` (Intel) jars, which is why the default `java` works while `splitstree-env` would refuse it.

**Six discrepancies recorded** in `../docs/20_logic.md` §10 and listed in `../open/todo.md`. None has been put to
Daniel.
Four are one-line observations (the title-string node lookup; `updateTitle` using the algorithm map for data
nodes; the source node whose purpose the code itself queries in a `// todo:`; the silent empty-input path); one
is the view-completion poll; one is D-1 above.

**What was deliberately not written.** No `2x_logic_*.md` files. Daniel asked for the overview only — "just
give an overview of the main workflow design, without detailing all the algorithms" — and writing algorithm
descriptions from the code without his review would produce exactly the kind of document that
`../docs/10_context.md` rule 2 exists to prevent. `../docs/20_logic.md` §4 lists what exists and with which signature,
and the
file table in `../docs/10_context.md` says the `2x_` files are to be added one subsystem at a time.

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
— never a `git mv` or an in-place refactor. This is a standing instruction from Daniel, now `../docs/10_context.md`
rule 9.
