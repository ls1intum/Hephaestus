---
"hephaestus": patch
---

Fixes several ways the AI console could lose an edit or say something untrue about one.

Saving practice detection and the mentor one after the other without waiting no longer re-enables the first card while its request is still running — which looked idle and accepted a second click. Timeout, concurrency and internet-access edits you have open are also no longer discarded when another admin repoints that purpose at a different model.

On a past month, the amount field in a cap or budget dialog no longer estimates "at today's rate" using that month's frozen rate, and the instance AI usage table now says why the Set budget buttons are absent instead of just leaving a gap. The workspace access dialog can now be closed while its save is in flight, so a provider that accepts the request and never answers no longer traps you in it.

Turning off a provider connection that has a single model reads as one model rather than "all 1 models", and a connection with no models on it turns off without asking you to confirm something that stops nothing.
