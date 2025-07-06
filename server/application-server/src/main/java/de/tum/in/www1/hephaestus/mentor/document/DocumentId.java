package de.tum.in.www1.hephaestus.mentor.document;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Composite primary key for Document entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentId implements Serializable {
    private UUID id;
    private Instant createdAt;
}
