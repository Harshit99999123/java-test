#!/bin/bash
# PreToolUse hook for the Bash tool: refuse `git push` that would touch a
# protected branch. Claude Code sends the tool call as JSON on stdin.
#
#   exit 0 -> allow the command (it still goes through normal permission rules)
#   exit 2 -> block it; whatever is on stderr is shown to Claude as the reason
#
# Uses only what ships with macOS: /bin/bash 3.2, /usr/bin/jq, git, sed.

PROTECTED="main develop"

input=$(cat)
command=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')
cwd=$(printf '%s' "$input" | jq -r '.cwd // empty')
[ -n "$cwd" ] || cwd=$PWD

# Fast path: nothing that mentions push can't be a push.
case "$command" in
  *push*) ;;
  *) exit 0 ;;
esac

is_protected() {
  local b
  for b in $PROTECTED; do
    [ "$1" = "$b" ] && return 0
  done
  return 1
}

block() {
  {
    echo "Blocked: $1"
    echo "Pushing to a protected branch ($PROTECTED) is not allowed."
    echo "Create a feature branch instead (git switch -c feat/<short-name>), commit there,"
    echo "and open a pull request with /create-pr."
  } >&2
  exit 2
}

# Split the command line into simple commands at ; && || | and newlines, and
# drop quotes so `git push "origin" 'main'` tokenizes like `git push origin main`.
segments=$(printf '%s\n' "$command" | sed -e 's/&&/\
/g' -e 's/||/\
/g' -e 's/[;|]/\
/g' -e "s/[\"']//g")

while IFS= read -r segment; do
  set -f                      # no glob expansion while word-splitting
  # shellcheck disable=SC2086
  set -- $segment
  set +f

  # Walk to the `git` word, skipping env assignments and wrappers (FOO=1 git ..., command git ...).
  while [ $# -gt 0 ] && [ "$1" != "git" ]; do shift; done
  [ $# -gt 0 ] || continue
  shift

  # Global git options before the subcommand; remember -C <dir> for the branch check.
  dir=$cwd
  while [ $# -gt 0 ]; do
    case "$1" in
      -C) [ -n "$2" ] && dir=$(cd "$cwd" 2>/dev/null && cd "$2" 2>/dev/null && pwd); shift 2 ;;
      -c|--git-dir|--work-tree|--namespace) shift 2 ;;
      -*) shift ;;
      *) break ;;
    esac
  done
  [ "$1" = "push" ] || continue
  shift
  [ -n "$dir" ] || dir=$cwd

  # Rule 1: never push while sitting on a protected branch.
  current=$(git -C "$dir" branch --show-current 2>/dev/null)
  if is_protected "$current"; then
    block "the current branch is '$current'."
  fi

  # Rule 2: never push to a protected branch by name. Collect positional
  # arguments: the first is the remote, the rest are refspecs.
  positionals=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --all|--mirror)
        block "'git push $1' would push every branch, including protected ones." ;;
      -o|--push-option|--repo|--receive-pack|--exec)
        shift 2 ;;                       # options that take a separate value
      --) shift; positionals="$positionals $*"; break ;;
      -*) shift ;;                       # any other flag (-u, --force-with-lease=..., --delete)
      *) positionals="$positionals $1"; shift ;;
    esac
  done

  first=1
  for arg in $positionals; do
    if [ $first = 1 ]; then first=0; continue; fi   # skip the remote name
    target=${arg#+}                  # +main        -> main (forced update)
    target=${target##*:}             # HEAD:main    -> main, :main (delete) -> main
    target=${target#refs/heads/}     # refs/heads/main -> main
    if is_protected "$target"; then
      block "this push targets the protected branch '$target' ($arg)."
    fi
  done
done <<EOF
$segments
EOF

exit 0
