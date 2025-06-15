package de.tum.in.www1.hephaestus.mentor;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Immutable composite primary key for ChatMessagePart entity.
 * Combines message ID and order index to uniquely identify a message part.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChatMessagePartId implements Serializable {
    
    /**
     * ID of the message this part belongs to
     */
    private UUID messageId;
    
    /**
     * Order of this part within the message (0-based)
     */
    private Integer orderIndex;
}
