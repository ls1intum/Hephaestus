package de.tum.in.www1.hephaestus.chat.message;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;
import de.tum.in.www1.hephaestus.chat.session.Session;
import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;

@Entity
@Table(name = "message")
@Getter
@Setter
@ToString(callSuper = true)
@RequiredArgsConstructor
public class Message extends BaseGitServiceEntity {
    @NonNull
    @Column(name = "sent_at")
    private final ZonedDateTime sentAt;

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