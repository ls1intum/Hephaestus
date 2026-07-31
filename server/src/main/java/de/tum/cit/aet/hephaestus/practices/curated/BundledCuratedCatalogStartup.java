package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !specs")
@ConditionalOnServerRole
@RequiredArgsConstructor
class BundledCuratedCatalogStartup implements ApplicationRunner {

    private final BundledPracticeCatalogLoader loader;
    private final BundledCuratedCatalogReconciler reconciler;

    @Override
    public void run(ApplicationArguments args) {
        reconciler.reconcile(loader.load());
    }
}
