package de.tum.in.www1.hephaestus.chat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.*;
import de.tum.in.www1.hephaestus.chat.message.Message;
import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.user.User;

@Entity
@Table(name = "session")
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
public class Session extends BaseGitServiceEntity {

    @OrderColumn(name = "message_order")
    @OneToMany(mappedBy = "session")
    private List<Message> messages = new ArrayList<>();

    @Column(name = "creation_date")
    private ZonedDateTime creationDate = ZonedDateTime.now();

    @ManyToOne
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;
}