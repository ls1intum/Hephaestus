---
mode: agent
description: Fetch, analyze, and resolve PR review comments with full code context
---

# Resolve Review Comments

Address review comments on the current PR - works for any reviewer (human, Copilot, CodeRabbit, etc.).

## 1. Get Current PR

```bash
PAGER=cat gh pr view --json number,url,title --jq '"#\(.number): \(.title)\n\(.url)"'
```

If no PR, run `/land-pr` first.

## 2. Fetch Unresolved Comments

```bash
PAGER=cat gh api graphql -f query='
query($owner: String!, $repo: String!, $number: Int!) {
  repository(owner: $owner, name: $repo) {
    pullRequest(number: $number) {
      reviewThreads(first: 50) {
        nodes {
          id
          isResolved
          path
          line
          comments(first: 3) {
            nodes {
              body
              diffHunk
              author { login }
            }
          }
        }
      }
    }
  }
}' -F owner=$(gh repo view --json owner -q .owner.login) \
   -F repo=$(gh repo view --json name -q .name) \
   -F number=$(PAGER=cat gh pr view --json number -q .number) \
  | jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)]'
```

This returns for each unresolved thread:
- `id`: Thread ID for resolution
- `path`: File path  
- `line`: Line number
- `comments[].body`: The actual feedback
- `comments[].diffHunk`: Code context showing what changed

## 3. Address Each Comment

For each unresolved comment, read `diffHunk` and `body` then decide:

| Situation | Action |
|-----------|--------|
| Already fixed in a later commit | Resolve |
| Style preference / nitpick | Resolve (or fix if trivial) |
| Valid bug or issue | Fix the code, then resolve |
| Disagree with suggestion | Reply explaining why, leave open for discussion |

**Best practices:**
- Address ALL comments before merging
- If fixing, explain what you changed in a reply
- For disagreements, provide technical reasoning

## 4. Resolve a Thread

```bash
PAGER=cat gh api graphql -f query='
mutation($threadId: ID!) {
  resolveReviewThread(input: {threadId: $threadId}) {
    thread { isResolved }
  }
}' -f threadId="<THREAD_ID>"
```

Replace `<THREAD_ID>` with the `id` from step 2 (e.g., `PRRT_kwDOL-LvAM5l9NCU`).

## 5. Reply to a Comment (optional)

```bash
PAGER=cat gh pr comment $(PAGER=cat gh pr view --json number -q .number) --body "Addressed in latest commit: <explanation>"
```

Or reply inline via the GitHub web UI for threaded discussions.

## 6. Verify All Resolved

```bash
PAGER=cat gh api graphql -f query='
query($owner: String!, $repo: String!, $number: Int!) {
  repository(owner: $owner, name: $repo) {
    pullRequest(number: $number) {
      reviewThreads(first: 50) {
        nodes { isResolved }
      }
    }
  }
}' -F owner=$(gh repo view --json owner -q .owner.login) \
   -F repo=$(gh repo view --json name -q .name) \
   -F number=$(PAGER=cat gh pr view --json number -q .number) \
  | jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)] | length'
```

Output should be `0` when all comments are addressed.
