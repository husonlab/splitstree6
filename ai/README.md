# `ai/` — the documentation an AI agent reads

Three layers, named for their epistemic status. Which layer a fact belongs in is decided by **how
sure we are of it**, not by what it is about.

|                   | Directory | Holds                                                                       | Written how                                                      |
|-------------------|-----------|-----------------------------------------------------------------------------|------------------------------------------------------------------|
| **What we know**  | `docs/`   | The program, the mathematics, the tools, the testing. Things believed true. | Rewritten in place. There is one current version.                |
| **What we don't** | `open/`   | Open questions, hunches, hazards, and tasks whose answer is already known.  | Rewritten in place. Items leave when settled.                    |
| **What we tried** | `lab/`    | One file per question investigated, plus the ledger of all of them.         | Append-only. Nothing is ever deleted, including the wrong turns. |

## Where to start

- **New to the project:** `docs/10_context.md`, then `docs/20_logic.md`.
- **Starting a session:** `open/questions.md` and `open/todo.md`.
- **About to try an idea:** copy `lab/TEMPLATE.md` to `lab/YYYY-MM-DD_slug.md` — or run `/probe`.
- **"Did we already try this?":** `lab/INDEX.md`. Search it before starting anything.
- **"What did that number used to be?":** `lab/measurements.md`.

## The one rule that makes this work

An idea is not finished when the code is written. It is finished when `lab/INDEX.md` has a row
with a **verdict**. A refuted idea with a number attached is worth as much as a confirmed one, and
costs the next person nothing to skip.
