---
description: Fetch, analyze, and resolve PR review comments with full code context
---

# Resolve Review Comments

// turbo-all

## 1. Get PR

```bash
PAGER=cat gh pr view --json number,url,title --jq '"#\(.number): \(.title)\n\(.url)"'
```

## 2. Fetch Unresolved

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
}' -f owner="ls1intum" -f repo="Hephaestus" \
   -F number=$(PAGER=cat gh pr view --json number -q .number) \
  | jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)]'
```

## 3. For Each Comment

Read `diffHunk` (code context) and `body` (feedback):

- **Already fixed?** → Resolve
- **Nitpick?** → Resolve or quick fix
- **Valid issue?** → Fix code, then resolve
- **Disagree?** → Reply with reasoning

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
PAGER=cat gh api graphql -f query='
query($owner: String!, $repo: String!, $number: Int!) {
  repository(owner: $owner, name: $repo) {
    pullRequest(number: $number) {
      reviewThreads(first: 50) {
        nodes { isResolved }
      }
    }
  }
}' -f owner="ls1intum" -f repo="Hephaestus" \
   -F number=$(PAGER=cat gh pr view --json number -q .number) \
  | jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)] | length'
```

Should output `0`.
