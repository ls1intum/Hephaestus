package de.tum.cit.aet.hephaestus.config;

import liquibase.change.core.LoadDataChange;
import liquibase.database.Database;
import liquibase.database.core.PostgresDatabase;
import liquibase.datatype.DataTypeInfo;
import liquibase.datatype.LiquibaseDataType;
import liquibase.datatype.core.ClobType;
import org.jspecify.annotations.Nullable;

@DataTypeInfo(
        name = "jsonb",
        aliases = {},
        minParameters = 0,
        maxParameters = 0,
        priority = LiquibaseDataType.PRIORITY_DATABASE)
public class JsonbDataType extends LiquibaseDataType {

    @Override
    public boolean supports(Database database) {
        return database instanceof PostgresDatabase || "hibernateSpringPackage".equals(database.getShortName());
    }

    @Override
    public @Nullable String objectToSql(@Nullable Object value, Database database) {
        // Inheritance would make Liquibase classify JSONB metadata as text.
        return new ClobType().objectToSql(value, database);
    }

    @Override
    public LoadDataChange.LOAD_DATA_TYPE getLoadTypeName() {
        return LoadDataChange.LOAD_DATA_TYPE.STRING;
    }
}
