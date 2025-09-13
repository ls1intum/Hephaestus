package de.tum.in.www1.hephaestus.mentor.vote;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Represents a vote on a chat message.
 */
@Entity
@Table(name = "chat_message_vote")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatMessageVote {

    /**
     * The message ID - serves as primary key since each message has at most one vote
     */
    @Id
    @EqualsAndHashCode.Include
    private UUID messageId;

    /**
     * Boolean vote: true = upvote (helpful), false = downvote (not helpful)
     */
    @NotNull
    @Column(nullable = false)
    private Boolean isUpvoted;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Constructor for new votes
     */
    public ChatMessageVote(UUID messageId, Boolean isUpvoted) {
        this.messageId = messageId;
        this.isUpvoted = isUpvoted;
    }
}
