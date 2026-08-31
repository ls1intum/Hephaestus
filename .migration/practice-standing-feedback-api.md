#### 🔴 The practice-area endpoints are replaced by practice-group endpoints

**Affected**: anything calling the application API directly — scripts, dashboards, or an integration
built against `/practice-areas` or the developer practice list. Deployments that only run the
bundled web client need no changes; it ships updated in this release.

**Before**: practice groupings were served under `/workspaces/{workspaceSlug}/practice-areas`, with
`PracticeArea` schemas and `areaSlug` parameters. A developer's practices came from
`/workspaces/{workspaceSlug}/practices/developer`, and a reaction to delivered feedback was recorded
through its own endpoint.

**After**: the same groupings are served under `/workspaces/{workspaceSlug}/practice-groups`, with
`PracticeGroup` schemas and `groupSlug` parameters — *practice area*, `PracticeArea`, `areaSlug` and
`/practice-areas` are retired names, not synonyms, and no alias remains. The developer practice list
is `/workspaces/{workspaceSlug}/practices/reviewed`. A developer's response to delivered feedback —
whether it was helpful, how it was handled, and an optional explanation — is written through one
combined response endpoint that replaces the earlier reaction endpoint. Every one of these answers
only for the signed-in developer.

**Migration**: update each caller's paths, parameter names and response field names to the group
spelling, move any caller of `/practices/developer` to `/practices/reviewed`, and switch reaction
writes to the combined response endpoint. Response history recorded before the upgrade is preserved
and readable through the new endpoint, so nothing needs re-entering. Regenerate any client
built from `server/openapi.yaml`.
