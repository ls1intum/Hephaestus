package de.tum.in.www1.hephaestus.mentor.session;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.mentor.message.Message;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "session")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OrderColumn
    @OneToMany(mappedBy = "session")
    private List<Message> messages = new ArrayList<>();

    @NonNull
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @NonNull
    private boolean isClosed = false;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;
}
