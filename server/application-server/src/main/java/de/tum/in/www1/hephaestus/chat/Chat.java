package de.tum.in.www1.hephaestus.chat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.*;
import de.tum.in.www1.hephaestus.chat.message.Message;
import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.user.User;

import java.util.Optional;


@Entity
@Table(name = "chat")
@Getter
@Setter
@ToString(callSuper = true) 
@NoArgsConstructor
public class Chat extends BaseGitServiceEntity {

    @OrderColumn(name = "message_order")
    @OneToMany(mappedBy = "chat")
    private List<Message> messages = new ArrayList<>();

    @Column(name = "creation_date")
    private ZonedDateTime creationDate = ZonedDateTime.now();

    @OneToOne
    private User user;
}