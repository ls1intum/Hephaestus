---
description: Fetch, analyze, and resolve PR review comments with full code context
---

# Resolve Review Comments

// turbo-all

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

| Situation     | Action                           |
| ------------- | -------------------------------- |
| Already fixed | Resolve thread                   |
| Valid issue   | Fix code, then resolve           |
| Disagree      | Reply with reasoning, leave open |

## 4. Resolve Thread

```bash
PAGER=cat gh api graphql -f query='
mutation($threadId: ID!) {
  resolveReviewThread(input: {threadId: $threadId}) {
    thread { isResolved }
  }
}' -f threadId="<THREAD_ID>"
```

## 5. Handle Code Scanning Alerts (github-advanced-security)

Security scan threads from `github-advanced-security` cannot be resolved via `resolveReviewThread` (returns "not a conversation"). Handle them separately:

### Dismiss Code Scanning Alerts

```bash
# List open alerts
gh api repos/{owner}/{repo}/code-scanning/alerts --jq '.[] | select(.state == "open") | "\(.number): \(.rule.id) - \(.most_recent_instance.location.path)"'

# Dismiss an alert (reasons: "false positive", "won't fix", "used in tests")
gh api -X PATCH repos/{owner}/{repo}/code-scanning/alerts/{alert_number} \
  -f state=dismissed \
  -f "dismissed_reason=false positive" \
  -f "dismissed_comment=Reason for dismissal"
```

### Minimize Security Comments (Hide Clutter)

Security threads have `viewerCanResolve: false`. To hide them, minimize the comments:

```bash
# Get comment IDs from unresolved security threads
PAGER=cat gh api graphql -f query='
query($owner: String!, $repo: String!, $number: Int!) {
  repository(owner: $owner, name: $repo) {
    pullRequest(number: $number) {
      reviewThreads(first: 100) {
        nodes {
          isResolved
          comments(first: 1) {
            nodes { id author { login } }
          }
        }
      }
    }
  }
}' -F owner="$OWNER" -F repo="$REPO" -F number="$PR_NUMBER" \
  | jq -r '.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false and .comments.nodes[0].author.login == "github-advanced-security") | .comments.nodes[0].id'

# Minimize each comment (collapses with "marked as resolved")
gh api graphql -f query='
mutation($id: ID!) {
  minimizeComment(input: {subjectId: $id, classifier: RESOLVED}) {
    minimizedComment { isMinimized }
  }
}' -f id="<COMMENT_ID>"
```

## 6. Verify

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
