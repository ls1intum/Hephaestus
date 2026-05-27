package de.tum.cit.aet.hephaestus.integration.core.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.integration.scm.github.webhook.GithubSubjectParser;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.webhook.GitlabSubjectParser;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandlerRegistry;
import de.tum.cit.aet.hephaestus.integration.outline.webhook.OutlineSubjectParser;
import de.tum.cit.aet.hephaestus.integration.slack.webhook.SlackSubjectParser;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SubjectParser;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Message;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IntegrationMessageDispatcher subject routing")
class IntegrationMessageDispatcherTest extends BaseUnitTest {

    private static final List<SubjectParser> ALL_PARSERS = List.of(
        new GithubSubjectParser(),
        new GitlabSubjectParser(),
        new SlackSubjectParser(),
        new OutlineSubjectParser()
    );

    @Test
    void githubSubjectWithoutHandlerReturnsEmpty() {
        IntegrationMessageDispatcher dispatcher = new IntegrationMessageDispatcher(
            new IntegrationMessageHandlerRegistry(List.of()),
            ALL_PARSERS
        );

        assertThat(dispatcher.dispatch("github.acme.foo.issues")).isEmpty();
    }

    @Test
    void slackSubjectWithoutHandlerReturnsEmpty() {
        IntegrationMessageDispatcher dispatcher = new IntegrationMessageDispatcher(
            new IntegrationMessageHandlerRegistry(List.of()),
            ALL_PARSERS
        );

        assertThat(dispatcher.dispatch("slack.T0123.C456.message")).isEmpty();
    }

    @Test
    void gitlabSubjectWithoutHandlerReturnsEmpty() {
        IntegrationMessageDispatcher dispatcher = new IntegrationMessageDispatcher(
            new IntegrationMessageHandlerRegistry(List.of()),
            ALL_PARSERS
        );

        assertThat(dispatcher.dispatch("gitlab.acme~group.project.push")).isEmpty();
    }

    @Test
    void outlineSubjectWithoutHandlerReturnsEmpty() {
        IntegrationMessageDispatcher dispatcher = new IntegrationMessageDispatcher(
            new IntegrationMessageHandlerRegistry(List.of()),
            ALL_PARSERS
        );

        assertThat(dispatcher.dispatch("outline.workspace.collection.document.publish")).isEmpty();
    }

    @Test
    void unknownPrefixReturnsEmpty() {
        IntegrationMessageDispatcher dispatcher = new IntegrationMessageDispatcher(
            new IntegrationMessageHandlerRegistry(List.of()),
            ALL_PARSERS
        );

        // Explicit allow-list never reflects on input — bitbucket, blank, dot-less, and
        // weird inputs all collapse to empty before any parser is consulted.
        assertThat(dispatcher.dispatch("bitbucket.foo")).isEmpty();
        assertThat(dispatcher.dispatch("bitbucket")).isEmpty();
        assertThat(dispatcher.dispatch("")).isEmpty();
        assertThat(dispatcher.dispatch("github")).isEmpty();
        assertThat(dispatcher.dispatch(".github.acme.foo.issues")).isEmpty();
    }

    @Test
    void malformedSubjectForKnownPrefixReturnsEmpty() {
        IntegrationMessageDispatcher dispatcher = new IntegrationMessageDispatcher(
            new IntegrationMessageHandlerRegistry(List.of()),
            ALL_PARSERS
        );

        // The prefix matches, but the parser rejects the structure — the dispatcher
        // must absorb the IllegalArgumentException and return empty so the consumer
        // can ACK rather than crash.
        assertThat(dispatcher.dispatch("github.acme.foo")).isEmpty(); // < 4 components
        assertThat(dispatcher.dispatch("slack.only.three")).isEmpty();
    }

    @Test
    void dispatchResolvesRegisteredHandler() {
        RecordingHandler handler = new RecordingHandler(new EventTypeKey(IntegrationKind.GITHUB, "repository.issues"));
        IntegrationMessageDispatcher dispatcher = new IntegrationMessageDispatcher(
            new IntegrationMessageHandlerRegistry(List.of(handler)),
            ALL_PARSERS
        );

        Optional<IntegrationMessageHandler> resolved = dispatcher.dispatch("github.acme.foo.issues");
        assertThat(resolved).contains(handler);
    }

    @Test
    void duplicateSubjectParserForSameKindFailsAtConstruction() {
        SubjectParser first = new GithubSubjectParser();
        SubjectParser second = new GithubSubjectParser();
        IntegrationMessageHandlerRegistry emptyRegistry = new IntegrationMessageHandlerRegistry(List.of());

        assertThatThrownBy(() -> new IntegrationMessageDispatcher(emptyRegistry, List.of(first, second)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate SubjectParser")
            .hasMessageContaining("GITHUB")
            .hasMessageContaining(GithubSubjectParser.class.getName());
    }

    @Test
    void prefixLookupIsCaseInsensitiveAndAllowListed() {
        // Static helper exposed package-private so we can pin its allow-list semantics
        // without spinning up the full dispatcher.
        assertThat(IntegrationMessageDispatcher.kindFromSubjectPrefix("GITHUB.acme.foo.issues")).contains(
            IntegrationKind.GITHUB
        );
        assertThat(IntegrationMessageDispatcher.kindFromSubjectPrefix("Gitlab.x.y.z")).contains(IntegrationKind.GITLAB);
        assertThat(IntegrationMessageDispatcher.kindFromSubjectPrefix("bitbucket.x.y.z")).isEmpty();
        assertThat(IntegrationMessageDispatcher.kindFromSubjectPrefix(null)).isEmpty();
        assertThat(IntegrationMessageDispatcher.kindFromSubjectPrefix("")).isEmpty();
        assertThat(IntegrationMessageDispatcher.kindFromSubjectPrefix("github")).isEmpty();
    }

    private static class RecordingHandler implements IntegrationMessageHandler {

        private final EventTypeKey key;
        private final java.util.List<Message> received = new java.util.ArrayList<>();

        RecordingHandler(EventTypeKey key) {
            this.key = key;
        }

        @Override
        public EventTypeKey key() {
            return key;
        }

        @Override
        public void onMessage(Message msg) {
            received.add(msg);
        }
    }
}
