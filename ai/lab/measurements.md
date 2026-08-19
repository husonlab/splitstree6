# Measurements

Every number worth comparing to a later number. Append only; never edit a recorded value — record a
new row and let the two stand side by side.

**Record the instrument, not just the value.** A result without its dataset and its option settings
is not comparable to anything and is worse than no row. This project has no unit tests and no stored
baselines, so these numbers are, for now, the only memory of what the program used to produce.

> ⚠️ **Any bootstrap number recorded before `b995795c` (2026-08-19) is worthless.** A non-atomic
> aggregation made support values irreproducible even with `optionRandomSeed` set.

## Datasets in use

| name                           | file               | note                                                                                                                                                 |
|--------------------------------|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| phyml alignment                | 346 taxa           | 59 685 pairs; the workhorse for distance-method checks                                                                                               |
| `ungulates.nex`                | 156 156 characters | declares `gap=x`, so `x` is a gap here and not missing. 5 characters are `l`, which is why LogDet is 99.1 % undefined on it — the data, not a defect |
| `buttercups-cytochromeC.fasta` | protein            | 4950 pairs                                                                                                                                           |
| `dolphins_binary.nex`          | binary             | where the 35 non-finite Gene Sharing entries showed up                                                                                               |
| `examples/publications/`       | corpus             | datasets paired with the `.stree6` that reproduces a published figure. The natural baseline corpus; **not yet used as one**                          |

## Coverage

| date       | what                                                  | value                 | conditions                                                                                                   |
|------------|-------------------------------------------------------|-----------------------|--------------------------------------------------------------------------------------------------------------|
| 2026-08-18 | `examples/` files read by the reader that claims them | **171 / 177**         | read from Python; the 6 failures are listed in `../open/todo.md`                                             |
| 2026-08-17 | concrete algorithms in the catalogue                  | **102** (177 options) |                                                                                                              |
| 2026-08-17 | algorithms returning no `getCitation()`               | **38**                | ~16 are legitimately uncitable (filters, loaders, view producers), so ~22 are real holes in the methods text |
| 2026-08-17 | readers / writers                                     | **31 / 44**           | no test that any pair round-trips                                                                            |
| 2026-08-18 | tests in `husonlab/splitstree-py`                     | **83**                | steps 1–4                                                                                                    |
| —          | tests in this repository                              | **0**                 |                                                                                                              |

## Correctness — before and after

| date       | change                                                | metric                                                                            | before                                 | after                                                                     |
|------------|-------------------------------------------------------|-----------------------------------------------------------------------------------|----------------------------------------|---------------------------------------------------------------------------|
| 2026-08-19 | ambiguity codes no longer counted as states           | LogDet on phyml, pairs defined                                                    | **0 of 59 685**                        | all                                                                       |
| 2026-08-19 | "                                                     | LogDet mean distance                                                              | undefined                              | **0.111**                                                                 |
| 2026-08-19 | "                                                     | base-frequency vector length                                                      | 11, π_T = 0                            | **4, correct**                                                            |
| 2026-08-19 | protein ambiguity treated as missing                  | protein states                                                                    | 24                                     | **20**                                                                    |
| 2026-08-19 | "                                                     | LogDet on `buttercups-cytochromeC.fasta`                                          | undefined for every pair               | mean **0.134**, 1 of 4950 pairs at the replacement value                  |
| 2026-08-19 | "                                                     | LogDet on `ungulates.nex`                                                         | 99.1 % undefined                       | **99.1 % undefined** — unchanged, and correct: it is the alignment's skew |
| 2026-08-19 | `getNumNotMissing()` redefined to agree with `getF()` | pairs where the two disagreed (phyml)                                             | **1098 of 1770** (worst case 43 sites) | **0**                                                                     |
| 2026-08-19 | "                                                     | pairs where the old Hamming formula was wrong                                     | **402**                                | **0**                                                                     |
| 2026-08-19 | four divisions by zero fixed                          | non-finite Gene Sharing entries, `dolphins_binary.nex`                            | **35**                                 | **0**                                                                     |
| 2026-08-19 | `HammingDistance` rewritten                           | mismatches vs an independent implementation, 8 option combinations × 3 alignments | —                                      | **0**                                                                     |
| 2026-07    | PhyloFusion tree tracing ported                       | trees byte-exact against Banu Cetinkaya's reference, branch lengths included      | —                                      | **118 / 120**                                                             |

## Headless behaviour — measured, not assumed

| date       | what                                                           | result                                                                                                                              |
|------------|----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| 2026-08-17 | whole workflow, no toolkit                                     | computes inline and synchronously; `RunWorkflow` regression **byte-identical**                                                      |
| 2026-08-17 | outline, equal angle, four rooted layouts, general-network SPI | all report taxon coordinates; **outline and equal angle agree on every taxon coordinate**                                           |
| 2026-08-17 | not available headless, by nature                              | curved edge control points, label placement, pane scaling                                                                           |
| 2026-08-17 | `AlgorithmTabsManager` in a headless `RunWorkflow` run         | throws in **1 run out of 3** via `Platform.runLater`; output unaffected — two runs byte-identical apart from the creation timestamp |

## Instruments and their traps

- **`.stree6` round trips** are the only regression check that exists. Two runs of the same workflow
  are byte-identical **apart from the creation timestamp** — diff accordingly.
- **The example workflows carry dead options** (`UsePreconditioner`, `UseDual`, `Normalize`,
  `ShowConfidence`), which load with a warning. They exercise the pipeline but **do not pin down
  current defaults**, so do not read them as a specification.
- **The JavaFX native classifier follows the JVM Maven runs on.** This machine's default `java` is an
  x86_64 GraalVM 17 on an arm64 Mac; a build with an arm64 JDK produces jars the default JVM cannot
  run.
- **fmmm-layout needs JDK 22+ and `-Pdesktop`**; on JDK 17 it silently falls back to jloda's own
  layout. The startup line `Graph layout: …` says which one you got — check it before comparing
  layout timings or coordinates.
