package de.tum.in.www1.hephaestus.chat.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<List<Session>> findByUserLogin(@Param("login") String login);
}