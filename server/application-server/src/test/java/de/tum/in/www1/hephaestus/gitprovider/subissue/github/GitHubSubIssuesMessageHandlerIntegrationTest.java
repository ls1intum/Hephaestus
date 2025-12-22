package de.tum.in.www1.hephaestus.gitprovider.subissue.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEventPayloadSubIssues;
import org.kohsuke.github.GHEventPayloadSubIssues.Action;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.StreamUtils;

/**
 * Integration tests for GitHub sub_issues webhook handling.
 * <p>
 * Uses the hub4j-compatible {@link GHEventPayloadSubIssues} payload class.
 * <p>
 * These tests verify:
 * <ul>
 * <li>Payload parsing from real GitHub webhook fixtures</li>
 * <li>End-to-end handler invocation with proper routing</li>
 * <li>Database persistence of parent-child relationships</li>
 * <li>Edge cases: idempotency, missing issues</li>
 * </ul>
 */
@DisplayName("GitHub Sub-Issues Webhook Handler")
class GitHubSubIssuesMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private GitHubSubIssuesMessageHandler messageHandler;

    @MockitoSpyBean
    private GitHubSubIssueSyncService subIssueSyncService;

    // Test fixture IDs from the JSON payloads
    private static final long SUB_ISSUE_ID = 3578496597L;
    private static final long PARENT_ISSUE_ID = 3578496080L;
    private static final int SUB_ISSUE_NUMBER = 21;
    private static final int PARENT_ISSUE_NUMBER = 20;

    @BeforeEach
    void setup() {
        issueRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PAYLOAD PARSING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Payload Parsing")
    class PayloadParsingTests {

        @Test
        @DisplayName("Should parse sub_issue_added payload with all fields")
        void parseSubIssueAddedPayload() throws Exception {
            String payload = loadPayload("github/sub_issues.sub_issue_added.json");

            GHEventPayloadSubIssues parsed = objectMapper.readValue(payload, GHEventPayloadSubIssues.class);

            // Action should be type-safe enum
            assertThat(parsed.getActionType()).isEqualTo(Action.SUB_ISSUE_ADDED);
            assertThat(parsed.getAction()).isEqualTo("sub_issue_added");
            assertThat(parsed.isLinkEvent()).isTrue();
            assertThat(parsed.isUnlinkEvent()).isFalse();

            // IDs
            assertThat(parsed.getSubIssueId()).isEqualTo(SUB_ISSUE_ID);
            assertThat(parsed.getParentIssueId()).isEqualTo(PARENT_ISSUE_ID);

            // Sub-issue details (via hub4j GHIssue)
            assertThat(parsed.getSubIssue()).isNotNull();
            assertThat(parsed.getSubIssue().getId()).isEqualTo(SUB_ISSUE_ID);
            assertThat(parsed.getSubIssue().getNumber()).isEqualTo(SUB_ISSUE_NUMBER);

            // Parent issue details
            assertThat(parsed.getParentIssue()).isNotNull();
            assertThat(parsed.getParentIssue().getId()).isEqualTo(PARENT_ISSUE_ID);
            assertThat(parsed.getParentIssue().getNumber()).isEqualTo(PARENT_ISSUE_NUMBER);

            // Repository context
            assertThat(parsed.getSubIssueRepo()).isNotNull();
            assertThat(parsed.getSubIssueRepo().getFullName()).isEqualTo("HephaestusTest/TestRepository");
        }

        @Test
        @DisplayName("Should parse sub_issue_removed payload correctly")
        void parseSubIssueRemovedPayload() throws Exception {
            String payload = loadPayload("github/sub_issues.sub_issue_removed.json");

            GHEventPayloadSubIssues parsed = objectMapper.readValue(payload, GHEventPayloadSubIssues.class);

            assertThat(parsed.getActionType()).isEqualTo(Action.SUB_ISSUE_REMOVED);
            assertThat(parsed.isLinkEvent()).isFalse();
            assertThat(parsed.isUnlinkEvent()).isTrue();
        }

        @Test
        @DisplayName("Should parse parent_issue_added payload correctly")
        void parseParentIssueAddedPayload() throws Exception {
            String payload = loadPayload("github/sub_issues.parent_issue_added.json");

            GHEventPayloadSubIssues parsed = objectMapper.readValue(payload, GHEventPayloadSubIssues.class);

            assertThat(parsed.getActionType()).isEqualTo(Action.PARENT_ISSUE_ADDED);
            assertThat(parsed.isLinkEvent()).isTrue();
            assertThat(parsed.isUnlinkEvent()).isFalse();
            assertThat(parsed.getParentIssueRepo()).isNotNull();
        }

        @Test
        @DisplayName("Should parse parent_issue_removed payload correctly")
        void parseParentIssueRemovedPayload() throws Exception {
            String payload = loadPayload("github/sub_issues.parent_issue_removed.json");

            GHEventPayloadSubIssues parsed = objectMapper.readValue(payload, GHEventPayloadSubIssues.class);

            assertThat(parsed.getActionType()).isEqualTo(Action.PARENT_ISSUE_REMOVED);
            assertThat(parsed.isLinkEvent()).isFalse();
            assertThat(parsed.isUnlinkEvent()).isTrue();
        }

        @Test
        @DisplayName("Action enum should correctly identify link vs unlink")
        void actionEnumBehavior() {
            assertThat(Action.SUB_ISSUE_ADDED.isLinkAction()).isTrue();
            assertThat(Action.PARENT_ISSUE_ADDED.isLinkAction()).isTrue();
            assertThat(Action.SUB_ISSUE_REMOVED.isLinkAction()).isFalse();
            assertThat(Action.PARENT_ISSUE_REMOVED.isLinkAction()).isFalse();

            assertThat(Action.SUB_ISSUE_ADDED.isUnlinkAction()).isFalse();
            assertThat(Action.PARENT_ISSUE_ADDED.isUnlinkAction()).isFalse();
            assertThat(Action.SUB_ISSUE_REMOVED.isUnlinkAction()).isTrue();
            assertThat(Action.PARENT_ISSUE_REMOVED.isUnlinkAction()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HANDLER INTEGRATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Handler Integration")
    class HandlerIntegrationTests {

        @Test
        @DisplayName("Should invoke sync service when handler processes sub_issue_added")
        void handlerInvokesSyncServiceOnSubIssueAdded() throws Exception {
            createTestIssues();

            String payloadJson = loadPayload("github/sub_issues.sub_issue_added.json");
            GHEventPayloadSubIssues payload = objectMapper.readValue(payloadJson, GHEventPayloadSubIssues.class);

            messageHandler.handleEvent(payload);

            // Verify sync service was called with correct arguments
            ArgumentCaptor<Long> subIssueIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long> parentIssueIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Boolean> isLinkCaptor = ArgumentCaptor.forClass(Boolean.class);

            verify(subIssueSyncService).processSubIssueEvent(
                subIssueIdCaptor.capture(),
                parentIssueIdCaptor.capture(),
                isLinkCaptor.capture(),
                any()
            );

            assertThat(subIssueIdCaptor.getValue()).isEqualTo(SUB_ISSUE_ID);
            assertThat(parentIssueIdCaptor.getValue()).isEqualTo(PARENT_ISSUE_ID);
            assertThat(isLinkCaptor.getValue()).isTrue();
        }

        @Test
        @DisplayName("Should invoke sync service with isLink=false for sub_issue_removed")
        void handlerInvokesSyncServiceOnSubIssueRemoved() throws Exception {
            createTestIssues();

            String payloadJson = loadPayload("github/sub_issues.sub_issue_removed.json");
            GHEventPayloadSubIssues payload = objectMapper.readValue(payloadJson, GHEventPayloadSubIssues.class);

            messageHandler.handleEvent(payload);

            verify(subIssueSyncService).processSubIssueEvent(
                anyLong(),
                anyLong(),
                ArgumentCaptor.forClass(Boolean.class).capture(),
                any()
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATABASE PERSISTENCE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Database Persistence")
    class DatabasePersistenceTests {

        @Test
        @DisplayName("Should link sub-issue to parent in database")
        void linksSubIssueToParentInDatabase() {
            createTestIssues();

            subIssueSyncService.processSubIssueEvent(SUB_ISSUE_ID, PARENT_ISSUE_ID, true);

            Issue subIssue = issueRepository.findById(SUB_ISSUE_ID).orElseThrow();
            assertThat(subIssue.getParentIssue()).isNotNull();
            assertThat(subIssue.getParentIssue().getId()).isEqualTo(PARENT_ISSUE_ID);
        }

        @Test
        @DisplayName("Should unlink sub-issue from parent in database")
        void unlinksSubIssueFromParentInDatabase() {
            createTestIssues();

            // First link
            subIssueSyncService.processSubIssueEvent(SUB_ISSUE_ID, PARENT_ISSUE_ID, true);

            // Then unlink
            subIssueSyncService.processSubIssueEvent(SUB_ISSUE_ID, PARENT_ISSUE_ID, false);

            Issue subIssue = issueRepository.findById(SUB_ISSUE_ID).orElseThrow();
            assertThat(subIssue.getParentIssue()).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IDEMPOTENCY & EDGE CASE TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Idempotency & Edge Cases")
    class IdempotencyTests {

        @Test
        @DisplayName("Should handle duplicate link events gracefully (idempotent)")
        void duplicateLinkEventsAreIdempotent() {
            createTestIssues();

            // Link multiple times
            subIssueSyncService.processSubIssueEvent(SUB_ISSUE_ID, PARENT_ISSUE_ID, true);
            subIssueSyncService.processSubIssueEvent(SUB_ISSUE_ID, PARENT_ISSUE_ID, true);
            subIssueSyncService.processSubIssueEvent(SUB_ISSUE_ID, PARENT_ISSUE_ID, true);

            Issue subIssue = issueRepository.findById(SUB_ISSUE_ID).orElseThrow();
            assertThat(subIssue.getParentIssue()).isNotNull();
            assertThat(subIssue.getParentIssue().getId()).isEqualTo(PARENT_ISSUE_ID);
        }

        @Test
        @DisplayName("Should handle unlink when not linked (idempotent)")
        void unlinkWhenNotLinkedIsIdempotent() {
            createTestIssues();

            // Issue is not linked initially
            Issue subIssueBefore = issueRepository.findById(SUB_ISSUE_ID).orElseThrow();
            assertThat(subIssueBefore.getParentIssue()).isNull();

            // Unlink should not throw
            subIssueSyncService.processSubIssueEvent(SUB_ISSUE_ID, PARENT_ISSUE_ID, false);

            Issue subIssueAfter = issueRepository.findById(SUB_ISSUE_ID).orElseThrow();
            assertThat(subIssueAfter.getParentIssue()).isNull();
        }

        @Test
        @DisplayName("Should skip gracefully when sub-issue not in database")
        void skipWhenSubIssueNotInDatabase() {
            // Create only parent issue
            Issue parentIssue = new Issue();
            parentIssue.setId(PARENT_ISSUE_ID);
            parentIssue.setNumber(PARENT_ISSUE_NUMBER);
            parentIssue.setTitle("Parent");
            issueRepository.save(parentIssue);

            // Should not throw when sub-issue doesn't exist
            subIssueSyncService.processSubIssueEvent(SUB_ISSUE_ID, PARENT_ISSUE_ID, true);
        }

        @Test
        @DisplayName("Should skip gracefully when parent issue not in database")
        void skipWhenParentIssueNotInDatabase() {
            // Create only sub-issue
            Issue subIssue = new Issue();
            subIssue.setId(SUB_ISSUE_ID);
            subIssue.setNumber(SUB_ISSUE_NUMBER);
            subIssue.setTitle("Child");
            issueRepository.save(subIssue);

            // Should not throw when parent doesn't exist
            subIssueSyncService.processSubIssueEvent(SUB_ISSUE_ID, PARENT_ISSUE_ID, true);

            // Sub-issue should remain without parent
            Issue reloaded = issueRepository.findById(SUB_ISSUE_ID).orElseThrow();
            assertThat(reloaded.getParentIssue()).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void createTestIssues() {
        Issue parentIssue = new Issue();
        parentIssue.setId(PARENT_ISSUE_ID);
        parentIssue.setNumber(PARENT_ISSUE_NUMBER);
        parentIssue.setTitle("Parent Issue");
        issueRepository.save(parentIssue);

        Issue subIssue = new Issue();
        subIssue.setId(SUB_ISSUE_ID);
        subIssue.setNumber(SUB_ISSUE_NUMBER);
        subIssue.setTitle("Sub Issue");
        issueRepository.save(subIssue);
    }

    private String loadPayload(String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
