package de.tum.in.www1.hephaestus.mentor;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageVoteRepository extends JpaRepository<ChatMessageVote, UUID> {
    // Inherits findById / save / deleteById keyed by messageId — sufficient for the upsert/delete flow.
}
