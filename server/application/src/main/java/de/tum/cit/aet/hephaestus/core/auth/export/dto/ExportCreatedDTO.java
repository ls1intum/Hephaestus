package de.tum.cit.aet.hephaestus.core.auth.export.dto;

/** Acknowledgement returned when a data export is requested. */
public record ExportCreatedDTO(Long id, String status) {}
