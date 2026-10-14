package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.core.privacy.spi.JdbcPersonDataStore;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataSelection;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportService;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

final class ScanReportReferencePersonDataStore extends JdbcPersonDataStore {
    private final ScanReportService reports;

    ScanReportReferencePersonDataStore(
            ScanReportService reports,
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper,
            String store,
            String table,
            String predicate,
            String columns,
            String keys,
            String redaction,
            int order) {
        super(jdbc, mapper, store, table, predicate, columns, keys, redaction, order);
        this.reports = reports;
    }

    @Override
    public long erase(PersonDataSelection selection) {
        var rows = querySelected(
                "jsonb_build_object('workspaceId',t.workspace_id,'resultId',t.result_id)::text",
                "review_item t",
                selection);
        var byWorkspace = rows.stream()
                .filter(row -> !row.path("resultId").isNull())
                .collect(Collectors.groupingBy(row -> row.path("workspaceId").longValue()));
        long erased = 0;
        for (var entry : byWorkspace.entrySet())
            erased += reports.eraseResultReferences(
                    entry.getKey(),
                    entry.getValue().stream()
                            .map(row -> UUID.fromString(row.path("resultId").asString()))
                            .distinct()
                            .toList());
        return erased + super.erase(selection);
    }
}
