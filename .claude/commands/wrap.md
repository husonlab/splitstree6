---
description: Close out the session — update the ledger, the questions and the todo
---

Close out this session. Work through these in order and report what you changed; if a step needs
nothing, say "nothing" rather than skipping it silently.

1. **Any open lab entry touched today** — append a dated section to its `## 4. Log` saying what was
   tried, what came out, and what it means, in numbers. If a result contradicted the entry's §3, say
   so in the first sentence. If the question is now answered, fill in §5 Result and §6 What this
   rules out, set **Status** and **Verdict** in the header, and set **Closed**.
2. **`ai/lab/INDEX.md`** — add or update a row for every question that closed. Verdict must be one
   of `confirmed`, `refuted`, `inconclusive`, `shipped`, `reverted`, `parked`. The result column
   carries the number, not an adjective.
3. **`ai/lab/measurements.md`** — append every figure worth comparing to a later one, with the
   instrument and the conditions. Never edit an existing row.
4. **`ai/open/questions.md`** — remove what was settled; add anything new that came up and that we
   do not know the answer to, with its gate.
5. **`ai/open/todo.md`** — update the checkboxes; move anything that turned out to be a question
   rather than a task over to `questions.md`.
6. **`ai/lab/journal.md`** — add a dated entry at the top for anything worth remembering that does
   not fit a single question: dead ends, surprises, decisions and why they went the way they did.
7. **`ai/docs/`** — if an algorithm changed, confirm the relevant `2x_logic_*.md`, the class javadoc
   and the in-code comments say the new truth. If code and documentation disagree and you did not
   fix it, add it to that file's discrepancy section.
8. **Report honestly.** End with a short summary: what is finished, what is half-done, what is
   broken, and what needs a decision from Daniel. Do not round a flat result up into a win.
