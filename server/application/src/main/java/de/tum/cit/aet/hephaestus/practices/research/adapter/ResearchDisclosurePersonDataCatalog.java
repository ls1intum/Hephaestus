package de.tum.cit.aet.hephaestus.practices.research.adapter;

import de.tum.cit.aet.hephaestus.core.privacy.spi.JdbcPersonDataStore;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCatalog;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataContributor;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataStores;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The research disclosure stores in the person request. Erasure never deletes a subject row: it drops
 * the account link and records that the person's rows must be removed downstream, so the researchers
 * can still be told which key to remove. The grant reference stays, because erasure unlinks that
 * ledger row from the account too.
 */
@Component
@ConditionalOnServerRole
@PersonDataStores({"research_disclosure_subject", "research_disclosure"})
public class ResearchDisclosurePersonDataCatalog implements PersonDataCatalog {

    static final String SUBJECT_ERASURE = "account_id=NULL,removal_required_at=COALESCE(removal_required_at,now()),"
            + "removal_reason=COALESCE(removal_reason,'ACCOUNT_ERASED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ResearchDisclosurePersonDataCatalog(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public List<PersonDataContributor> contributors() {
        return List.of(
                new JdbcPersonDataStore(
                        jdbc,
                        mapper,
                        "research_disclosure_subject",
                        "research_disclosure_subject",
                        "t.account_id=:account",
                        "id,disclosure_id,workspace_id,participant_key,removal_required_at,removal_reason,removed_at",
                        "id",
                        SUBJECT_ERASURE,
                        -140),
                new JdbcPersonDataStore(
                        jdbc,
                        mapper,
                        "research_disclosure",
                        "research_disclosure",
                        "t.created_by_account_id=:account",
                        "id,workspace_id,purpose,schema_version,window_from,window_to,created_at,use_until,closed_at,closure",
                        "id",
                        "created_by_account_id=NULL",
                        -140));
    }
}
