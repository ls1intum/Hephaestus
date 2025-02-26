package de.tum.in.www1.hephaestus.gitprovider.contributor;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

@org.springframework.stereotype.Repository
public interface ContributorRepository extends JpaRepository<Contributor, Long> {
    List<Contributor> findByRepository(Repository repository);

    Optional<Contributor> findByRepositoryAndUser(Repository repository, User user);

    List<Contributor> findByUser(User user);
}
