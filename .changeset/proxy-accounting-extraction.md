---
---

No user-facing or operator-facing effect. The LLM proxy's budget check, per-call token accounting and
request metrics now sit behind one collaborator instead of being wired into the proxy endpoint
individually. The behaviour they implement is unchanged and is described where it belongs — in the
notes for the spending cap and the per-call accounting themselves.
