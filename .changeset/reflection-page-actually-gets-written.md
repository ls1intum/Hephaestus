---
"hephaestus": patch
---

Your reflection page now receives the messages a review composed for it. A review that ran long enough
to be told to start writing things down was treated as one that had run out of time, and the composing
step was skipped — which, at any normal timeout, meant almost every review. Composing now goes ahead
whenever the review finished with enough of its allowance left over to write, and a review's own retry
time is still never spent on it.
