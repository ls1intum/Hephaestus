---
"hephaestus": patch
---

The startup error for a wrong-length encryption key now says which length is wrong. It reported only
bytes, so an operator who had pasted exactly 32 characters — with one accent or umlaut among them,
which costs more than one byte — was told to produce a 32-byte key while looking at what they
already believed was one. The message now gives both counts, says which of the two is the problem,
and repeats the command that generates a valid key.
