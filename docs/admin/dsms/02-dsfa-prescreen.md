---
id: dsfa-prescreen
title: DPIA Pre-Screen
description: Art. 35 GDPR pre-check for the TUM-operated Hephaestus deployment.
---

# Hephaestus — DPIA Pre-Screen (Art. 35 GDPR)

Documents whether a full Data Protection Impact Assessment (Datenschutz-Folgenabschätzung) is required. Pre-screen uses the BayLfD "Muss-/Soll-Liste" for Bavarian public bodies and the DSK lists.

## 1. Threshold check against Art. 35(3) GDPR

| Trigger | Present? | Reason |
|---|---|---|
| Systematic and extensive evaluation based on automated processing, including profiling, with legal or similarly significant effects | **No** | Hephaestus produces advisory Findings and Guidance. They do *not* drive automated decisions with legal or similarly significant effects — the platform does not grade, admit, dismiss, or withhold access from Contributors (§3 of the privacy statement, §16 of the VT). Workspace members consult Findings as informational dashboards; any human decision taken on that basis is the viewer's own under their own process. |
| Large-scale processing of Art. 9 (special categories) or Art. 10 (criminal convictions) data | **No** | No special-category data is processed. Contributors are warned in the privacy statement not to enter third-party personal data into commits / comments / diagrams. |
| Systematic monitoring of a publicly accessible area on a large scale | **No** | Hephaestus does not monitor publicly accessible areas. |

## 2. Check against the BayLfD "Muss-Liste"

| Criterion | Present? | Reasoning |
|---|---|---|
| Vulnerable data subjects on a large scale | **No** | Users are primarily TUM members and invited external open-source contributors. No BayLfD-vulnerable group is processed systematically. |
| Employee performance / behaviour monitoring | **No** | Hephaestus does not observe employment performance. Staff who use the platform as mentors or administrators do so on the same footing as any other Contributor. Leaderboards and league signals are student-facing engagement features, opt-in per workspace, and not used for HR purposes. |
| Innovative technology with unclear DP impact | **Partly (AI-assisted features)** | AI-assisted guidance and automated practice review call an external LLM provider (OpenAI / Azure OpenAI / Anthropic) per workspace. This is a commonly understood class of processing under enterprise API terms with no-training clauses and DPF / SCC safeguards, but falls on the "elevated risk" side of the BayLfD innovative-technology criterion and warrants documented mitigations (see §5 below). |
| Dataset-matching from different sources | **No** | Hephaestus does not cross-match Contributor data with external profiles beyond the federated identity attributes that the IdP itself discloses. |
| Third-country transfer outside adequacy decision | **No** | All U.S. recipients (GitHub, OpenAI, Azure OpenAI, Anthropic, Slack) are DPF-certified; Standard Contractual Clauses under Art. 46(2)(c) GDPR are contracted as a fall-back. |
| Scoring / profiling affecting service access | **No** | Findings are advisory and do not gate access, features, or grades within Hephaestus. |
| Biometric / genetic data | **No** | |
| Surveillance of publicly accessible areas | **No** | |

## 3. Check against the DSK white-list

Activity fits the profile of a "standard university teaching-support platform with authenticated federated login and an external LLM integration". The DSK does not require a DPIA for this profile *per se*, provided the AI surface is bounded by enterprise no-training terms, the shared-responsibility model is documented, and the data-subject rights — including the Art. 21 objection against AI-assisted processing — are surfaced in the privacy statement. All three conditions are satisfied (privacy §3.2, §4.4, §9).

## 4. Residual risk analysis

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Free-text Artifact content (PR descriptions, commit messages, review comments) contains personal data of identifiable third parties and is transmitted to the LLM provider | Low-medium | Low-medium | Privacy notice warns Contributors not to enter third-party personal data; practice-review sandbox runs on an internal Docker network with no outbound connectivity except a per-job LLM proxy; only the diff and relevant context — not the full user profile — is transmitted; Contributors have an Art. 21 opt-out in their profile settings. |
| LLM provider retains the prompt beyond the enterprise default retention window | Low | Low-medium | Enterprise API terms with no-training clauses; default 30-day abuse-monitoring retention; Zero Data Retention can be negotiated per workspace; DPF + SCCs in place. |
| Server access logs retain IP addresses | Low | Low | Hard 14-day maximum server-log retention; logs are not merged with other sources; only AET operators have access. |
| gitlab.lrz.de content leaks via the LRZ integration | Low | Low | LRZ is a separate controller under § 16 BayHIG public-body cooperation — this is not an Art. 28 processor chain; LRZ applies its own TOMs on its own infrastructure; LRZ privacy notice and GitLab terms of use are linked from the Hephaestus privacy statement. |
| Workspace administrator routes personal data through an unexpected channel (Slack digest containing leaderboard snippets, for example) | Low | Low | Shared-responsibility model is documented in §3.2 of the privacy statement; workspace administrators are named as joint controllers for the six decisions in §3.2; Contributors can see the workspace-level configuration and raise Art. 21 objections. |
| Keycloak compromise exposes federated identity tokens | Low | Medium | Self-hosted Keycloak on AET infrastructure; confidential client; TLS-only ingress; regular patching; incident-response under TUM DPO oversight. |

## 5. Required additional measures for the "DPIA-light" posture

In lieu of a full DPIA, the following mitigations are documented and must remain in place:

- **No-training enterprise API terms** for every configured LLM provider. Regressing to a consumer tier is a material change and triggers an amended VT + full DPIA.
- **Per-job LLM proxy** enforced by the practice-review sandbox. DNS disabled inside the sandbox; outbound traffic limited to a per-job, token-authenticated proxy. Any widening of this network posture is a material change.
- **Art. 21 objection switch** in Contributor profile settings ("AI review comments"). Disabling or hiding the switch is a material change.
- **Workspace-administrator joint-controllership notice** in the privacy statement (§3.2). Structural changes to the shared-responsibility split trigger an amended VT.
- **Hard 14-day cap** on server-log retention. Raising the cap is a material change.

## 6. Conclusion

**No full DPIA required at the current scope.** Hephaestus operates within the DSK white-list profile with documented mitigations for the elevated-risk AI surface. Submit the VT with the DPIA-light attachments (`02-dsfa-prescreen.md`, `04-toms.md`, `05-avv-checklist.md`) and without a full DPIA template.

If the DSB disagrees, or if any of the material conditions in §5 changes, upgrade to the BayLfD DPIA template and expand the residual-risk section with concrete technical evidence (pen-test report, dependency-scan summary, LLM-provider DPA copies).
