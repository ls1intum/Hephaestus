---
"hephaestus": patch
---

When a practice review's AI model sends its result in a form that Hephaestus does not accept, the correction it gets back now contains only the result fields that Hephaestus reads. It also names the facts that the model must copy from its original answer. Before, the correction could repeat the field that was not accepted, so a corrected result could still be refused.
