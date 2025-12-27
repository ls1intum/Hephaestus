---
description: Analyze and resolve PR review comments with critical thinking
---

# Resolve Review Comments

// turbo-all

## 1. Fetch & Display

```bash
PR=$(PAGER=cat gh pr view --json number,url -q '"\(.url)"') && echo "$PR"

PAGER=cat gh api graphql -f query='
query($owner: String!, $repo: String!, $pr: Int!) {
  repository(owner: $owner, name: $repo) {
    pullRequest(number: $pr) {
      reviewThreads(first: 100) {
        nodes {
          id
          isResolved
          path
          line
          comments(first: 10) { nodes { author { login } body } }
        }
      }
    }
  }
}' -F owner="$(PAGER=cat gh repo view --json owner -q .owner.login)" \
   -F repo="$(PAGER=cat gh repo view --json name -q .name)" \
   -F pr="$(PAGER=cat gh pr view --json number -q .number)" \
  | jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)] | to_entries | .[] | "\(.key+1). [\(.value.path):\(.value.line // "file")] \(.value.comments.nodes[0].author.login): \(.value.comments.nodes[0].body[:100])..." | "\(.value.id)"'
```

## 2. Evaluate Each Comment

For EACH unresolved thread, ask:

```
┌─────────────────────────────────────────────────────────────┐
│ Is this feedback CORRECT?                                   │
├──────────────┬──────────────────────────────────────────────┤
│ YES, valid   │ → Fix it, then resolve                       │
│ NO, wrong    │ → Don't fix. Note why. Resolve.              │
│ MAYBE        │ → Read the code. Decide. No middle ground.   │
└──────────────┴──────────────────────────────────────────────┘

│ Is this feedback IMPORTANT?                                 │
├──────────────┬──────────────────────────────────────────────┤
│ Bug/security │ → Must fix                                   │
│ Correctness  │ → Should fix                                 │
│ Style/nitpick│ → Fix if trivial, skip if bike-shedding     │
│ Over-eng     │ → Skip. Complexity isn't free.               │
└──────────────┴──────────────────────────────────────────────┘
```

**Red flags for REJECTING feedback:**

- Adds complexity for hypothetical edge cases
- "Consider adding..." without concrete benefit
- Defensive programming for impossible states
- Suggests rewriting working code for style preference
- Reviewer misunderstood the code

**Signs to ACCEPT feedback:**

- Points out actual bug or incorrect behavior
- Security concern with realistic attack vector
- Missing error handling that will cause runtime failure
- Objective improvement (perf, correctness) with evidence

## 3. Resolve Threads

After fixing (or deciding not to fix), resolve each thread:

```bash
PAGER=cat gh api graphql -f query='mutation($id: ID!) { resolveReviewThread(input: {threadId: $id}) { thread { isResolved } } }' -f id="THREAD_ID"
```

## 4. Verify

```bash
PAGER=cat gh pr view --json reviewDecision,reviewRequests -q '"Decision: \(.reviewDecision // "NONE")\nPending: \(.reviewRequests | length)"'
```

---

**Remember:** Your job is to ship correct code, not to satisfy every reviewer comment. Reject bad feedback politely but firmly.
