package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Runs pending provenance linking before application readiness. */
@Component
@Profile("!test & !specs")
@ConditionalOnServerRole
@RequiredArgsConstructor
class CatalogProvenanceBackfillStartup implements ApplicationRunner {

    private final CatalogProvenanceBackfill backfill;

    @Override
    public void run(ApplicationArguments args) {
        backfill.run();
    }
}
