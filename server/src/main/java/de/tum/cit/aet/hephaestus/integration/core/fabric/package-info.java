/**
 * Context Fabric — the host-side cache substrate beneath every integration (ADR 0020).
 *
 * <p>SQL is the source of truth; everything this package manages on disk is a <em>rebuildable
 * cache</em>, deletable at any time and reconstructable from SQL plus the upstream connector. The
 * layout is integration-namespaced so a future Slack/Outline connector slots in beside SCM with no
 * restructuring:
 *
 * <pre>
 * {fabric.root}/
 *   cas/sha256/{ab}/{rest}             content-addressed blob store (sha-256, fan-out, refcounted) — IMMUTABLE
 *   sources/{connectorId}/{externalId} per-connector source materialisation (the SCM git checkout is
 *                                      sources/scm/{repoId}; a future export is sources/slack/…) — MUTABLE, regenerable
 *   jobs/{jobId}/                      per-job manifest + blob references for replay / reproducibility — MUTABLE
 * </pre>
 *
 * <p>Three regions, three lifecycles (the immutable/mutable split every proven content store keeps physical:
 * Git objects-vs-refs, Bazel cas-vs-ac, OCI blobs-vs-index). The region noun is connector-agnostic — "source"
 * subsumes a git checkout, a Slack export, and a docs snapshot — so no connector's native vocabulary leaks here.
 *
 * <p>{@link de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout} is the single source of
 * these paths; {@link de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore}
 * generalises the git clone into a connector-agnostic blob cache.
 */
package de.tum.cit.aet.hephaestus.integration.core.fabric;
