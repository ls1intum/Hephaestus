package de.tum.in.www1.hephaestus.chat;

import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Service for managing chats.
 */
@Service
public class ChatService {

    private final ChatRepository chatRepository;

    public ChatService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    /**
     * Creates a new chat for the given user.
     * 
     * @param user The user the chat belongs to
     * @return The created chat
     */
    public ChatDTO createChat(User user) {
        Chat chat = new Chat();
        chat.setUser(user);
        chat.setCreatedAt(OffsetDateTime.now());
        return new ChatDTO(chatRepository.save(chat));
    }

    /**
     * Finds a chat by its ID.
     * 
     * @param chatId The ID of the chat to find
     * @return The chat entity if found, otherwise throws an exception
     */
    public Optional<ChatDTO> findChatById(Long chatId) {
        return chatRepository.findById(chatId).map(ChatDTO::new);
    }

}