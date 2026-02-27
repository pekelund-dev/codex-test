# Agent Prompt — Work Through TICKETS.md

> **Purpose:** Reusable prompt for an AI coding agent (Copilot, Codex, etc.) that
> picks up one issue at a time from `TICKETS.md`, implements it, and moves on to the next.
>
> Copy the prompt below and paste it as the agent instructions for a new issue or session.

---

## The Prompt

```text
You are implementing improvements from the project backlog.

### Workflow

1. **Pick the next issue**
   - Read `TICKETS.md` and find the first issue that has NOT already been completed
     (i.e. it still has a `### Issue:` section in the file).
   - If `TICKETS.md` is empty or contains no more issues, report that all issues are
     done and stop.

2. **Set up PROGRESS.md**
   - Create (or overwrite) `PROGRESS.md` with the issue title, description, tasks,
     and acceptance criteria copied from `TICKETS.md`.
   - Remove that issue block (from `### Issue:` through the closing `~~~` and the
     `---` separator) from `TICKETS.md` so it is not picked up again.
   - Commit both file changes: "chore: start work on <issue title>".

3. **Plan**
   - Analyse the codebase to understand what needs to change.
   - Add an **Implementation plan** section to `PROGRESS.md` with numbered steps.
   - Commit: "docs: add implementation plan for <issue title>".

4. **Implement**
   - Work through the plan step by step.
   - After each meaningful unit of work, update the task checklist in `PROGRESS.md`
     (mark items `[x]`) and commit with a descriptive message.
   - Run relevant tests after each change — use the narrowest test scope possible
     (see `AGENTS.md` for build/test commands).
   - If a step fails or needs adjustment, update the plan in `PROGRESS.md` and continue.

5. **Verify**
   - Confirm all acceptance criteria are met.
   - Run `./mvnw verify` (or the appropriate full build) to ensure nothing is broken.
   - If the issue touches frontend code, also run `cd web && npm run lint`.

6. **Archive**
   - Add a **Completion summary** section to `PROGRESS.md` that includes:
     - Short description of what was done
     - Tests run and their results (exact commands)
     - The commit hash of the final implementation commit
   - Rename `PROGRESS.md` to `done/<ISSUE_TITLE_SLUG>.md`
     (e.g. `done/add-jacoco-test-coverage-reporting.md`).
     Create the `done/` directory if it does not exist.
   - Commit: "docs: complete <issue title>".

7. **Repeat**
   - Go back to step 1 and pick the next issue.
   - Continue until `TICKETS.md` has no remaining issues.

### Rules
- Follow the conventions in `AGENTS.md` (Swedish UI text, English code, incremental
  Maven builds, update docs alongside code changes).
- Do NOT skip issues or reorder them — work top to bottom.
- Keep each issue in its own commit sequence so it can be reviewed independently.
- If an issue is blocked (e.g. requires infrastructure access you don't have), leave
  it in `TICKETS.md`, add a `**Blocked:**` note with the reason, and move to the next.
- Reference `docs/architecture-review.md` when you need design context.
```

---

## Quick-start (single issue)

If you only want the agent to handle **one** issue per session, use this shorter variant:

```text
Read `TICKETS.md` and pick the first remaining issue.
Move it to `PROGRESS.md`, remove it from `TICKETS.md`, and commit.
Create an implementation plan in `PROGRESS.md` and commit.
Implement the plan step by step, updating `PROGRESS.md` as you go.
When done, add a completion summary with the final commit hash,
rename `PROGRESS.md` to `done/<issue-slug>.md`, and commit.
Follow conventions in `AGENTS.md`.
```
