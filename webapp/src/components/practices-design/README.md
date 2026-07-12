# Practice surface design exploration

Three genuinely different design directions for the practice surfaces, built as pure
presentational components with realistic seeded data. No API wiring, no routes. Open
Storybook and compare them side by side under `components/practices-design`.

The previous attempt (`components/practices/**` on the rejected branch) failed on four
counts: detached from activity, text-heavy, not scannable, not cohesive. Every candidate
here was designed against those four failures, on top of one shared visual language
(`shared/`): an icon-and-hue identity per practice area, a four-value status dot/chip, a
directional trend glyph with blame-free copy, and a single artifact deep-link treatment.
No ranks, no scores, no numeric comparison between people anywhere.

## The candidates

### A. Activity-anchored feed (`candidate-a/`)

The developer's home is their own chronological feed of PRs and issues. Practice
observations attach inline to the artifact rows they came from and expand in place to
reasoning plus one next step. A compact area strip on top shows where they stand and
doubles as a feed filter. The mentor gets the same thing through a triage-ordered roster
rail: pick a developer, read their actual work.

### B. Area grid + side panel (`candidate-b/`)

Mentor-first density: a developers-by-areas matrix with icon-only column headers and one
status dot per cell. Thirty developers fit one screen. Clicking an area header filters the
roster, clicking a row opens a Sheet where each practice is one line (name, sparkline,
count, status) that expands to evidence with a deep link. The developer self view is the
same language as area cards: icon, status chip, one line per practice, evidence inside.

### C. Focus queue (`candidate-c/`)

Opinionated selection instead of complete display. The mentor sees a to-do list: one card
per developer who could use support with the why and the linked evidence on the card, and
everyone doing fine as one line each. The developer sees at most three practices to focus
on this cycle, each earned by a concrete artifact and paired with one next step, with
strengths affirmed and everything else one tap away.

## Against the critique

| Criterion | A. Activity feed | B. Area grid | C. Focus queue |
| --- | --- | --- | --- |
| Anchored in activity | Best. Observations live on the artifact rows themselves | Referenced. Every signal deep-links its artifact, one panel away | Strong. Evidence and links sit directly on the focus cards |
| Structure over text | Good. Chips and expand-in-place, but feed prose accumulates | Best. Dots, icons, sparklines; prose only appears on demand | Best for the default view. Three cards, one sentence each |
| Scannable at 30 devs | Weak. Rail scrolls, one developer at a time | Best. Whole roster in one screen, filterable by area | Best for triage. Queue length tracks problems, not team size |
| Cohesive with the app | Yes. Feed idiom matches the profile activity surface | Yes. Card/Sheet/Tooltip idiom matches admin surfaces | Yes. Card idiom, plainest composition of the three |
| Weakest spot | Cross-team area questions need per-developer visits | Most abstract; dots can read as monitoring, not coaching | Depends entirely on the needs-attention heuristic being right |

## Recommendation

Ship **C for developers and B for mentors**, sharing the `shared/` language, and fold A's
one real invention into both: observation chips attached to artifact references.

Reasoning: the two audiences fail differently. Developers do not lack data, they lack a
decision about what to do next, and C is the only candidate that makes that decision while
still deep-linking every claim to real work. Mentors asked for scannability and area
filtering at 6 to 30 people, which is exactly the shape of B's matrix; C's queue alone
would leave a mentor blind to the landscape between problems, so B's sheet becomes the
"see full picture" target that C's queue cards link to. A taken whole is the weakest
overall (it optimizes for browsing history rather than making a decision, and it scales
worst for mentors), but its inline evidence treatment is the best answer to "detached from
activity" and both B and C already reuse it via `shared/ArtifactLink`.

If only one candidate can be built, build B: it is the only one that serves both audiences
acceptably, and C's picker can grow on top of its data later.
