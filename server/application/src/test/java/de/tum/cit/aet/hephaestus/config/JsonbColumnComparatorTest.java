package de.tum.cit.aet.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import liquibase.database.core.PostgresDatabase;
import liquibase.diff.ObjectDifferences;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.compare.DatabaseObjectComparatorFactory;
import liquibase.statement.DatabaseFunction;
import liquibase.structure.core.Column;
import liquibase.structure.core.DataType;
import liquibase.structure.core.Table;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class JsonbColumnComparatorTest {

    @Test
    void shouldKeepEqualEmptyObjectsWhenPostgresStripsQuotesAndCast() {
        assertThat(compareDefaults("jsonb", "'{}'::jsonb", "{}").hasDifferences())
                .isFalse();
        assertThat(compareDefaults("jsonb", "'{}'", "{}").hasDifferences()).isFalse();
    }

    @Test
    void shouldReportDifferentJsonbDefaults() {
        assertThat(compareDefaults("jsonb", "'{}'::jsonb", "'{\"enabled\":true}'::jsonb")
                        .hasDifferences())
                .isTrue();
    }

    @Test
    void shouldReportDifferentTextDefaults() {
        assertThat(compareDefaults("varchar", "'{}'", "{}").hasDifferences()).isTrue();
    }

    @Test
    void shouldReportMissingJsonbDefault() {
        assertThat(compareDefaults("jsonb", "'{}'", null).hasDifferences()).isTrue();
    }

    private ObjectDifferences compareDefaults(String type, String reference, @Nullable String actual) {
        Table table = new Table().setName("feedback");
        Column first = new Column("checks")
                .setRelation(table)
                .setType(new DataType(type))
                .setDefaultValue(new DatabaseFunction(reference));
        Column second = new Column("checks")
                .setRelation(table)
                .setType(new DataType(type))
                .setDefaultValue(actual == null ? null : new DatabaseFunction(actual));
        return DatabaseObjectComparatorFactory.getInstance()
                .findDifferences(first, second, new PostgresDatabase(), new CompareControl());
    }
}
