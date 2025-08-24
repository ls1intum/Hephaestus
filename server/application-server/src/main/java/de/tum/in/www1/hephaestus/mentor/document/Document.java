package de.tum.in.www1.hephaestus.mentor.document;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.springframework.lang.NonNull;

/**
 * Document entity representing a versioned document.
 * Uses composite primary key (id, createdAt).
 * Each save creates a new version with the same id but different createdAt.
 */
@Entity
@Table(
    name = "document",
    indexes = {
        @Index(name = "idx_document_id", columnList = "id"),
        @Index(name = "idx_document_user_id", columnList = "user_id"),
        @Index(name = "idx_document_created_at", columnList = "created_at"),
    }
)
@IdClass(DocumentId.class)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Document {

    @Id
    @Column(name = "id", nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Id
    @Column(name = "version_number", nullable = false)
    @EqualsAndHashCode.Include
    private Integer versionNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NonNull
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private DocumentKind kind = DocumentKind.TEXT;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    public Document(UUID id, int versionNumber, String title, String content, DocumentKind kind, User user) {
        this.id = id;
        this.versionNumber = versionNumber;
        this.createdAt = Instant.now();
        this.title = title;
        this.content = content;
        this.kind = kind;
        this.user = user;
    }
}
