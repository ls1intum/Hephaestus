---
"hephaestus": minor
---

A run that is waiting on a spend cap now says so. Jobs held because their monthly LLM cap is
exhausted used to be indistinguishable from jobs waiting for a free worker, both in the API and on
the queue metrics — a capped workspace read as a queue of depth zero, exactly like an idle one. Each
job now reports when it becomes eligible to run and, when an admin can release it, why it is waiting;
and a new `agent.queue.held` metric counts the jobs parked on a cap, so a paused instance can be
alerted on instead of looking healthy. Raising the cap still releases them by itself.

Practices → **Runs** shows it too. A held run reads "Held · Over the AI budget · due in …" beneath
its status, and a run backing off after a crash says when it will be tried again; every other run
looks exactly as it did, because it is claimable right now and has nothing to wait for. Opening a
held run adds an **On hold** note: the run is parked rather than failed, it resumes on its own once
the cap is raised or the month rolls over, and AI usage names which purse is capped.
