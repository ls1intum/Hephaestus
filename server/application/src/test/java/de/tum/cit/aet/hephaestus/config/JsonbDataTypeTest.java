package de.tum.cit.aet.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import liquibase.database.Database;
import liquibase.database.core.PostgresDatabase;
import liquibase.datatype.DataTypeFactory;
import liquibase.statement.DatabaseFunction;
import liquibase.structure.core.DataType;
import liquibase.util.SqlUtil;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
class JsonbDataTypeTest {

    @Test
    void shouldPreserveNativeTypeAndDefaultWhenReadingPostgres() {
        assertJsonbDefault(new PostgresDatabase());
    }

    @Test
    void shouldPreserveNativeTypeAndDefaultWhenReadingHibernateMetadata() {
        Database database = mock(Database.class);
        when(database.getShortName()).thenReturn("hibernateSpringPackage");
        assertJsonbDefault(database);
    }

    private static Stream<Arguments> defaults() {
        return Stream.of(
                Arguments.of("[]", "'[]'"),
                Arguments.of("{}", "'{}'"),
                Arguments.of("{\"author\":\"O'Hara\"}", "'{\"author\":\"O''Hara\"}'"),
                Arguments.of(new DatabaseFunction("'[]'::jsonb"), "'[]'::jsonb"));
    }

    @ParameterizedTest
    @MethodSource("defaults")
    void shouldRenderJsonDefaultsAsLiteralsAndComputedDefaultsAsSql(Object value, String sql) {
        var database = new PostgresDatabase();
        var resolved = DataTypeFactory.getInstance().fromDescription("jsonb", database);
        assertThat(resolved.objectToSql(value, database)).isEqualTo(sql);
    }

    private void assertJsonbDefault(Database database) {
        DataType type = new DataType("jsonb");
        type.setDataTypeId(SqlTypes.JSON);
        var resolved = DataTypeFactory.getInstance().from(type, database);

        assertThat(resolved).isInstanceOf(JsonbDataType.class);
        assertThat(resolved.toDatabaseDataType(database).toString()).isEqualTo("JSONB");
        assertThat(SqlUtil.parseValue(database, "'{}'::jsonb", type)).isEqualTo(new DatabaseFunction("'{}'::jsonb"));
    }
}
