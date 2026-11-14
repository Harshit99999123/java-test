---
name: review-pr
description: Read-only code review for this repo. With a PR number it reviews that GitHub pull request; with no argument it reviews the current branch locally against main (git diff main...HEAD). Checks correctness, missing tests, CLAUDE.md conventions, error handling, and claims the code doesn't back up. Reports findings ranked by severity with file:line and gives a verdict (approve / approve with comments / request changes). Never edits files or posts to GitHub unless asked.
model: opus
context: fork
argument-hint: "[pr-number]"
allowed-tools: Read, Grep, Glob, Bash(gh pr view:*), Bash(gh pr diff:*), Bash(gh pr list:*), Bash(gh pr checks:*), Bash(git fetch:*), Bash(git show:*), Bash(git log:*), Bash(git diff:*), Bash(git status:*), Bash(git branch:*), Bash(git rev-parse:*), Bash(git merge-base:*)
---

# Review PR

Argument: **$ARGUMENTS**

## Pick the mode

- **PR mode:** `$ARGUMENTS` is a PR number (e.g. `2` or `#2`). Review that GitHub pull request.
- **Local mode:** `$ARGUMENTS` is empty. Review the current branch's commits against `main`, using only local git and no GitHub calls.
- If `$ARGUMENTS` is anything else, stop and ask for a PR number or no argument.

Say which mode you're in on the first line of the report.

## Ground rules (both modes)

- **Read-only.** Don't create, edit or delete any file. Don't run `git checkout`, `git switch`, `git stash`, `git reset`, `git commit` or anything else that changes the working tree, the index or the current branch. Don't run `./mvnw` either, since it writes to `target/`.
- **Don't post to GitHub** (no `gh pr review`, `gh pr comment`, `gh pr merge`, labels or edits) unless the user explicitly asks in this conversation.
- Report only what you've verified in the code. If something is a suspicion you couldn't confirm, label it as such.

## 1. Get the changes and the description

### PR mode

```bash
gh pr view <n> --json number,title,body,author,baseRefName,headRefName,state,files,commits
gh pr diff <n>
gh pr checks <n>        # CI status, if any
git fetch origin <headRefName>
```

- **What the change claims to do:** the PR title and body.
- **Code under review:** `origin/<headRefName>`.
- **Stacked PRs:** if the base isn't `main`, this is a stacked PR. Review only its own diff against that base, and don't flag changes that belong to the parent PR.

### Local mode

```bash
git branch --show-current
git log --oneline main..HEAD
git diff --stat main...HEAD
git diff main...HEAD
git status --short
```

- **On `main`, or nothing in `main..HEAD`:** stop and say there's nothing to review.
- **What the change claims to do:** the commit messages in `main..HEAD`. Read them in full with `git log main..HEAD`.
- **Code under review:** `HEAD`.
- **Uncommitted changes:** `main...HEAD` covers committed work only. If `git status` shows uncommitted changes, list them in the report as **not reviewed**, but don't review them.
- **Branch built on another branch:** `main...HEAD` includes every commit since `main`, including commits from a parent branch this one was cut from. If the log shows that, say so in the report.

## 2. Read the surrounding code

The diff alone isn't enough. Read the full version of each changed file at the ref under review (`origin/<headRefName>` in PR mode, `HEAD` in local mode) without checking it out:

```bash
git show <ref>:<path>
```

Use `git show` even in local mode, so uncommitted edits in the working tree don't skew the review.

Then read whatever it touches: callers and callees, the DTOs, `GlobalExceptionHandler`, the entity, and the existing tests for that layer. Read `CLAUDE.md` for the conventions you'll check against.

## 3. Check

Go through each of these:

- **Correctness:** logic errors, null handling, wrong HTTP status codes, transaction boundaries (`@Transactional` / `readOnly` on writes), lazy loading outside a transaction (`open-in-view=false`), PUT full-replace semantics, the split dueDate rule (create vs. update), and edge cases such as already-in-state, missing ID and non-numeric ID.
- **Missing tests:** every new endpoint needs a controller test (`TaskControllerTest` style) **and** an integration test (`TaskApiIntegrationTest` style). Service logic needs `TaskServiceTest` cases. Check that error paths are tested, not just the happy path, and that tests assert exact `detail` / `errors.*` strings.
- **CLAUDE.md conventions:**
  - constructor injection only (no field `@Autowired` in `src/main`)
  - DTOs are records, and entities never cross the web boundary
  - controllers stay thin
  - `TaskService` owns transactions
  - the integration test stays **not** `@Transactional`
  - Bean Validation messages live on `TaskRequest`
- **Error handling:** every error must reach the client as a `ProblemDetail` through `GlobalExceptionHandler`. 400s carry an `errors` map. Nothing echoes Jackson or internal exception messages, and nothing uses `ResponseStatusException` or a hand-built error body in the controller. A new exception type needs a handler.
- **Description vs. code:** check each claim in the PR body (PR mode) or the commit messages (local mode) against the code: behavior, status codes, "idempotent", test counts, "no new X". Flag any claim the code doesn't support, and any significant change that isn't mentioned.

## 4. Report findings

Rank them by severity:

- **Critical:** wrong behavior, data loss, a security problem or a broken build.
- **Major:** a bug in a realistic case, required tests missing, or a convention violation that matters.
- **Minor:** an edge case, weak assertions or unclear code.
- **Nit:** style or naming. Keep these few.

For each finding, give:
- the severity, plus `file:line` (a path in the version under review)
- what's wrong, in a sentence
- a concrete example: the input or request and what happens
- a suggested fix

If there are no findings at a level, skip it. If there are none at all, say so plainly.

## 5. Verdict

End with exactly one of these:

- **Approve:** no Critical or Major findings, and nothing worth blocking on.
- **Approve with comments:** only Minor or Nit findings worth addressing.
- **Request changes:** any Critical or Major finding.

Add one or two sentences explaining why.

In PR mode, mention you can post the review to GitHub if the user wants, but don't do it. In local mode, there's nothing to post.
