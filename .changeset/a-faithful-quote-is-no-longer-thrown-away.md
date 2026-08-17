---
"hephaestus": patch
---

A review no longer discards a finding because the model typed a curly quote. Evidence is checked by
looking for the quoted text in the source it came from, character for character. Models routinely
rewrite straight quotes, dashes and spaces into their typographic equivalents while copying text
faithfully, and every such finding was being rejected and the observation lost. The check now folds
those substitutions before comparing, and only those: a quote that says something the work does not
say still fails, exactly as before.
