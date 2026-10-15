package de.tum.cit.aet.hephaestus.mentor.adapter;

import de.tum.cit.aet.hephaestus.core.privacy.spi.JdbcPersonDataStore;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataSelection;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonScope;
import de.tum.cit.aet.hephaestus.integration.core.framework.MonitoredRepositoryScope;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Clears the hidden runtime journals of every conversation in each workspace that holds the person's
 * data. Heph can read members, conversations, documents and authored work into another member's
 * journal, which has no per-person provenance to split it by. The selection freezes workspaces, not
 * threads, so journals written after the preview are cleared too and active conversations do not
 * invalidate it. Journals are runtime memory, not the person's record, and are never exported.
 * Erasure leaves an empty journal rather than none: the next turn of each thread discards any warm
 * runtime that still holds the old session, and a turn already running cannot store it back.
 */
final class MentorRuntimeJournalPersonDataStore extends JdbcPersonDataStore {
    private static final String PERSON_WORKSPACES = """
        SELECT workspace_id FROM membership WHERE account_id = :account
        UNION SELECT workspace_id FROM slack_thread WHERE id = ANY(:conversations)
        UNION SELECT workspace_id FROM outline_document WHERE id = ANY(:documents)
        UNION SELECT rtm.workspace_id FROM repository_to_monitor rtm
            JOIN repository r ON r.name_with_owner = rtm.name_with_owner
                AND %s
            JOIN issue i ON i.repository_id = r.id
            WHERE i.id = ANY(:items)
        UNION SELECT workspace_id FROM review_job WHERE id = ANY(:derivedJobs)
        """.formatted(MonitoredRepositoryScope.NATIVE_RTM_R);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    MentorRuntimeJournalPersonDataStore(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        super(
                jdbc,
                mapper,
                "chat_thread_runtime_journal",
                "chat_thread",
                "FALSE",
                "workspace_id",
                "workspace_id",
                "session_jsonl=''::bytea",
                -243);
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public PersonDataSelection select(PersonScope person) {
        return new PersonDataSelection(jdbc.query(
                """
                SELECT DISTINCT workspace_id FROM chat_thread
                WHERE length(session_jsonl) > 0 AND (account_id IS NULL OR account_id <> :account)
                  AND workspace_id IN (%s)
                ORDER BY workspace_id
                """.formatted(PERSON_WORKSPACES),
                parameters(person, mapper),
                (rs, row) -> new PersonDataSelection.RowKey(Map.of("workspace_id", Long.toString(rs.getLong(1))))));
    }

    @Override
    public List<JsonNode> export(PersonDataSelection selection, PersonScope frozenScope) {
        return List.of();
    }

    /** Counts workspaces, matching the preview. */
    @Override
    public long erase(PersonDataSelection selection, PersonScope frozenScope) {
        super.erase(selection, frozenScope);
        return selection.rows().size();
    }
}
