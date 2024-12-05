package de.tum.in.www1.hephaestus.mentor.session;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByUser(User user);
}