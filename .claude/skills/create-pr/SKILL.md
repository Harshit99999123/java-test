---
name: create-pr
description: Run the full test suite, commit any uncommitted work, review the branch locally with review-pr, then push and open a GitHub pull request with a Summary / Why / How it was tested / Risks body. Refuses to run on main, when tests fail, or when the review verdict is request changes. Run manually with /create-pr.
model: sonnet
disable-model-invocation: true
argument-hint: "[optional notes for the PR description]"
---

# Create PR

Open a pull request for the current branch. Extra context from the user (may be empty): **$ARGUMENTS**

Do these steps in order. If any step says stop, stop: tell the user why and do nothing further.

## 1. Check the branch

Run `git branch --show-current`.

- If it's `main` (or empty, meaning detached HEAD), **stop**. Tell the user to create a branch first, for example `git switch -c feat/<short-name>`. Don't create one for them.

## 2. Run the full test suite

```bash
./mvnw test
```

Always use the wrapper, never the system `mvn`.

- If the build fails or any test fails or errors, **stop**. Show the failing test names and the summary line, and don't commit, push or open a PR. Don't try to fix the failures in this skill.
- On success, note the final line, `Tests run: N, Failures: 0, Errors: 0, Skipped: S`, for the PR body.

## 3. Commit uncommitted changes

Run `git status --short`. If the working tree is clean, skip to step 4.

Otherwise:

- Review the changes with `git diff` (and `git diff --cached`) so the message describes them accurately.
- Stage files **by path**. Never use `git add -A` or `git add .`.
- **Never commit** `.claude/settings.local.json`, `target/`, IDE files, `.env` or anything that looks like a secret. If unsure about a file, leave it out and mention it in the final report.
- Write a descriptive commit message: an imperative subject line of 72 characters or less, a blank line, then a short body explaining what changed and why. If the changes are clearly unrelated, make separate commits.

## 4. Review the local diff

Run the `review-pr` skill with **no argument** (use the Skill tool: skill `review-pr`, empty args). That puts it in local mode, reviewing `git diff main...HEAD`. Because step 3 has already committed everything, the review covers all the work that's about to be pushed.

Read the verdict at the end of its report:

- **Request changes:** **stop**. Don't push and don't open a PR. Show the user the review's findings exactly as it reported them: severity, `file:line`, the problem, an example and the fix. Also tell them that the commits from step 3 exist only locally and weren't pushed, and that running `/create-pr` again after fixing will re-test and re-review. Don't try to fix the findings yourself.
- **Approve with comments:** continue. Keep the Minor and Nit findings for the PR body (step 6) and the final report.
- **Approve:** continue.

If the review fails to run or doesn't end with one of those three verdicts, **stop** and tell the user. Don't treat it as an approval.

## 5. Push the branch

```bash
git push -u origin HEAD
```

Never force push. If the push is rejected (e.g. the remote has new commits), **stop** and tell the user. Don't rebase or merge for them.

## 6. Open the PR with gh

First, check whether this branch already has a PR: `gh pr view --json url,state`. If an open PR exists, don't create another one. Report its URL and say the new commits were pushed to it.

**Pick the base branch:**
- The default is `main`.
- If this branch was cut from another branch that has its own open PR (check `gh pr list --state open` and `git log --oneline main..HEAD`), use that branch as the base so the diff shows only this work. Add a note at the top of the body: `> Stacked on #<n>. Merge #<n> first.`

**Title:** short and imperative, about 60 characters or less (e.g. "Add PATCH /api/tasks/{id}/complete"). No trailing period.

**Body:** base it on `git log <base>..HEAD` and `git diff <base>...HEAD`, not just the last commit. Include exactly these sections:

```markdown
## Summary
What changed, as a few bullets: endpoints, behavior, files of note.

## Why
The problem or need this addresses.

## How it was tested
Tests added or changed (by class), plus the `./mvnw test` summary line from step 2,
plus the local review verdict from step 4.

## Risks
What could break or behave differently: changed API contracts, error messages that clients
or tests depend on, data or schema effects, anything not covered by tests. Include any
unresolved Minor/Nit findings from step 4. Write "Low: …" with a reason if there's little
risk; don't leave it empty.
```

Fold any notes from `$ARGUMENTS` into the relevant sections.

Create the PR with a heredoc so the formatting survives:

```bash
gh pr create --base <base> --head <branch> --title "<title>" --body "$(cat <<'EOF'
...
EOF
)"
```

## 7. Print the PR URL

End with:

- the PR URL, on its own line
- the review verdict from step 4
- the base branch, and whether the PR is stacked on another one
- the commits that were included
- any files you deliberately left uncommitted
