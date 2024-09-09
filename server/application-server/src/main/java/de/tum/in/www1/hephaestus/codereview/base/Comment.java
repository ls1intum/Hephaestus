package de.tum.in.www1.hephaestus.codereview.base;

import de.tum.in.www1.hephaestus.codereview.user.User;
import jakarta.persistence.Basic;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public abstract class Comment extends BaseGitServiceEntity {
    @Lob
    @Basic(fetch = FetchType.EAGER)
    protected String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    protected User author;
}
