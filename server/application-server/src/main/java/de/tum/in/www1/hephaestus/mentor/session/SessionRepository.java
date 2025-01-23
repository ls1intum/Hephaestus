package de.tum.in.www1.hephaestus.mentor.session;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByUser(User user);

    Optional<Session> findFirstByUserOrderByCreatedAtDesc(User user);

    List<Session> findByUserOrderByCreatedAtDesc(User user);
}
