package de.tum.cit.aet.hephaestus.core.privacy.spi;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface PersonIdentityResolver {
    record Provider(long id, String type, String serverUrl) {}

    List<Provider> providers();

    /** Exact linked ownership, including retained disabled links; conflicting owners are refused. */
    Optional<Long> accountFor(PersonIdentity identity);

    PersonScope resolve(@Nullable Long accountId, List<PersonIdentity> identities);
}
