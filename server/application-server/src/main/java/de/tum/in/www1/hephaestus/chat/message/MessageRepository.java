package de.tum.in.www1.hephaestus.chat.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> { 
    List<Message> findByChatId(Long chatId);
    List<Message> findByChatIdAndContentContaining(Long chatId, String keyword);
}