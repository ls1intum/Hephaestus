#### 🔴 Audit text and native Hibernate JSON cutover

**Operators:** Schedule an outage for every replica of `server`, `worker`, and `webhook`.
Stop all runtime roles, external database writers, and scheduled database maintenance.
Verify restoration of a complete database backup before the migration.

The migration converts authentication details, both configuration snapshots, GitHub project options,
and connection audit detail from JSONB to TEXT.
It takes exclusive locks and rewrites tables, authentication partitions, and the configured pg_partman template.
Typed JSON data remains JSONB.
Unexpected source types or partition/template states stop the migration.

Apply the migration once while all writers remain stopped.
Confirm migration completion and future partition creation before restarting all runtime roles on the same new release.

Direct person-data exports return these TEXT values as JSON Strings.
An old JSONB String keeps its decoded String value.
An old structured object or array becomes a String that contains canonical JSON text.
This includes connection audit detail and configuration snapshots in membership-subject exports.
JSON null becomes a null value.
The JSON String "null" remains the literal String "null".

Check consumers that require nested export values.

Downgrade requires restoration of the verified backup from before this cutover.
Do not start the previous binary against the converted database.
Liquibase rollback stops because TEXT cannot recover the original JSONB representation.

Follow the complete [operator procedure](https://docs.hephaestus.build/admin/native-hibernate-json-cutover).
