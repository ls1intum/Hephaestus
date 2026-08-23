---
---

No user-facing or operator-facing effect. A stream limit change that outlasted the broker's reply
reported that it had done nothing while the broker went on to apply it. The bounds it mis-reported on
ship in this same release, so no operator ever saw the wrong message; the timeout it adds is named in
the stream-bounds note instead.
