# Hephaestus — DPIA Pre-Screen (Art. 35 GDPR)

_Last updated: 2026-05-05._

Records whether a full Data Protection Impact Assessment is required for the TUM-operated Hephaestus deployment. Three independent gates are checked: Art. 35(3) GDPR, the BayLfD Muss-Liste, and the DSK Muss-Liste.

## 1. Threshold check against Art. 35(3) GDPR

| Trigger | Present? | Reasoning |
|---|---|---|
| Systematic and extensive evaluation, including profiling, that forms the basis for decisions producing legal effects or similarly significant effects (Art. 35(3)(a)) | **No** | Hephaestus produces advisory findings and guidance delivered to the contributor and visible to other workspace members on dashboards and per-artefact views. They are not consumed by any automated grading, assessment, HR, or access-control pipeline operated by Hephaestus. The processing is workspace-scoped and practice-scoped (defined per workspace by the workspace administrator), not exhaustively profiling individuals across a large population. |
| Large-scale processing of Art. 9(1) (special categories) or Art. 10 (criminal convictions) data | **No** | No special-category data is processed. Contributors are warned in the privacy statement not to enter third-party personal data into commits, reviews, or practice-review diffs. |
| Systematic monitoring of a publicly accessible area on a large scale | **No** | Hephaestus does not monitor publicly accessible areas. |

None of the three Art. 35(3) triggers is present.

## 2. BayLfD Muss-Liste

| Criterion | Present? | Reasoning |
|---|---|---|
| Vulnerable data subjects on a large scale | **No** | Users are primarily TUM members and invited external open-source contributors. No BayLfD-vulnerable group is processed systematically. |
| Employee performance / behaviour monitoring | **No** | Art. 75a BayPVG's _Eignungsrechtsprechung_ is the capability test for monitoring employee behaviour or performance. Hephaestus is _suitable_ for displaying contributor activity but is not deployed as a personnel-evaluation, performance-management, or HR-consuming instrument; staff appear as contributors on the same footing as students. The platform exposes no Dienststelle-segmented dashboard, no HR export, and no manager-facing roll-up. |
| Innovative technology with unclear DP impact | **Partly (AI-assisted features)** | AI-assisted guidance and automated practice review call an external LLM provider per workspace. This is a commonly understood class of processing under enterprise no-training terms with DPF / SCC safeguards, but falls on the elevated-risk side of the BayLfD innovative-technology criterion and warrants the documented mitigations in §5 below. |
| Dataset-matching from different sources | **No** | Hephaestus does not cross-match contributor data with external profiles beyond the federated identity attributes the IdP itself discloses. |
| Third-country transfer outside adequacy decision | **No** | All U.S. recipients are DPF-certified; SCCs Module 2 contracted as fall-back. |
| Scoring / profiling affecting service access | **No** | Findings are advisory and do not gate access, features, or grades within Hephaestus. |
| Biometric / genetic data | **No** | |
| Surveillance of publicly accessible areas | **No** | |

## 3. DSK Muss-Liste

The processing fits the profile of a "standard university teaching-support platform with authenticated federated login and an external LLM integration". The DSK Muss-Liste does not require a DPIA for this profile, provided the AI surface is bounded by enterprise no-training terms, the joint-controller model is documented, and the data-subject rights — including the Art. 21 objection against AI-assisted processing — are surfaced in the privacy statement. All three conditions are met (privacy statement §3.2, §4.4, §9). The absent DSK Positivliste is not a positive finding for either side.

## 4. Residual risk analysis

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Free-text artefact content (PR descriptions, commit messages, review comments) contains personal data of identifiable third parties and is transmitted to the LLM provider | Low-medium | Low-medium | Privacy statement warns contributors not to enter third-party personal data; practice-review sandbox runs on a per-job `--internal` Docker network with no general egress except a per-job, token-authenticated LLM proxy; only the diff and surrounding discussion is forwarded; contributors have an Art. 21 objection toggle ("AI review comments"). |
| LLM provider retains the prompt beyond the enterprise default retention window | Low | Low-medium | Enterprise no-training terms; default 30-day abuse-monitoring retention; Zero Data Retention can be negotiated per workspace where the provider supports it; DPF / SCCs Module 2 in place. |
| Server access logs retain IP addresses | Low | Low | Tomcat AccessLogValve deletes entries after 14 days; pattern minimised to timestamp, IP, method, path, protocol, status, response size, processing time. |
| gitlab.lrz.de content leaks via the LRZ integration | Low | Low | LRZ is a separate controller; inter-public-body transmission under Art. 5 Abs. 1 Nr. 1 BayDSG; LRZ applies its own TOMs on its own infrastructure. |
| Workspace administrator routes personal data through an unexpected channel (Slack digest containing leaderboard snippets, for example) | Low | Low | Joint-controller / shared-responsibility model documented in privacy §3.2; the administrator invokes the lawful basis for the workspace and accepts the corresponding duties; contributors can see the configuration and raise Art. 21 objections. |
| Keycloak compromise exposes federated identity tokens | Low | Medium | Self-hosted on AET infrastructure; confidential client; TLS-only ingress; regular patching; incident response under TUM DPO oversight. |

## 5. DPIA-light mitigations that must remain in place

- **No-training enterprise API terms** for every LLM provider configured by the AET-pool. Regressing to a consumer tier is a material change.
- **Per-job LLM proxy** enforced by the practice-review sandbox. DNS disabled inside the sandbox; outbound traffic limited to a per-job, token-authenticated proxy. Any widening of this network posture is a material change.
- **Art. 21 objection switch** in contributor profile settings ("AI review comments"). Disabling or hiding it is a material change.
- **Workspace-administrator joint-controller notice** in the privacy statement (§3.2). Structural changes to the shared-responsibility split require an amended record.
- **Bounded server-log retention** via Tomcat's native 14-day retention with the minimised pattern. Extending the retention window or widening the logged fields is a material change.
- **Error telemetry and product analytics remain disabled.** The webapp ships a Sentry integration and a PostHog integration, both disabled in the current production deployment. Activating either is a material change.

## 6. Conclusion

**No full DPIA required at the current scope.** Hephaestus operates within the DSK Muss-Liste profile with documented mitigations for the elevated-risk AI surface.

A full DPIA must be opened before any of the following takes effect:

- LLM provider is added, changed to a consumer tier, or loses its no-training commitment.
- Practice-review sandbox gains outbound connectivity beyond the per-job LLM proxy.
- Findings begin to drive any automated decision within Hephaestus (grading, recognition caps, feature access).
- The bundled Sentry integration is activated against a SaaS tenant, or the bundled PostHog integration is activated.
- The processing population broadens beyond TUM members and invited external contributors into a category covered by the BayLfD vulnerable-data-subjects criterion.
