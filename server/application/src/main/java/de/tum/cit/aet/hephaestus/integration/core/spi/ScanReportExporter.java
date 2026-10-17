package de.tum.cit.aet.hephaestus.integration.core.spi;

/** Serializes neutral report facts; the report owner retains comparison and identity policy. */
public interface ScanReportExporter {
    byte[] export(ScanExport report);
}
