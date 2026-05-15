package de.tum.in.www1.hephaestus.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Wave H4 (issue #1071): verifies the FK cascade behaviour both up
 * ({@code workspace} → {@code chat_thread}) and sideways ({@code user} → {@code chat_thread}).
 *
 * <p>The {@code 1779000000000-mentor-1071-chat-thread-user-cascade} migration adds
 * {@code ON DELETE CASCADE} to {@code chat_thread.user_id} so a future user-delete path
 * (GDPR Art. 17 right-to-erasure) erases the user's conversation history transparently.
 * Without this constraint a user delete would either fail (NO ACTION FK) or leak threads
 * (SET NULL with no cleanup).
 *
 * <p>The test profile uses JPA {@code ddl-auto=create} which infers FKs without cascades.
 * We re-apply the production cascade DDL in {@link #setUp} so the test exercises the same
 * constraint shape production uses.
 */
@DisplayName("ChatThread FK cascade integration")
class ChatThreadCascadeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private DataSource dataSource;

    private Workspace workspace;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        databaseTestUtils.cleanDatabase();
        // Re-apply the production cascade FK shape from 1779000000000_changelog.xml. The
        // JPA-inferred FK has no cascade, so without this step `userRepository.delete(user)`
        // throws PSQLException("violates foreign key constraint") instead of cascading.
        // The same is true for chat_thread.workspace_id FK: the production migration sets
        // CASCADE but JPA ddl-auto=create doesn't infer it.
        //
        // FK names are JPA-generated (e.g. `fkd60x7l7kqrfe3yba8wltun1er`) so we look them up
        // by column reference via pg_constraint rather than name pattern, drop, then re-add
        // with CASCADE.
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute(
                "DO $$ " +
                    "DECLARE fk_name TEXT; " +
                    "BEGIN " +
                    "  FOR fk_name IN " +
                    "    SELECT con.conname FROM pg_constraint con " +
                    "      JOIN pg_class cls ON cls.oid = con.conrelid " +
                    "      JOIN pg_attribute att ON att.attnum = ANY(con.conkey) AND att.attrelid = cls.oid " +
                    "    WHERE con.contype = 'f' AND cls.relname = 'chat_thread' AND att.attname = 'user_id' " +
                    "  LOOP " +
                    "    EXECUTE 'ALTER TABLE chat_thread DROP CONSTRAINT ' || quote_ident(fk_name); " +
                    "  END LOOP; " +
                    "  FOR fk_name IN " +
                    "    SELECT con.conname FROM pg_constraint con " +
                    "      JOIN pg_class cls ON cls.oid = con.conrelid " +
                    "      JOIN pg_attribute att ON att.attnum = ANY(con.conkey) AND att.attrelid = cls.oid " +
                    "    WHERE con.contype = 'f' AND cls.relname = 'chat_thread' AND att.attname = 'workspace_id' " +
                    "  LOOP " +
                    "    EXECUTE 'ALTER TABLE chat_thread DROP CONSTRAINT ' || quote_ident(fk_name); " +
                    "  END LOOP; " +
                    "END $$;"
            );
            stmt.execute(
                "ALTER TABLE chat_thread " +
                    "ADD CONSTRAINT fk_chat_thread_user_cascade " +
                    "FOREIGN KEY (user_id) REFERENCES \"user\"(id) ON DELETE CASCADE"
            );
            stmt.execute(
                "ALTER TABLE chat_thread " +
                    "ADD CONSTRAINT fk_chat_thread_workspace_cascade " +
                    "FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE"
            );
        }

        workspace = new Workspace();
        workspace.setWorkspaceSlug("cascade-test-ws");
        workspace.setDisplayName("Cascade Test Workspace");
        workspace.setAccountLogin("cascade-org");
        workspace.setAccountType(AccountType.ORG);
        workspace = workspaceRepository.save(workspace);

        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://gitlab.com")));

        user = new User();
        user.setNativeId(9_999L);
        user.setLogin("cascade-tester");
        user.setName("Cascade Tester");
        user.setAvatarUrl("https://example.com/c.png");
        user.setHtmlUrl("https://gitlab.com/cascade-tester");
        user.setType(User.Type.USER);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setProvider(provider);
        user = userRepository.save(user);
    }

    @Test
    @DisplayName("user delete cascades to chat_thread (GDPR Art. 17 right-to-erasure)")
    void userDelete_cascadesToChatThread() {
        // Seed two threads owned by the same user.
        ChatThread t1 = seedThread("cascade-thread-A");
        ChatThread t2 = seedThread("cascade-thread-B");
        assertThat(chatThreadRepository.findById(t1.getId())).isPresent();
        assertThat(chatThreadRepository.findById(t2.getId())).isPresent();

        userRepository.delete(user);
        userRepository.flush();

        assertThat(chatThreadRepository.findById(t1.getId()))
            .as("chat_thread.user_id ON DELETE CASCADE must erase the user's threads")
            .isEmpty();
        assertThat(chatThreadRepository.findById(t2.getId()))
            .as("second user-owned thread must also cascade")
            .isEmpty();
    }

    @Test
    @DisplayName("workspace delete cascades to chat_thread (existing fk_chat_thread_workspace)")
    void workspaceDelete_cascadesToChatThread() {
        ChatThread t1 = seedThread("ws-cascade-A");
        ChatThread t2 = seedThread("ws-cascade-B");
        assertThat(chatThreadRepository.findById(t1.getId())).isPresent();
        assertThat(chatThreadRepository.findById(t2.getId())).isPresent();

        // Physical delete of the workspace row triggers the existing DDL cascade defined
        // in migration 1764514002516-6f (fk_chat_thread_workspace ON DELETE CASCADE).
        workspaceRepository.delete(workspace);
        workspaceRepository.flush();

        assertThat(chatThreadRepository.findById(t1.getId())).isEmpty();
        assertThat(chatThreadRepository.findById(t2.getId())).isEmpty();
    }

    private ChatThread seedThread(String title) {
        ChatThread thread = new ChatThread();
        thread.setId(UUID.randomUUID());
        thread.setTitle(title);
        thread.setWorkspace(workspace);
        thread.setUser(user);
        return chatThreadRepository.save(thread);
    }
}
