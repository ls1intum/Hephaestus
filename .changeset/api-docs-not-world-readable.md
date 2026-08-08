---
"hephaestus": minor
---

A production instance no longer publishes its API documentation to the internet. Previously both the
full OpenAPI description and the interactive Swagger UI answered any unauthenticated request, so
anyone who knew the address could read the complete list of routes — including the instance-admin and
workspace-admin ones — and use the built-in "try it out" form against them. The routes themselves
always required a login, but the map of them no longer needs to be public.

**Operators:** no action needed. If you deliberately published the API description — for a client
generator or an internal integration — set `SPRINGDOC_API_DOCS_ENABLED=true` (and
`SPRINGDOC_SWAGGER_UI_ENABLED=true` for the browser UI) to keep it reachable.
