package de.tum.cit.aet.hephaestus.agent.scan;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("hephaestus.scan")
public record ScanProperties(
        @DefaultValue("1000000") int treeEntries,
        @DefaultValue("268435456") long inventoryBytes,
        @DefaultValue("PT5M") Duration enumerationDeadline,
        @DefaultValue("PT24H") Duration scanDeadline) {
    public ScanProperties {
        Objects.requireNonNull(enumerationDeadline);
        Objects.requireNonNull(scanDeadline);
        if (treeEntries <= 0
                || inventoryBytes <= 0
                || enumerationDeadline.isNegative()
                || enumerationDeadline.isZero()
                || scanDeadline.isNegative()
                || scanDeadline.isZero()) throw new IllegalArgumentException("Scan limits must be positive");
    }
}
