package de.tum.in.www1.hephaestus.chat.message;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;
import de.tum.in.www1.hephaestus.chat.session.Session;

@Entity
@Table(name = "message")
@Getter
@Setter
@ToString(callSuper = true)
@RequiredArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    protected Long id;
    
    @NonNull
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @NonNull
    @Enumerated(EnumType.STRING)
    private MessageSender sender;

    @NonNull
    @Column(name = "content")
    private String content;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;

    public enum MessageSender {
        SYSTEM, USER
    }
}