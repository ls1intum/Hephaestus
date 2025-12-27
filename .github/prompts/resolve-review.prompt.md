---
mode: agent
description: Fetch, analyze, and resolve PR review comments with full code context
---

# Resolve Review Comments

Address review comments on the current PR (works for any reviewer: human, Copilot, CodeRabbit, etc.).

## 1. Get PR

```bash
PAGER=cat gh pr view --json number,url,title --jq '"#\(.number): \(.title)\n\(.url)"'
```

If no PR exists, run `/land-pr` first.

## 2. Fetch Unresolved Comments

```bash
PR_NUMBER=$(PAGER=cat gh pr view --json number -q .number)
OWNER=$(PAGER=cat gh repo view --json owner -q .owner.login)
REPO=$(PAGER=cat gh repo view --json name -q .name)

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
            nodes { body diffHunk author { login } }
          }
        }
      }
    }
  }
}' -F owner="$OWNER" -F repo="$REPO" -F number="$PR_NUMBER" \
  | jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)]'
```

## 3. Address Each Comment

Read `diffHunk` (code context) and `body` (feedback):

| Situation | Action |
|-----------|--------|
| Already fixed | Resolve thread |
| Valid issue | Fix code, then resolve |
| Disagree | Reply with reasoning, leave open |

## 4. Resolve Thread

```bash
PAGER=cat gh api graphql -f query='
mutation($threadId: ID!) {
  resolveReviewThread(input: {threadId: $threadId}) {
    thread { isResolved }
  }
}' -f threadId="<THREAD_ID>"
```

## 5. Verify

```bash
PR_NUMBER=$(PAGER=cat gh pr view --json number -q .number)
OWNER=$(PAGER=cat gh repo view --json owner -q .owner.login)
REPO=$(PAGER=cat gh repo view --json name -q .name)

PAGER=cat gh api graphql -f query='
query($owner: String!, $repo: String!, $number: Int!) {
  repository(owner: $owner, name: $repo) {
    pullRequest(number: $number) {
      reviewThreads(first: 50) {
        nodes { isResolved }
      }
    }
  }
}' -F owner="$OWNER" -F repo="$REPO" -F number="$PR_NUMBER" \
  | jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)] | length'
```

Output should be `0`.
