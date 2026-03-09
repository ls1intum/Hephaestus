package de.tum.in.www1.hephaestus.practices.model;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class BadPracticeDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pullrequest_id")
    @ToString.Exclude
    private PullRequest pullRequest;

    @NonNull
    @Column(columnDefinition = "TEXT")
    private String summary;

    @NonNull
    @OneToMany(mappedBy = "badPracticeDetection", cascade = CascadeType.ALL)
    @ToString.Exclude
    private List<PullRequestBadPractice> badPractices;

    @NonNull
    @Column(name = "detected_at")
    private Instant detectedAt;

    private String traceId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BadPracticeDetection that = (BadPracticeDetection) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
