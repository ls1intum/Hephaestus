package de.tum.cit.aet.hephaestus.core.auth.export.dto;

/**
 * Small acknowledgement body returned by {@code POST /user/exports} alongside the
 * {@code 202 Accepted} + {@code Location} header. The client polls the status endpoint next.
 */
public record ExportCreatedDTO(Long id, String status) {}
