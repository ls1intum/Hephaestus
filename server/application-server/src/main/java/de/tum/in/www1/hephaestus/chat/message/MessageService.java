package de.tum.in.www1.hephaestus.chat.message;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.chat.Chat;
import de.tum.in.www1.hephaestus.chat.message.Message.MessageSender;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DefaultApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatResponse;
import de.tum.in.www1.hephaestus.chat.ChatRepository;



/**
 * Service for managing messages.
 */
@Service
public class MessageService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final DefaultApi chatApiClient;

    public MessageService(ChatRepository chatRepository, MessageRepository messageRepository) {
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.chatApiClient = new DefaultApi();
    }

    /**
     * Sends a message, saves it, and generates a bot response.
     */
    public MessageDTO sendMessage(MessageDTO messageDTO) {
        Chat chat = chatRepository.findById(messageDTO.chatId())
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        Message userMessage = new Message(ZonedDateTime.now(), MessageSender.USER, messageDTO.content(), chat);
        messageRepository.save(userMessage);

        String systemResponse = generateResponse(messageDTO.chatId(), messageDTO.content());

        Message systemMessage = new Message(ZonedDateTime.now(), MessageSender.SYSTEM, systemResponse, chat);
        messageRepository.save(systemMessage);

        return new MessageDTO(systemMessage.getId(), systemMessage.getSentAt(), systemMessage.getSender(), systemMessage.getContent(), systemMessage.getChat().getId()); 
    }

    /**
     * Calls the Python FastAPI service to generate a bot response.
     */
    private String generateResponse(Long chatId, String messageContent) {
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setChatId(chatId.toString());
        chatRequest.setMessageContent(messageContent);

        try {
            ChatResponse chatResponse = chatApiClient.chatChatPost(chatRequest);
            return chatResponse.getMessageContent();
        } catch (Exception e) {
            throw new RuntimeException("Error communicating with intelligence service: " + e.getMessage(), e);
        }
    }
}