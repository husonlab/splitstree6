# <the question, in one line, ending in a question mark>

**Status:** open &nbsp;&nbsp; <!-- open | answered | abandoned -->
**Verdict:** — &nbsp;&nbsp; <!-- one sentence. This is the line INDEX.md copies. -->
**Opened:** YYYY-MM-DD &nbsp;&nbsp; **Closed:** —
**Raised by:** Daniel | the agent | a bug report | a measurement
**Touches:** `ai/docs/2x_logic_*.md` §…, `src/main/java/megan8/…`

---

## 1. The question

Ask something that could turn out to be false. Not "improve X" but "does X cost more than 10 % of a
run?", "does the weighted LCA beat the naive one at τ = 5?", "is the sweep quadratic in *m*, or only
on staggered intervals?"

If the entry cannot be phrased this way, it is a task, not a question — put it in `../open/todo.md`.

## 2. Why it matters

What becomes possible, or what stops being a worry, under each answer. If the honest answer is
"nothing much either way", write that and close the entry here. That is an afternoon saved, and it
is a legitimate outcome — set **Status: abandoned**, give the verdict, and add the row to the index.

## 3. How it would be settled

**Write this before taking any measurement.** The dataset, the instrument, the number that would
count as yes, and the number that would count as no. Committing to this in advance is what stops a
measurement being read as whatever we hoped it would say.

If nothing could settle it as things stand, say so, and say what would have to be built first.

> **Gate:** <the condition under which this entry closes without further work — e.g. "if
> `ActiveMatches` is under 10 % of binning, close and do not act">

## 4. Log

Append only, newest at the bottom, dated. Wrong turns stay in — they are the record of what the
codebase does to a reasonable hypothesis.

### YYYY-MM-DD — <what was tried>

What was done, what came out, what it means. Numbers, not adjectives.

If the result contradicts §3, **say so in the first sentence.** That line is the most valuable one
in the file, and it is the one there is most temptation to soften.

## 5. Result

The answer, and how much weight it bears. Sample sizes, run-to-run spread, and the granularity of
the instrument — "23 s against 22 s" on a stopwatch that reports whole seconds is not a difference.

Copy the verdict up to the header, and add the row to `INDEX.md`. Put every number worth
comparing later into `measurements.md`.

## 6. What this rules out

The negative result, stated plainly enough to save the next person the work: what has been tried,
under what conditions, and what would have to be different for it to be worth trying again.

## 7. Plan &nbsp; <!-- only once the answer is "build this" -->

A plan is an *outcome* of an investigation, not its precondition. Steps in order, what has to keep
working, and how each step is verified. Then a `## 8. Outcome` when it has been done — including
"applied, and it did not help", which is a result and stays.
