package de.tum.cit.aet.hephaestus.practices.research;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountErasureContributor;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.research.adapter.ResearchDisclosurePersonDataCatalog;
import org.springframework.stereotype.Component;

/**
 * Account purge reaches disclosures released after a person request froze its selection. Same policy
 * as {@link ResearchDisclosurePersonDataCatalog}: the link goes, the removal obligation and the key stay.
 */
@Component
@ConditionalOnServerRole
class ResearchDisclosureAccountErasureAdapter implements AccountErasureContributor {

    private final ResearchDisclosureSubjectRepository subjects;
    private final ResearchDisclosureRepository disclosures;

    ResearchDisclosureAccountErasureAdapter(
            ResearchDisclosureSubjectRepository subjects, ResearchDisclosureRepository disclosures) {
        this.subjects = subjects;
        this.disclosures = disclosures;
    }

    @Override
    public void eraseAccount(long accountId) {
        subjects.unlinkErasedAccount(accountId);
        disclosures.unlinkCreator(accountId);
    }
}
