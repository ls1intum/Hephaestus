package de.tum.cit.aet.hephaestus.integration.core.sync.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncStateChangedEvent;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit coverage for the {@code SyncStateChangedEvent} → {@link SyncEventHub} bridge: the
 * NATS-absent in-process fallback, the NATS-present publish path, and the inbound-message handler
 * that is the ONLY path allowed to deliver locally when NATS is available (never double-delivered
 * from the publish side — see class javadoc on {@link SyncPushService}).
 */
class SyncPushServiceTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;
    private static final long CONNECTION_ID = 7L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private SyncEventHub hub;

    @Mock
    private Connection connection;

    @Mock
    private Dispatcher dispatcher;

    private SyncStateChangedEvent someEvent() {
        return new SyncStateChangedEvent(
            WORKSPACE_ID,
            CONNECTION_ID,
            IntegrationKind.GITHUB,
            SyncStateChangedEvent.Scope.JOB
        );
    }

    @Test
    void withoutNats_onSyncStateChanged_deliversDirectlyInProcess() {
        ObjectProvider<Connection> provider = objectProviderReturning(null);
        SyncPushService service = new SyncPushService(hub, MAPPER, provider);

        service.onSyncStateChanged(someEvent());

        ArgumentCaptor<SyncEventHint> captor = ArgumentCaptor.forClass(SyncEventHint.class);
        verify(hub, times(1)).publish(eq(WORKSPACE_ID), captor.capture());
        assertThat(captor.getValue().scope()).isEqualTo("job");
        assertThat(captor.getValue().connectionId()).isEqualTo(CONNECTION_ID);
        assertThat(captor.getValue().kind()).isEqualTo("GITHUB");
        verify(connection, never()).publish(anyString(), any(byte[].class));
    }

    @Test
    void withoutNats_neverTouchesNatsConnection() {
        ObjectProvider<Connection> provider = objectProviderReturning(null);
        new SyncPushService(hub, MAPPER, provider);
        // Constructor must not attempt to create a dispatcher when the connection is absent/null.
        verify(connection, never()).createDispatcher(any(MessageHandler.class));
    }

    @Test
    void withNats_subscribesToWildcardSubjectOnConstruction() {
        when(connection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);
        ObjectProvider<Connection> provider = objectProviderReturning(connection);

        new SyncPushService(hub, MAPPER, provider);

        verify(dispatcher).subscribe("hephaestus.syncstatus.>");
    }

    @Test
    void withNats_onSyncStateChanged_publishesToNatsAndDoesNotDeliverLocally() {
        when(connection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);
        ObjectProvider<Connection> provider = objectProviderReturning(connection);
        SyncPushService service = new SyncPushService(hub, MAPPER, provider);

        service.onSyncStateChanged(someEvent());

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(connection, times(1)).publish(eq("hephaestus.syncstatus.42"), payloadCaptor.capture());
        SyncEventHint published = MAPPER.readValue(payloadCaptor.getValue(), SyncEventHint.class);
        assertThat(published.scope()).isEqualTo("job");
        assertThat(published.connectionId()).isEqualTo(CONNECTION_ID);
        assertThat(published.kind()).isEqualTo("GITHUB");

        // The origin replica must NOT also deliver locally — only the NATS subscription does.
        verify(hub, never()).publish(any(Long.class), any(SyncEventHint.class));
    }

    @Test
    void onMessage_deliversToHubExactlyOnce() {
        when(connection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);
        ObjectProvider<Connection> provider = objectProviderReturning(connection);
        SyncPushService service = new SyncPushService(hub, MAPPER, provider);

        SyncEventHint hint = new SyncEventHint("resources", CONNECTION_ID, "GITLAB");
        byte[] payload = MAPPER.writeValueAsBytes(hint);
        Message message = mock(Message.class);
        when(message.getSubject()).thenReturn("hephaestus.syncstatus.42");
        when(message.getData()).thenReturn(payload);

        service.onMessage(message);

        verify(hub, times(1)).publish(eq(WORKSPACE_ID), eq(hint));
    }

    @Test
    void onMessage_malformedSubject_isLoggedNotThrown() {
        ObjectProvider<Connection> provider = objectProviderReturning(null);
        SyncPushService service = new SyncPushService(hub, MAPPER, provider);

        Message message = mock(Message.class);
        when(message.getSubject()).thenReturn("not.the.right.prefix");

        service.onMessage(message); // must not throw — malformed subject is caught + logged

        verify(hub, never()).publish(any(Long.class), any(SyncEventHint.class));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Connection> objectProviderReturning(Connection connection) {
        ObjectProvider<Connection> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(connection);
        return provider;
    }
}
