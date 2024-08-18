package de.tum.in.www1.hephaestus.codereview.comment;

import java.util.List;

import de.tum.in.www1.hephaestus.codereview.pullrequest.Pullrequest;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table
@Getter
@Setter
public class CommentConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "connection", fetch = FetchType.LAZY)
    private List<Comment> nodes;
}
