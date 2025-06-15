package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test data setup helper for chat-related integration tests.
 * Creates necessary test users and chat data for testing the chat persistence functionality.
 */
@Component
@Profile("test")
public class ChatTestDataSetup {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ChatThreadRepository chatThreadRepository;
    
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    @Autowired
    private ChatMessagePartRepository chatMessagePartRepository;

    /**
     * Creates and persists a test user for chat testing.
     * Uses the same test user from the Keycloak example config.
     */
    @Transactional
    public User createTestUser() {
        // Check if test user already exists
        return userRepository.findByLogin("testuser")
            .orElseGet(() -> {
                User testUser = new User();
                testUser.setId(999L); // Setting a test ID for the user
                testUser.setLogin("testuser");
                testUser.setName("Test User");
                testUser.setAvatarUrl("https://github.com/testuser.png");
                testUser.setHtmlUrl("https://github.com/testuser");
                testUser.setType(User.Type.USER);
                testUser.setEmail("testuser@example.com");
                testUser.setFollowers(0);
                testUser.setFollowing(0);
                testUser.setLeaguePoints(0);
                testUser.setNotificationsEnabled(true);
                
                return userRepository.save(testUser);
            });
    }

    /**
     * Creates a test chat thread for the given user.
     */
    @Transactional
    public ChatThread createTestChatThread(User user) {
        ChatThread thread = new ChatThread();
        thread.setUser(user);
        thread.setTitle("Test Chat Thread");
        
        return chatThreadRepository.save(thread);
    }

    /**
     * Cleans up chat test data.
     */
    @Transactional
    public void cleanupChatData() {
        chatMessagePartRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatThreadRepository.deleteAll();
    }
}
