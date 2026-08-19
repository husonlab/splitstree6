# Tasks

Last updated **2026-08-19**. Branch `ai-docs-and-workflow-fixes`.

Jobs whose answer is already known — only the work is outstanding. Anything where we do not know
what the right answer *is* belongs in `questions.md`; anything already investigated is in
`../lab/INDEX.md`.

---

## Standing — every session

**Check that the code still does what these files say it does**, and that they still say what we
mean. A session that touches the workflow machinery or an algorithm ends with either "checked,
consistent" or a new entry in the relevant discrepancy section.

A change is finished when **all** of these say the new truth: the logic file, the class javadoc, the
in-code comments, this file, `../lab/journal.md`, and a row in `../lab/INDEX.md` with a verdict.

---

## In flight

- [ ] **`splitstree.py` step 5 — the generator.** Steps 1–4 are done in `husonlab/splitstree-py`
  (spike, JVM bootstrap, block layer, IO — 83 tests). Step 5 is the big one: one data-driven
  generator producing the whole algorithm surface from the jar scan. Both blocking decisions were
  taken 2026-08-18 — `quiet` defaults **true**, capturing Java's stderr into a buffer
  `java_messages()` returns rather than discarding it (Daniel's reasoning: Java users do not see
  that output either unless they open the message window); and the jars **stay gitignored**,
  synced by `tools/sync_jars.py`. Plan: `../lab/2026-08-17_splitstree-py.md`.

- [ ] **The unit-test suite.** `../lab/2026-08-17_test-suite.md`, still awaiting review. Tiered: pure
  functions by hand, then one data-driven test over all 102 algorithms and 177 options, then
  readers/writers, then the workflow engine (newly possible), then `.stree6` round trips and the
  `examples/publications/` corpus. GUI explicitly out of scope.

## Known defects, fix pending

- [ ] **`ExportManager` leaks the prepend-taxa flag.** `write(…, "NexusWithTaxa", …)` sets
  `optionPrependTaxa` on the **shared** Nexus writer instance and never clears it, so every later
  plain-Nexus write in the same process also prepends taxa. Found 2026-08-18.

- [ ] **D-8: make `ProgramProperties`'s font lazy** (jloda3). Its static initialiser calls
  `javafx.scene.text.Font.font(…)`, so constructing almost any algorithm tries to start a graphics
  pipeline. Caught and harmless, but it prints alarming `UnsatisfiedLinkError` noise when the
  natives do not match, and it is the same shape of bug as D-1 in the same class family.
  `../docs/20_logic.md` §10 D-8.

- [ ] **Let more Java exceptions propagate instead of being printed** (raised 2026-08-18). With
  `quiet` on, anything jloda `Basic.caught`s and prints goes into the capture buffer rather than
  to a user. No worse than the GUI, where it goes to a message window nobody opens — but the right
  fix is for those paths to throw rather than print. Worth a pass when someone is next in that
  code.

- [ ] **Six files in `examples/` cannot be read by the readers that claim them** (171/177 succeed,
  found 2026-08-18). Each needs a decision — fix the reader, fix the file, or accept it:
  `genomes/domestic-mtdna.fasta` is ragged at line 482; three Nexml/BEAST2 XML files give "No
  trees found" (two are BEAST2 *input* XML that the detector over-claims);
  `Gruenstaeudl2019/…/ML-trees-rerooted.tree` is Newick the reader rejects at position 81; and
  `…/nj.workf6` is a SplitsTree5 workflow file claimed as Nexus characters, which then **reads as
  empty rather than failing** — the worst kind. Listed in `tests/test_io.py::KNOWN_UNREADABLE` in
  splitstree-py.

## Infrastructure

- [ ] **Nothing checks that a commit compiles.** The only Actions workflow here, `deploy-docs.yml`,
  rebuilds the Pages site when `docs/` changes; the installer workflows are `workflow_dispatch`.
  A workflow triggered `on: push` doing no more than `mvn -o package` would catch a commit that
  does not compile — the cheapest possible check, and the one this repository lacks. Do it with
  step 8 of the test plan.

- [ ] **Stored baselines.** No recorded reference output for any dataset, so "did this change
  anything?" can only be answered within a single session. `examples/publications/` — datasets
  paired with the `.stree6` that reproduces a published figure — is the natural corpus; T5 of the
  test plan covers it.

- [ ] **Run the portability probe** once the jloda3 headless-workflow commit is pushed. See
  `questions.md` Q3 — it decides whether `splitstree.py` ships one wheel or five.

- [ ] **`RunPhyloFusion.java` is untracked.** It compiles, it works, and both `../docs/20_logic.md`
  and `../docs/40_testing.md` cite it as the worked example of headless algorithm use. Either
  commit it or stop citing it.

- [ ] **Write the first `2x_logic_*.md` files.** There are none yet; `../docs/20_logic.md` covers the
  workflow machinery and deliberately not the individual algorithms. Write them one subsystem at a
  time as work touches them — the distance methods are the obvious first, given how much of
  2026-08-19 was spent there.

---

## Done

Completed work is not listed here. It is in `../lab/INDEX.md`, one row per question with its verdict
and its numbers, and in `../lab/journal.md` in full.
