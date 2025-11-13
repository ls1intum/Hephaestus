package de.tum.in.www1.hephaestus.gitprovider.issue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "issue_link",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_issue_link_source_target_type",
        columnNames = { "source_issue_id", "target_issue_id", "type" }
    )
)
@Getter
@Setter
@NoArgsConstructor
public class IssueLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_issue_id", nullable = false)
    private Issue source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_issue_id", nullable = false)
    private Issue target;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private IssueLinkType type;

    public IssueLink(Issue source, Issue target, IssueLinkType type) {
        this.source = source;
        this.target = target;
        this.type = type;
    }
}
