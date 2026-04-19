---
id: dsfa-prescreen
title: DPIA Pre-Screen
description: Art. 35 GDPR pre-check for the TUM-operated Hephaestus deployment.
---

*Last updated: 2026-04-19.*

Documents whether a full Data Protection Impact Assessment (Datenschutz-Folgenabschätzung) is required. The pre-screen applies the threshold in Art. 35(3) GDPR, the BayLfD "Muss-Liste" for Bavarian public bodies, and the DSK white-list.

## 1. Threshold check against Art. 35(3) GDPR

| Trigger | Present? | Reason |
|---|---|---|
| Systematic and extensive evaluation, including profiling, that forms the basis for decisions producing legal effects or similarly significant effects (Art. 35(3)(a)) | **No** | Hephaestus produces advisory Findings and Guidance delivered to the Contributor and visible to other workspace members (peers, workspace administrators, team leads, teaching staff) on dashboards and per-Artifact views. They are *not* consumed by any automated grading, assessment, HR, or access-control pipeline operated by Hephaestus; the platform does not grade, admit, dismiss, or withhold access from Contributors. The processing is not "systematic and extensive" in the EDPB WP248 sense — coverage is workspace-scoped, practice-scoped, and defined by the workspace administrator, not exhaustively profiling individuals across a large population. §3 and §4.4 of the privacy statement and §16 of the VVT are the authoritative record. |
| Large-scale processing of Art. 9 (special categories) or Art. 10 (criminal convictions) data | **No** | No special-category data is processed. Contributors are warned in the privacy statement not to enter third-party personal data into commits, reviews, or practice-review diffs. |
| Systematic monitoring of a publicly accessible area on a large scale | **No** | Hephaestus does not monitor publicly accessible areas. |

None of the three Art. 35(3) triggers is present, so the GDPR's hard "mandatory DPIA" threshold is not met. The residual elevated risk on the AI surface is handled through §5 below ("DPIA-light").

## 2. Check against the BayLfD "Muss-Liste"

| Criterion | Present? | Reasoning |
|---|---|---|
| Vulnerable data subjects on a large scale | **No** | Users are primarily TUM members and invited external open-source contributors. No BayLfD-vulnerable group is processed systematically. |
| Employee performance / behaviour monitoring | **No** | Hephaestus does not observe employment performance. Staff who use the platform as mentors or administrators do so on the same footing as any other Contributor. Leaderboards and league signals are student-facing engagement features, opt-in per workspace, and not used for HR purposes. |
| Innovative technology with unclear DP impact | **Partly (AI-assisted features)** | AI-assisted guidance and automated practice review call an external LLM provider (OpenAI / Azure OpenAI / Anthropic) per workspace. This is a commonly understood class of processing under enterprise API terms with no-training clauses and DPF / SCC safeguards, but falls on the "elevated risk" side of the BayLfD innovative-technology criterion and warrants documented mitigations (see §5 below). |
| Dataset-matching from different sources | **No** | Hephaestus does not cross-match Contributor data with external profiles beyond the federated identity attributes that the IdP itself discloses. |
| Third-country transfer outside adequacy decision | **No** | All U.S. recipients (GitHub, OpenAI, Azure OpenAI, Anthropic, Slack, PostHog when deployment-enabled) are DPF-certified; Standard Contractual Clauses Module 2 under Art. 46(2)(c) GDPR are contracted as a fall-back. |
| Scoring / profiling affecting service access | **No** | Findings are advisory and do not gate access, features, or grades within Hephaestus. |
| Biometric / genetic data | **No** | |
| Surveillance of publicly accessible areas | **No** | |

## 3. Check against the DSK white-list

Activity fits the profile of a "standard university teaching-support platform with authenticated federated login and an external LLM integration". The DSK does not require a DPIA for this profile *per se*, provided the AI surface is bounded by enterprise no-training terms, the shared-responsibility model is documented, and the data-subject rights — including the Art. 21 objection against AI-assisted processing — are surfaced in the privacy statement. All three conditions are satisfied (privacy §3.2, §4.4, §9).

## 4. Residual risk analysis

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Free-text Artifact content (PR descriptions, commit messages, review comments) contains personal data of identifiable third parties and is transmitted to the LLM provider | Low-medium | Low-medium | Privacy notice warns Contributors not to enter third-party personal data; practice-review sandbox runs on an internal Docker network with no outbound connectivity except a per-job LLM proxy; only the diff and relevant context — not the full user profile — is transmitted; Contributors have an Art. 21 objection switch in their profile settings ("AI review comments"). |
| LLM provider retains the prompt beyond the enterprise default retention window | Low | Low-medium | Enterprise API terms with no-training clauses; default 30-day abuse-monitoring retention; Zero Data Retention can be negotiated per workspace; DPF + SCCs Module 2 in place. |
| Server access logs retain IP addresses | Low | Low | Hard 14-day maximum enforced by `logrotate` and the Docker `json-file` log driver; logs are not merged with other sources; only AET operators have access. |
| Self-hosted Sentry retains error events tied to the Contributor's Keycloak user ID and IP (`sendDefaultPii: true`) | Low | Low | Sentry runs on AET infrastructure (`sentry.ase.in.tum.de`) under the AET operational baseline; access is restricted to AET operators; events are retained at the Sentry default of 90 days. |
| PostHog product analytics captures Keycloak user ID, event stream, and IP | Low (only when deployment-enabled) | Low | `POSTHOG_ENABLED=false` by default; when enabled, events are suppressed at the client unless the Contributor has active research-participation consent (`participateInResearch`, opt-out in profile settings). Art. 7(3) withdrawal is honoured immediately. |
| gitlab.lrz.de content leaks via the LRZ integration | Low | Low | LRZ is a separate controller under Art. 16 Abs. 1 Satz 2 BayHIG public-body cooperation — this is not an Art. 28 processor chain; LRZ applies its own TOMs on its own infrastructure; LRZ privacy notice and GitLab terms of use are linked from the Hephaestus privacy statement. |
| Workspace administrator routes personal data through an unexpected channel (Slack digest containing leaderboard snippets, for example) | Low | Low | Shared-responsibility model is documented in §3.2 of the privacy statement; workspace administrators are named as joint controllers for the six decisions in §3.2 with Art. 26(2) duty allocation; Contributors can see the workspace-level configuration and raise Art. 21 objections. |
| Keycloak compromise exposes federated identity tokens | Low | Medium | Self-hosted Keycloak on AET infrastructure; confidential client; TLS-only ingress; regular patching; incident-response under TUM DPO oversight. |

## 5. Required additional measures for the "DPIA-light" posture

In lieu of a full DPIA, the following mitigations are documented and must remain in place. Any material change triggers an amended VVT and a re-assessment under this file:

- **No-training enterprise API terms** for every configured LLM provider. Regressing to a consumer tier is a material change and triggers an amended VVT + full DPIA.
- **Per-job LLM proxy** enforced by the practice-review sandbox. DNS disabled inside the sandbox; outbound traffic limited to a per-job, token-authenticated proxy. Any widening of this network posture is a material change.
- **Art. 21 objection switch** in Contributor profile settings ("AI review comments"). Disabling or hiding the switch is a material change.
- **Research-consent switch** ("Participate in research") gating PostHog product analytics. Removing the switch, defaulting analytics to *active without an operational consent check*, or forwarding events without the switch being honoured at the client is a material change.
- **Workspace-administrator joint-controllership notice** in the privacy statement (§3.2). Structural changes to the shared-responsibility split trigger an amended VVT.
- **Hard 14-day cap** on server-log retention via `logrotate` + Docker `json-file` log driver. Raising the cap is a material change.
- **Self-hosted error telemetry**. Forwarding Sentry events to a third-party SaaS would convert a TUM-internal recipient into an Art. 28 processor in the U.S. and trigger a re-assessment.

## 6. Conclusion

**No full DPIA required at the current scope.** Hephaestus operates within the DSK white-list profile with documented mitigations for the elevated-risk AI surface. Submit the VVT with the DPIA-light attachments (`02-dsfa-prescreen.md`, `04-toms.md`, `05-avv-checklist.md`) without a full DPIA template.

A full DPIA must be opened before any of the following takes effect:

- LLM provider is added, changed to a consumer tier, or loses its no-training commitment.
- Practice-review sandbox gains outbound connectivity beyond the per-job LLM proxy.
- Findings begin to drive any automated decision within Hephaestus (grading, recognition caps, feature access).
- PostHog is enabled without the research-consent gate, or a third-party Sentry replaces the self-hosted one.
- The processing population broadens beyond TUM members and invited external contributors into a category covered by the BayLfD "vulnerable data subjects" criterion.

If the DSB requests a full DPIA, upgrade to the BayLfD DPIA template and expand the residual-risk section with concrete technical evidence (pen-test report, dependency-scan summary, LLM-provider DPA copies, Sentry retention report).
