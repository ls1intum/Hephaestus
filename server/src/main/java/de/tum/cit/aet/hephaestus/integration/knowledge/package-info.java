/**
 * Knowledge family library.
 *
 * <p>Family-shared abstractions for knowledge-base vendors (Outline, future Confluence / Notion).
 * Includes {@code KnowledgeFeedbackChannel}, sealed {@code DocumentAnchor}, {@code KnowledgeDomainEvent},
 * {@code DocumentAccessProbe} (per-page permission probe for Notion granular access),
 * and thin {@code DocumentRef} entity.
 *
 * <p>{@code DocumentRef} stores identity only (id + last revision). Document content is fetched
 * on demand from the vendor API — large/versioned ProseMirror content is not duplicated.
 */
@org.springframework.modulith.NamedInterface({"api", "events"})
package de.tum.cit.aet.hephaestus.integration.knowledge;
