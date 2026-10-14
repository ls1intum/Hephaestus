package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class ConfigAuditEntityTypeConverterTest extends BaseUnitTest {

    private final ConfigAuditEntityTypeConverter converter = new ConfigAuditEntityTypeConverter();

    @Test
    void readsHistoricalPracticeGroupNames() {
        assertThat(converter.convertToEntityAttribute("PRACTICE_AREA")).isEqualTo(ConfigAuditEntityType.PRACTICE_GROUP);
        assertThat(converter.convertToEntityAttribute("CATALOG_PRACTICE_AREA"))
                .isEqualTo(ConfigAuditEntityType.CATALOG_GROUP);
    }

    @Test
    void writesCurrentNames() {
        assertThat(converter.convertToDatabaseColumn(ConfigAuditEntityType.PRACTICE_GROUP))
                .isEqualTo("PRACTICE_GROUP");
    }
}
