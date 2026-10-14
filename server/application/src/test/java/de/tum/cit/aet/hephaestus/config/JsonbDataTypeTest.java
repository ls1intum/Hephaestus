package de.tum.cit.aet.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import liquibase.database.Database;
import liquibase.database.core.PostgresDatabase;
import liquibase.datatype.DataTypeFactory;
import liquibase.statement.DatabaseFunction;
import liquibase.structure.core.DataType;
import liquibase.util.SqlUtil;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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

    private void assertJsonbDefault(Database database) {
        DataType type = new DataType("jsonb");
        type.setDataTypeId(SqlTypes.JSON);
        var resolved = DataTypeFactory.getInstance().from(type, database);

        assertThat(resolved).isInstanceOf(JsonbDataType.class);
        assertThat(resolved.toDatabaseDataType(database).toString()).isEqualTo("JSONB");
        assertThat(SqlUtil.parseValue(database, "'{}'::jsonb", type)).isEqualTo(new DatabaseFunction("'{}'::jsonb"));
    }
}
