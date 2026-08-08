---
"hephaestus": patch
---

A review backfill now reports the artifacts it could not review, instead of counting them as reviewed
and finishing clean. A campaign that hit an error on an item previously folded it in with the items it
had deliberately walked past, so the totals added up to the estimate and the run announced itself
complete over a baseline with gaps in it. Those items are now counted and reported separately, so a
finished campaign says plainly whether its baseline is whole.

Cancelling a running backfill also no longer fails with a server error when the campaign happens to be
mid-batch at the moment you press the button.
