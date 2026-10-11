## How to do it

- Before you ask for a review, split the change where your reviewer would start to skim.
- Open each part that a teammate can review and merge without the rest as its own change. For example, instead of one change that adds CSV export, its settings page and its emails, open the export first. Then open the settings page and the emails as smaller changes on top of it.
- Say in the description which files are lockfile, generated or vendored updates, or put them in their own change. Then the reviewer can see what needs a careful read.
- Do the groundwork first. Move or rename code in its own change, so the next change shows only the new behavior.
- If the new code replaces old code, remove the old code in its own change, after nothing uses it.

![Three changes follow in order. The first only moves or renames code, the second adds only the new behavior, and the third removes old code after nothing uses it.](figures/split-order.svg)

## When it does not apply

- The change has nothing to review.
- The change holds only generated or vendored files that a tool can produce again, with nothing in them to review.

It still applies when a migration, a snapshot or a generated contract changes what the software does.

## Common mistakes

- Looking for a line or file limit. There is none. Instead, look at the work your reviewer must read with care. A long change that does one simple thing can be easy to read. A short change spread across unrelated areas can be hard to read.
- Splitting work into changes that cannot be read alone. Instead, make each change self-contained, so it makes sense by itself and keeps the build working.

## Sources

- [Small CLs](https://google.github.io/eng-practices/review/developer/small-cls.html), Google Engineering Practices. Google advises small, self-contained changes, with refactorings in their own change and no hard size limit.
- [Modern code review: a case study at Google](https://doi.org/10.1145/3183519.3183525), Sadowski and others, 2018. Most changes at Google are small and get reviewed quickly. Google sells developer tools.
- [Characteristics of useful code reviews: an empirical study at Microsoft](https://www.microsoft.com/en-us/research/publication/characteristics-of-useful-code-reviews-an-empirical-study-at-microsoft/), Bosu, Greiler and Bird, 2015. The more files a change touched, the smaller the share of its review comments that helped its author. Microsoft sells code review tools.
- [Parallel change](https://martinfowler.com/bliki/ParallelChange.html), Danilo Sato, 2014. Replace code in steps, and remove the old version only after every caller uses the new one.
