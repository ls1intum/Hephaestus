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
 *   bulk/{connectorId}/{externalId}/   working-tree-shaped artifacts (the git clone is bulk/scm/{repoId})
 *   cas/{ab}/{rest}                    content-addressed blob store (sha-256, fan-out, refcounted)
 *   derived/{connectorId}/...          content-hash-keyed rebuildable views (precompute, indexes)
 *   jobs/{jobId}/                      per-job manifest + blob references for replay / reproducibility
 * </pre>
 *
 * <p>{@link de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout} is the single source of
 * these paths; {@link de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore}
 * generalises the git clone into a connector-agnostic blob cache.
 */
package de.tum.cit.aet.hephaestus.integration.core.fabric;
