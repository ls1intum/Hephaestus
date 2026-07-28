---
"hephaestus": patch
---

The public pages now describe what Hephaestus actually does. The landing page leads with mentoring
feedback grounded in your work — the hero shows the mentor's feedback on a pull request instead of a
scoreboard mock, the feature cards describe practice feedback and the mentor rather than leaderboards
and leagues, and the FAQ no longer claims GitHub is the only supported platform (GitLab works too).
The about page states the mission plainly and drops the invented biography. Copy across both pages is
sentence case.

Shared links stop rendering as a bare word: the app now ships a meta description and Open Graph /
Twitter card tags, and the PWA manifest gains a description.

The privacy statement is corrected on two points: the self-service GDPR data export shipped, so it is
no longer described as a roadmap item, and the exported authentication-event scope is enumerated
precisely (including IP address and browser identifier). The cookie section now matches the
consent-gated PostHog and Sentry reality instead of claiming no optional storage exists.

The decorative hero preview is hidden from screen readers, and the illustration on the landing page
has a descriptive alt text.
