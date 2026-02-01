package de.tum.in.www1.hephaestus.gitprovider.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.NonNull;

/**
 * Represents a custom field configuration in a GitHub Projects V2 project.
 * <p>
 * Fields define the columns/properties that can be set on project items.
 * GitHub provides various field types: text, number, date, single-select, iteration.
 * <p>
 * Note: Uses String ID (GitHub's node ID) as there is no databaseId for fields in GraphQL.
 */
@Entity
@Table(
    name = "project_field",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_field_project_name", columnNames = { "project_id", "name" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ProjectField {

    /**
     * The data type of the field.
     * Maps to GitHub's ProjectV2FieldType.
     */
    public enum DataType {
        TEXT,
        NUMBER,
        DATE,
        SINGLE_SELECT,
        ITERATION,
    }

    /**
     * GitHub GraphQL node ID for this field.
     * This is the primary key since fields don't have databaseId in GraphQL.
     */
    @Id
    @Column(length = 64)
    private String id;

    /**
     * The project this field belongs to.
     */
    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @ToString.Exclude
    private Project project;

    /**
     * The name of the field (e.g., "Status", "Priority", "Sprint").
     */
    @NonNull
    @Column(length = 256, nullable = false)
    private String name;

    /**
     * The data type of this field.
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", length = 32, nullable = false)
    private DataType dataType;

    /**
     * JSON configuration for this field.
     * For SINGLE_SELECT: array of {id, name, color, description}.
     * For ITERATION: array of {id, title, startDate, duration}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @ToString.Exclude
    private String options;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * Field values that use this field definition.
     */
    @OneToMany(mappedBy = "field")
    @ToString.Exclude
    private Set<ProjectFieldValue> values = new HashSet<>();
}
