package de.tum.in.www1.hephaestus.mentor.message;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySessionIdOrderBySentAtAsc(Long sessionId);
}
