/**
 * Practice-finding reactions (ENACTED / DISPUTED / NOT_APPLICABLE) — the developer's response to a finding,
 * the closed-loop research signal. Carries each reaction's {@code finding_fingerprint} (ADR 0021 A2) so it
 * follows one concern across the detector's per-run re-detections. Exposed as a named interface so the
 * {@code agent} delivery layer may read it for reaction-aware re-nag suppression (B2), mirroring the sibling
 * {@code finding} / {@code model} / {@code feedback} interfaces.
 */
@org.springframework.modulith.NamedInterface("reaction")
package de.tum.cit.aet.hephaestus.practices.finding.reaction;
