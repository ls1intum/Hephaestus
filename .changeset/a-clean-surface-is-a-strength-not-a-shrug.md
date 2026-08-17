---
"hephaestus": minor
---

A review can now tell you that you did something well when what you did well was leave a defect out. Eight practices — error handling, input validation, unsafe crashes, untrusted input, insecure defaults, duplication, function size, leftover debug code — hunt for one specific defect, and until now they had no way to record a clean result. Write sound error handling and the review would file it as "this work had no subject for this practice", which is both false and indistinguishable from "you touched nothing relevant". Those practices now report a clean pass as a strength: the harmful behaviour could have appeared in your change and did not.

The claim is bounded, and the review says so. It rests on the added and changed lines it actually read, and every clean result records what the search did not cover — code outside the change, the callers, whether the logic is correct. Where a practice cannot bound the ground its claim covers, nothing changed: the answer stays "could not be determined" rather than an unearned all-clear. And a review still will not praise a defect-hunting practice for code it merely looked at, because that would be a clean bill of health on logic it cannot run.

Expect noticeably more strengths on code-quality practices and correspondingly fewer "nothing to see here" entries on your reflection page.
