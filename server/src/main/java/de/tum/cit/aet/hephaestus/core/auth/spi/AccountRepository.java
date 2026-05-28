package de.tum.cit.aet.hephaestus.core.auth.spi;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @NamedInterface — cross-module read-only handle on {@link Account}. Concrete
 * mutations live behind {@code AccountService} in the auth module.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {}
