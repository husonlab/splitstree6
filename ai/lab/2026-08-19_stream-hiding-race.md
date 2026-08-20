# What leaves `System.err` — and `System.out` — pointing at a discarding stream?

**Status:** answered &nbsp;&nbsp;
**Verdict:** A data race on the global stream field: `Basic.hideSystemOut`/`hideSystemErr` save-and-replace one
static, `SplitsBlockUtilities.computeCycle` uses them, and any algorithm running that on several threads at
once has one thread save the *discarding* stream and later restore it. Reproducible from a single algorithm
after all — `BootstrapTreeSplits` does it every time at two threads and never at one. &nbsp;&nbsp;
**Opened:** 2026-08-19 &nbsp;&nbsp; **Closed:** 2026-08-19
**Raised by:** a measurement — a verification probe for `2026-08-19_bootstrapping.md` went silent halfway
through and exited 0
**Touches:** `splitstree6/algorithms/utils/SplitsBlockUtilities.java` §`computeCycle`, `jloda.util.Basic`,
`ai/docs/20_logic.md` §10 D-12, `ai/open/questions.md` Q1

---

## 1. The question

`questions.md` Q1: running the whole algorithm catalogue in sequence ended with `System.err` pointing at a
discarding stream, **no single algorithm reproduced it**, and every hide/restore call site looked correct. The
gate said: bisect the catalogue; and if no *sequence* reproduces it either, "the cause is ordering or
concurrency and the question changes shape".

## 2. Why it matters

Anything jloda prints rather than throws — `Basic.caught` stack traces included — disappears from that point
on. In the application `System.out` is aimed at `System.err` and thence at the message window, so a user who
bootstraps stops seeing warnings for the rest of the session. In `splitstree.py` it detaches the capture buffer
that `java_messages()` reads; the binding already carried a `_reinstall_capture()` workaround written against
this exact symptom, with a docstring saying the culprit had not been found.

## 3. How it would be settled

> **Gate:** run one algorithm that calls `computeCycle` from several threads, with the thread count as the only
> variable. If the streams survive at one thread and not at more, the cause is the race and the entry closes.
> If it fails at one thread too, the save/restore pairing is wrong somewhere and the question is different.

## 4. Log

### 2026-08-19 — found by accident, then pinned down

A probe comparing two bootstrap routes printed its first seven lines and then nothing, and exited **0**. Not a
crash: `System.out` had been replaced underneath it. Holding the original `PrintStream` in a static at start-up
made the rest of the run visible, and an explicit check — is `System.out == Basic.getOrigOut()`? — showed the
streams intact after `BootstrapSplits` and `BootstrapTree`, and **replaced after `BootstrapTreeSplits`**.

That is the discriminating detail: `BootstrapTreeSplits` is the only one of the three whose replicate pipeline
contains `TreeSelectorSplits`, and `TreeSelectorSplits` calls `SplitsBlockUtilities.computeCycle`, which for
`ntax > 3` wraps the SplitsTree4 cycle heuristic in

```java
final var pso = Basic.hideSystemOut();
final var pse = Basic.hideSystemErr();
try { ... } finally { Basic.restoreSystemErr(pse); Basic.restoreSystemOut(pso); }
```

The try/finally is correct. What is not is that `hideSystemOut` reads and writes **one global field**:

```java
public static PrintStream hideSystemOut() { var current = System.out; System.setOut(nullOut); return current; }
```

Two threads entering together: A saves the real stream, B saves `nullOut`, A restores the real one, B restores
`nullOut`. Whichever finishes last wins, and one of them is holding the discarding stream.

Thread count as the only variable, `BootstrapTreeSplits` on `algae.nex`, 100 replicates, seed 42:

| threads | `System.out` | `System.err` |
|---------|--------------|--------------|
| 1       | intact       | intact       |
| 2       | **REPLACED** | **REPLACED** |
| 4       | **REPLACED** | **REPLACED** |
| 8       | **REPLACED** | **REPLACED** |

Every run, no flakiness in either direction.

### 2026-08-19 — and it is not the new code

The standalone route is new. The workflow route is not. A probe that builds an ordinary workflow —
characters → `HammingDistance` → `NeighborJoining` → `BootstrapTreeSplits` — and does nothing else reports
`before: intact` and `after: REPLACED`. The leak is on the application's own path and predates this week.

## 5. Result

Answered, and it closes Q1's second branch exactly as the gate anticipated: not ordering across the catalogue,
but concurrency inside a single algorithm. "No single algorithm reproduces it" was true only because nothing
had yet run a *parallel* algorithm whose replicate pipeline computes a cycle.

The reach is wider than bootstrapping. Anything that computes cycles on a pool has it: `computeCycle` is called
from `TreeSelectorSplits`, `ConfidenceNetwork`, `SuperNetwork`, `BinaryToSplits`, `SplitDecomposition`,
`WeightsSlider` and `SplitsBlockUtilities` itself.

## 6. What this rules out

The hide/restore *call sites* are not the bug and do not need auditing — they are correctly paired, and reading
them was what made this look impossible for two days. `Basic`'s global save-and-restore is not usable from more
than one thread, and no discipline at the call site can make it so.

## 7. Plan — not written, because the choice is Daniel's

Three ways out, and they are not equivalent:

1. **Stop hiding at all in `computeCycle`.** Simplest, and the noise it was suppressing comes back.
2. **Make the hidden region mutually exclusive**, so two threads cannot interleave. Correct, but it serialises
   the cycle heuristic across a bootstrap's worker threads, which is a real cost in the one place it is hottest.
3. **Fix it in jloda3** — make the stream that is being suppressed a parameter of the noisy call rather than a
   global, or make hide/restore reference-counted. The right answer, the largest change, another repository.

Not attempted here: this was found while doing something else, and every option changes behaviour outside
bootstrapping.
