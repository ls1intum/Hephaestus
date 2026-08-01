package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stamps catalog provenance on workspaces seeded before the catalog existed, before the application
 * reports itself ready.
 *
 * <p>An {@code ApplicationRunner} runs strictly before {@code ApplicationReadyEvent}, which is what
 * puts this ahead of workspace provisioning and the seeder it drives.
 */
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
