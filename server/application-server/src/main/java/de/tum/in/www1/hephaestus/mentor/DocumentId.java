package de.tum.in.www1.hephaestus.mentor;

import java.io.Serializable;
import java.util.UUID;
import lombok.*;

/**
 * Composite primary key for Document entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentId implements Serializable {

    private UUID id;
    private Integer versionNumber;
}
