package de.tum.cit.aet.hephaestus.agent.adapter;

import de.tum.cit.aet.hephaestus.agent.mentor.turn.MentorAttemptUsageService;
import de.tum.cit.aet.hephaestus.core.privacy.spi.JdbcPersonDataStore;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCatalog;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataContributor;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataSelection;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataStores;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonScope;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The usage measurements of the person's own Heph turns. The selection freezes the turns themselves, each with its own
 * workspace, whether or not a measurement exists yet: a turn claimed before the preview may register its measurement
 * afterwards. Export and erasure work on the measurements of exactly those turns. Before any store erases, the
 * preparation keeps the spend observed so far, removes the measurements and detaches the turns' ledger rows
 * ({@link MentorAttemptUsageService#forgetTurn}), so a model call that returns later cannot write a turn back into the
 * ledger. A registration that starts after the erasure suppressed the account is refused by the person-data write
 * fence ({@link MentorAttemptUsageService#admit}).
 */
@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
@PersonDataStores({MentorAttemptUsagePersonDataCatalog.STORE})
public class MentorAttemptUsagePersonDataCatalog implements PersonDataCatalog {

    static final String STORE = "mentor_turn_attempt_usage";

    /** The selected turns, each by its id and its own workspace, as a relation. */
    private static final String SELECTED =
            "jsonb_to_recordset(CAST(:keys AS jsonb)) AS s(turn_id uuid, workspace_id bigint)";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final MentorAttemptUsageService attemptUsage;

    @Override
    public List<PersonDataContributor> contributors() {
        return List.of(new AttemptUsageStore());
    }

    private final class AttemptUsageStore implements PersonDataContributor {

        @Override
        public String store() {
            return STORE;
        }

        /** Before the turns (-100): a measurement goes before the turn it belongs to. */
        @Override
        public int getOrder() {
            return -105;
        }

        /** The person's own turns, the scope the ledger store selects for their Heph turns. */
        @Override
        public PersonDataSelection select(PersonScope person) {
            return new PersonDataSelection(jdbc.query(
                    "SELECT mt.id::text AS turn_id, mt.workspace_id::text AS workspace_id FROM mentor_turn mt"
                            + " JOIN mentor_conversation mc ON mc.id = mt.conversation_id"
                            + " AND mc.workspace_id = mt.workspace_id WHERE mc.account_id = :account ORDER BY mt.id",
                    JdbcPersonDataStore.parameters(person, mapper),
                    (rs, row) -> new PersonDataSelection.RowKey(Map.of(
                            "turn_id",
                            Objects.requireNonNull(rs.getString("turn_id")),
                            "workspace_id",
                            Objects.requireNonNull(rs.getString("workspace_id"))))));
        }

        /** One entry per selected turn, with the counts of each of its attempts. */
        @Override
        public List<JsonNode> export(PersonDataSelection selection, PersonScope frozenScope) {
            if (selection.rows().isEmpty()) return List.of();
            return jdbc.query(
                    "SELECT jsonb_build_object('turn_id', s.turn_id, 'workspace_id', s.workspace_id, 'attempts',"
                            + " COALESCE((SELECT jsonb_agg(jsonb_build_object('attempt', u.attempt, 'model', u.model,"
                            + " 'calls', u.calls, 'input_tokens', u.input_tokens, 'output_tokens', u.output_tokens,"
                            + " 'cache_read_tokens', u.cache_read_tokens, 'cache_write_tokens', u.cache_write_tokens,"
                            + " 'reasoning_tokens', u.reasoning_tokens, 'occurred_at', u.occurred_at)"
                            + " ORDER BY u.attempt) FROM mentor_turn_attempt_usage u WHERE u.turn_id = s.turn_id"
                            + " AND u.workspace_id = s.workspace_id), '[]'::jsonb))::text FROM " + SELECTED
                            + " ORDER BY s.turn_id",
                    keys(selection),
                    (rs, row) -> mapper.readTree(Objects.requireNonNull(rs.getString(1))));
        }

        /**
         * Removes the measurements of the selected turns that the preparation left, if any. The unit is the selected
         * turn, so the receipt counts what the preview counted.
         */
        @Override
        public long erase(PersonDataSelection selection, PersonScope frozenScope) {
            if (selection.rows().isEmpty()) return 0;
            jdbc.update(
                    "DELETE FROM mentor_turn_attempt_usage u USING " + SELECTED
                            + " WHERE u.turn_id = s.turn_id AND u.workspace_id = s.workspace_id",
                    keys(selection));
            return selection.rows().size();
        }

        /**
         * Forgets each selected turn, by its id and its own workspace. Repeating it is safe: a forgotten turn has no
         * measurement left, and its detached ledger rows no longer name it.
         */
        @Override
        public void prepareErasure(PersonDataSelection selection) {
            for (var row : selection.rows()) {
                attemptUsage.forgetTurn(
                        Long.parseLong(Objects.requireNonNull(row.columns().get("workspace_id"))),
                        UUID.fromString(Objects.requireNonNull(row.columns().get("turn_id"))));
            }
        }

        private Map<String, Object> keys(PersonDataSelection selection) {
            return Map.of(
                    "keys",
                    mapper.writeValueAsString(selection.rows().stream()
                            .map(PersonDataSelection.RowKey::columns)
                            .toList()));
        }
    }
}
