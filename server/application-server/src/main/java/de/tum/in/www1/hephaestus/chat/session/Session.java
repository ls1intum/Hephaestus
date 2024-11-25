package de.tum.in.www1.hephaestus.chat.session;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;
import jakarta.persistence.*;
import de.tum.in.www1.hephaestus.chat.message.Message;
import de.tum.in.www1.hephaestus.gitprovider.user.User;

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

    @OrderColumn(name = "sentAt")
    @OneToMany(mappedBy = "session")
    private List<Message> messages = new ArrayList<>();

    @NonNull
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @ManyToOne
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;
}