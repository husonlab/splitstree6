---
description: Open a new lab entry for an idea worth investigating
argument-hint: <short-name describing the question>
---

Open a new investigation entry.

1. First **search `ai/lab/INDEX.md`** for anything close to this question. If it has already been
   asked, say so, quote the verdict and its number, and ask whether to proceed anyway before
   creating anything.
2. Copy `ai/lab/TEMPLATE.md` to `ai/lab/<today>_$1.md` using today's real date (run `date +%F`).
3. Fill in sections 1–3 **and stop there**:
   - **§1 The question** — phrase it so that it could turn out to be false. If it cannot be phrased
     that way, it is a task: put it in `ai/open/todo.md` instead and say so.
   - **§2 Why it matters** — what changes under each answer. If the honest answer is "nothing much",
     say so and close the entry now as `abandoned`. That is a legitimate and valuable outcome.
   - **§3 How it would be settled** — the dataset, the instrument, the number that counts as yes and
     the number that counts as no, and the **gate** at which the entry closes without further work.
     Check `ai/lab/measurements.md` for whether our instruments can even resolve the effect
     expected, and `ai/open/questions.md` for the standing hazards.
4. Do **not** start measuring or writing code in the same step. Show §1–§3 and wait for Daniel to
   agree that this is the right question and the right gate.

Argument: `$1` — a short kebab-case name for the question.
