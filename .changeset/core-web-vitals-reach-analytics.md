---
"hephaestus": minor
---

Instances that run PostHog now receive Core Web Vitals — largest contentful paint, cumulative layout
shift, first contentful paint and interaction to next paint — for the web application, so a slow
page is visible in analytics rather than only in a complaint. The application never measured them
before. Nothing is captured without the analytics consent that already gates every other event, and
network request timing stays off.
