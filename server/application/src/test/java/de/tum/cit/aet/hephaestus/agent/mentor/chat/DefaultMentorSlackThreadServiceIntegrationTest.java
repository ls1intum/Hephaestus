package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.mentor.ThreadSurface;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real-Postgres proof that {@link MentorSlackThreadService#purgeSlackThreads} erases exactly the workspace's
 * {@code SLACK_DM} mentor threads and nothing else — the surface-scoped erasure an app uninstall relies on. The one
 * load-bearing assertion is that {@code WEB} history survives: flip {@code ThreadSurface.SLACK_DM} to {@code WEB}
 * inside {@code DefaultMentorSlackThreadService#purgeSlackThreads} (the exact inversion) and this fails — the SLACK_DM
 * row survives while the WEB row is wrongly erased. Also pins workspace scoping (a second workspace's SLACK_DM thread
 * is untouched).
 *
 * <p>Deliberately seeds no {@code chat_message}: the {@code chat_thread → chat_message} {@code ON DELETE CASCADE} that
 * {@code purgeSlackThreads} relies on exists only in the Liquibase production schema, not on this entity-derived
 * ({@code ddl-auto: create}) test schema, so a message here would trip a spurious FK violation.
 */
class DefaultMentorSlackThreadServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MentorSlackThreadService mentorSlackThreadService;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    private static final AtomicLong USER_SEQ = new AtomicLong(3_000_000L);

    private User user;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        IdentityProvider provider = identityProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                identityProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );
        user = userRepository.save(
            TestUserFactory.createUser(USER_SEQ.incrementAndGet(), "mentor-thread-user", provider)
        );
    }

    @Test
    @DisplayName("purgeSlackThreads erases only the workspace's SLACK_DM threads; WEB + other workspaces survive")
    void purgeSlackThreads_erasesSlackDmOnly_keepsWebAndOtherWorkspace() {
        Workspace a = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("slack-purge-threads-a"));
        Workspace b = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("slack-purge-threads-b"));

        UUID aSlackDm = seedThread(a, ThreadSurface.SLACK_DM);
        UUID aWeb = seedThread(a, ThreadSurface.WEB);
        UUID bSlackDm = seedThread(b, ThreadSurface.SLACK_DM);

        int purged = mentorSlackThreadService.purgeSlackThreads(a.getId());

        assertThat(purged).isEqualTo(1);
        // A's SLACK_DM thread is gone …
        assertThat(chatThreadRepository.findById(aSlackDm)).isEmpty();
        // … but A's WEB mentor history survives …
        assertThat(chatThreadRepository.findById(aWeb)).isPresent();
        // … and another workspace's SLACK_DM thread is untouched (workspace scoping).
        assertThat(chatThreadRepository.findById(bSlackDm)).isPresent();
    }

    private UUID seedThread(Workspace workspace, ThreadSurface surface) {
        ChatThread thread = new ChatThread();
        thread.setId(UUID.randomUUID());
        thread.setTitle(surface + " thread");
        thread.setUser(user);
        thread.setWorkspace(workspace);
        thread.setSurface(surface);
        return chatThreadRepository.save(thread).getId();
    }
}
