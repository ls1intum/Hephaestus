package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("GitLabMessageHandlerRegistry")
class GitLabMessageHandlerRegistryTest {

    @Test
    @DisplayName("getHandler returns registered handler for matching event key")
    void getHandler_returnsRegisteredHandler() {
        var handler = new StubMergeRequestHandler();
        var registry = new GitLabMessageHandlerRegistry(new GitLabMessageHandler<?>[] { handler });

        assertThat(registry.getHandler("merge_request")).isSameAs(handler);
    }

    @Test
    @DisplayName("getHandler performs case-insensitive lookup")
    void getHandler_caseInsensitiveLookup() {
        var handler = new StubMergeRequestHandler();
        var registry = new GitLabMessageHandlerRegistry(new GitLabMessageHandler<?>[] { handler });

        assertThat(registry.getHandler("MERGE_REQUEST")).isSameAs(handler);
        assertThat(registry.getHandler("Merge_Request")).isSameAs(handler);
    }

    @Test
    @DisplayName("getHandler returns null for unknown event key")
    void getHandler_returnsNull_forUnknownEvent() {
        var handler = new StubMergeRequestHandler();
        var registry = new GitLabMessageHandlerRegistry(new GitLabMessageHandler<?>[] { handler });

        assertThat(registry.getHandler("nonexistent")).isNull();
    }

    @Test
    @DisplayName("getHandler returns null for null key")
    void getHandler_returnsNull_forNullKey() {
        var registry = new GitLabMessageHandlerRegistry(new GitLabMessageHandler<?>[] {});

        assertThat(registry.getHandler(null)).isNull();
    }

    @Test
    @DisplayName("getHandler returns null for empty string key")
    void getHandler_returnsNull_forEmptyKey() {
        var registry = new GitLabMessageHandlerRegistry(new GitLabMessageHandler<?>[] {});

        assertThat(registry.getHandler("")).isNull();
        assertThat(registry.getHandler("   ")).isNull();
    }

    @Test
    @DisplayName("constructor with empty array creates empty registry")
    void constructor_emptyHandlerArray_createsEmptyRegistry() {
        var registry = new GitLabMessageHandlerRegistry(new GitLabMessageHandler<?>[] {});

        assertThat(registry.getSupportedEvents()).isEmpty();
    }

    @Test
    @DisplayName("getSupportedEvents returns all registered event keys")
    void getSupportedEvents_returnsAllRegisteredKeys() {
        var handler1 = new StubMergeRequestHandler();
        var handler2 = new StubIssueHandler();
        var registry = new GitLabMessageHandlerRegistry(new GitLabMessageHandler<?>[] { handler1, handler2 });

        assertThat(registry.getSupportedEvents()).containsExactlyInAnyOrder("merge_request", "issue");
    }

    @Test
    @DisplayName("duplicate handler registration throws IllegalStateException")
    void constructor_duplicateHandlers_throwsIllegalStateException() {
        var handler1 = new StubMergeRequestHandler();
        var handler2 = new StubMergeRequestHandler();

        assertThatThrownBy(() -> new GitLabMessageHandlerRegistry(new GitLabMessageHandler<?>[] { handler1, handler2 }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate GitLab message handler")
            .hasMessageContaining("merge_request");
    }

    /**
     * Stub handler for testing. Does not use NatsMessageDeserializer or TransactionTemplate
     * since we only need the event type for registry tests.
     */
    private static class StubMergeRequestHandler extends GitLabMessageHandler<String> {

        StubMergeRequestHandler() {
            super(String.class, null, null);
        }

        @Override
        protected void handleEvent(String eventPayload) {
            // no-op for registry tests
        }

        @Override
        public GitLabEventType getEventType() {
            return GitLabEventType.MERGE_REQUEST;
        }
    }

    private static class StubIssueHandler extends GitLabMessageHandler<String> {

        StubIssueHandler() {
            super(String.class, null, null);
        }

        @Override
        protected void handleEvent(String eventPayload) {
            // no-op for registry tests
        }

        @Override
        public GitLabEventType getEventType() {
            return GitLabEventType.ISSUE;
        }
    }
}
