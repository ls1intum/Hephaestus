#### 🔴 Update practice comparison API clients

The workspace comparison again counts developers by standing. Its tiles show the selected time window, the current developer's figures and the workspace's typical range.

Update clients that consume `/practices/workspace-overview` or `/practices/tiles` to the response schemas in this release's OpenAPI specification. The web app includes the generated client for these schemas.

Feedback mode controls delivery. A practice reviewed by Hephaestus remains eligible for standing when feedback is off. A practice reviewed by People or Nobody is excluded from these standings.
