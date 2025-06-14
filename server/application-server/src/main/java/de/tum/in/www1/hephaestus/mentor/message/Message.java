package de.tum.in.www1.hephaestus.mentor.message;

import de.tum.in.www1.hephaestus.mentor.session.Session;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.Length;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "message")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private OffsetDateTime sentAt = OffsetDateTime.now();

    @NonNull
    @Enumerated(EnumType.STRING)
    private MessageSender sender;

    @Column(length = Length.LONG16)
    @NonNull
    private String content;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;

    public enum MessageSender {
        MENTOR,
        USER,
    }
}
