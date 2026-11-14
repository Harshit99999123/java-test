#!/bin/bash
# PreToolUse hook for the Bash tool: refuse a `git push` that would touch a
# protected branch. Claude Code sends the tool call as JSON on stdin.
#
#   exit 0 -> allow the command (it still goes through normal permission rules)
#   exit 2 -> block it; whatever is on stderr is shown to Claude as the reason
#
# Only commands that actually *run* git push are checked. The command line is
# tokenized like a shell would (quotes, escapes, heredocs), so text that merely
# mentions "git push origin main" -- a commit message, an echo, a heredoc body --
# is not mistaken for a push.
#
# Uses only what ships with macOS: /bin/bash and /usr/bin/python3 (stdlib only).
# Fails open: if Python is unavailable or crashes, the exit code is not 2, so
# Claude Code lets the command through rather than blocking every Bash call.

input=$(cat)

# Fast path: a command that never mentions push can't be a push.
case "$input" in
  *push*) ;;
  *) exit 0 ;;
esac

HOOK_INPUT=$input exec /usr/bin/python3 -I - <<'PY'
import json
import os
import re
import shlex
import subprocess
import sys

PROTECTED = ("main", "develop")

SEPARATOR_CHARS = set(";&|()\n")
REDIRECTS = {">", ">>", "<", "<<", "<<<", "<<-", ">&", "<&", "&>", "&>>", ">|", "<>"}
# Words that run the next word as the command.
WRAPPERS = {"command", "builtin", "exec", "nohup", "time", "sudo", "env", "nice",
            "{", "!", "if", "then", "else", "elif", "while", "until", "do"}
SHELLS = {"sh", "bash", "zsh", "dash", "ksh"}
# git push options that take their value as the next argument.
PUSH_OPTS_WITH_VALUE = {"-o", "--push-option", "--repo", "--receive-pack", "--exec"}
GIT_OPTS_WITH_VALUE = {"-c", "--git-dir", "--work-tree", "--namespace", "--config-env"}


def block(reason):
    sys.stderr.write(
        "Blocked: %s\n"
        "Pushing to a protected branch (%s) is not allowed.\n"
        "Create a feature branch instead (git switch -c feat/<short-name>), commit there,\n"
        "and open a pull request with /create-pr.\n" % (reason, " ".join(PROTECTED)))
    sys.exit(2)


def strip_heredoc_bodies(text):
    """Drop the body lines of every heredoc; they are data, not commands."""
    lines = text.split("\n")
    out, i = [], 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        i += 1
        for m in re.finditer(r"(?<!<)<<(-?)\s*(['\"]?)([A-Za-z_][A-Za-z0-9_]*)\2", line):
            strip_tabs, delimiter = m.group(1) == "-", m.group(3)
            while i < len(lines):
                body = lines[i]
                i += 1
                if (body.lstrip("\t") if strip_tabs else body) == delimiter:
                    break
    return "\n".join(out)


def simple_commands(text):
    """Split a command line into simple commands (lists of words), shell-style."""
    text = strip_heredoc_bodies(text.replace("\\\n", " "))
    lexer = shlex.shlex(text, posix=True, punctuation_chars=";&|()<>\n")
    lexer.whitespace = " \t\r"          # newline is a separator, not whitespace
    lexer.whitespace_split = True
    try:
        tokens = list(lexer)
    except ValueError:                  # unbalanced quotes: the shell would refuse it too
        return []

    commands, words, skip_next = [], [], False
    for tok in tokens:
        if skip_next:                   # target of a redirection (> file, <<EOF, ...)
            skip_next = False
            continue
        if tok and set(tok) <= set(";&|()<>\n"):
            if tok in REDIRECTS:
                skip_next = True
                continue
            if set(tok) & SEPARATOR_CHARS:
                if words:
                    commands.append(words)
                words = []
                if tok.endswith((">", "<")):   # e.g. ")>" : separator followed by a redirect
                    skip_next = True
                continue
        words.append(tok)
    if words:
        commands.append(words)
    return commands


def check(command_line, cwd, depth=0):
    if depth > 3:
        return
    for words in simple_commands(command_line):
        # Skip env assignments and wrappers to reach the program actually run.
        i = 0
        while i < len(words):
            w = words[i]
            if re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", w):
                i += 1
            elif w in WRAPPERS:
                i += 1
                while i < len(words) and words[i].startswith("-"):   # e.g. time -p, env -i
                    i += 1
            else:
                break
        if i >= len(words):
            continue
        program, args = os.path.basename(words[i]), words[i + 1:]

        # `bash -c '...'` and `eval ...` run a nested command line: check it too.
        if program in SHELLS:
            for j, a in enumerate(args):
                if re.match(r"^-[A-Za-z]*c[A-Za-z]*$", a) and j + 1 < len(args):
                    check(args[j + 1], cwd, depth + 1)
                    break
            continue
        if program == "eval":
            check(" ".join(args), cwd, depth + 1)
            continue
        if program != "git":
            continue

        # git's own options before the subcommand; remember -C <dir>.
        repo_dir, j = cwd, 0
        while j < len(args):
            a = args[j]
            if a == "-C" and j + 1 < len(args):
                repo_dir = os.path.join(repo_dir, args[j + 1])
                j += 2
            elif a in GIT_OPTS_WITH_VALUE:
                j += 2
            elif a.startswith("-"):
                j += 1
            else:
                break
        if j >= len(args) or args[j] != "push":
            continue
        push_args = args[j + 1:]

        # Rule 1: never push while sitting on a protected branch.
        try:
            current = subprocess.run(
                ["git", "-C", repo_dir, "branch", "--show-current"],
                capture_output=True, text=True, timeout=5).stdout.strip()
        except Exception:
            current = ""
        if current in PROTECTED:
            block("the current branch is '%s'." % current)

        # Rule 2: never push to a protected branch by name.
        positionals, k = [], 0
        while k < len(push_args):
            a = push_args[k]
            if a in ("--all", "--mirror"):
                block("'git push %s' would push every branch, including protected ones." % a)
            if a in PUSH_OPTS_WITH_VALUE:
                k += 2
            elif a == "--":
                positionals.extend(push_args[k + 1:])
                break
            elif a.startswith("-"):
                k += 1
            else:
                positionals.append(a)
                k += 1
        for refspec in positionals[1:]:          # positionals[0] is the remote
            target = refspec.lstrip("+")              # +main            -> main
            target = target.rsplit(":", 1)[-1]        # HEAD:main, :main -> main
            if target.startswith("refs/heads/"):      # refs/heads/main  -> main
                target = target[len("refs/heads/"):]
            if target in PROTECTED:
                block("this push targets the protected branch '%s' (%s)." % (target, refspec))


data = json.loads(os.environ.get("HOOK_INPUT") or "{}")
command = (data.get("tool_input") or {}).get("command") or ""
check(command, data.get("cwd") or os.getcwd())
sys.exit(0)
PY
