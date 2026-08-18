---
"hephaestus": patch
---

Silent Mode no longer holds back the feedback a developer reads inside Hephaestus. Silencing an
instance is meant to stop Hephaestus writing anywhere outside it — comments on merge requests,
messages in chat. It was also stopping the private, longer-term feedback on a developer's own
practice pages, which never leaves the instance at all. A recovery pass picked those up within the
hour, so they arrived late rather than never; they now arrive with the review that produced them.
What Silent Mode stops is unchanged: nothing is posted on the work, and nothing is said in chat.
