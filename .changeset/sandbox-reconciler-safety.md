---
---

No user-facing or operator-facing effect. The sweep that removes abandoned agent sandboxes read
"the job list cannot be read" as "there are no jobs" and deleted every sandbox in flight, and it
judged a sandbox abandoned the moment it appeared rather than giving a starting one any grace. The
same sweep also removed the network belonging to a live mentor session, because it matched networks
by job and a session is not a job. It now stands down when it cannot see what is running, spares a
sandbox that has only just started, and keeps a network as long as a container still uses it. None
of this reached a release: agent sandboxes have never shipped, so there is no version an operator
can be upgrading from where a review or a mentor session was cut short this way.
