---
"hephaestus": patch
---

Practice-detection runs now record exactly how each result was produced — the model and prompt version
that ran, and a fingerprint of the inputs the detector actually saw — and every piece of feedback the
system prepares is now logged as either delivered or withheld with a reason. This makes the evaluation
data gathered during the shadow-review phase trustworthy: a result can always be traced back to what
generated it, and a developer's reaction to feedback can be read as a signal, because "seen and ignored"
is now distinguishable from "held back by policy". The accompanying database migration adds two columns
and tidies the set of recorded withholding reasons; it applies automatically, with no action required on
upgrade.
