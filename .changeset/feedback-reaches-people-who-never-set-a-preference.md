---
"hephaestus": patch
---

In-context practice feedback now reaches developers who have never opened their Hephaestus settings. Feedback delivery is on by default, but the in-context lane read a missing preferences row as a refusal — and a row is only written once someone visits their account page. Anyone whose account arrived through a repository sync and who never signed in was therefore skipped, and the delivery record said they had opted out of something they were never asked. The Slack lane already treated the same missing row as the default, so the two disagreed about the same person. Both now ask one question with one answer. An explicit opt-out is still honoured everywhere.
