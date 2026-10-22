---
"hephaestus": patch
---

In a Slack thread with Heph, Slack now shows that Heph works, with a **Stop** control that stops only the answer in progress. It works also before Heph writes its first words, and questions that wait after it keep their place. Heph stops soon after, not always at once. To show the control, apply the current Slack app manifest, which subscribes the app to `agent_session_stopped` and needs no new OAuth scope. Until then, developers stop Heph in the app as before.
