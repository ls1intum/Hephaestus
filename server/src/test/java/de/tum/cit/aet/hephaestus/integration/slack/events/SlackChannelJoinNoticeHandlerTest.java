package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.channel.SlackConsentBlocks;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Just-in-time join-notice unit tests. Deterministic (all collaborators mocked): the ephemeral consent notice is
 * posted ONLY for a real member joining an actively-ingested channel — never for a non-active channel, an unknown
 * workspace, or the bot's own join. Each test would fail if its gate were removed.
 */
class SlackChannelJoinNoticeHandlerTest extends BaseUnitTest {

    private static final String TEAM = "T1";
    private static final long WORKSPACE_ID = 42L;
    private static final String CHANNEL = "C1";
    private static final String JOINER = "U1";
    private static final String BOT = "Ubot";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Mock
    private SlackWorkspaceResolver workspaceResolver;

    @Mock
    private SlackChannelConsentGate consentGate;

    @Mock
    private SlackMessageService messageService;

    private SlackChannelJoinNoticeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SlackChannelJoinNoticeHandler(workspaceResolver, consentGate, messageService);
    }

    private JsonNode joinEvent(String userId, String channelId) {
        ObjectNode event = mapper.createObjectNode();
        event.put("type", "member_joined_channel");
        event.put("user", userId);
        event.put("channel", channelId);
        event.put("channel_type", "C");
        return event;
    }

    @Test
    void activeChannel_realJoiner_postsEphemeralConsentNotice() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE_ID));
        when(consentGate.ingestAllowed(WORKSPACE_ID, CHANNEL)).thenReturn(true);
        when(messageService.resolveBotUserId(WORKSPACE_ID)).thenReturn(Optional.of(BOT));

        handler.onMemberJoined(TEAM, joinEvent(JOINER, CHANNEL));

        verify(messageService).sendEphemeralForWorkspace(
            eq(WORKSPACE_ID),
            eq(CHANNEL),
            eq(JOINER),
            anyList(),
            eq(SlackConsentBlocks.FALLBACK_TEXT)
        );
    }

    @Test
    void nonActiveChannel_noOp_andNeverResolvesBotOrPosts() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE_ID));
        when(consentGate.ingestAllowed(WORKSPACE_ID, CHANNEL)).thenReturn(false);

        handler.onMemberJoined(TEAM, joinEvent(JOINER, CHANNEL));

        // Gate short-circuits BEFORE the remote auth.test and before any post — nothing is disclosed where nothing
        // is being read.
        verify(messageService, never()).resolveBotUserId(WORKSPACE_ID);
        verify(messageService, never()).sendEphemeralForWorkspace(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            anyList(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void botOwnJoin_isIgnored() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.of(WORKSPACE_ID));
        when(consentGate.ingestAllowed(WORKSPACE_ID, CHANNEL)).thenReturn(true);
        when(messageService.resolveBotUserId(WORKSPACE_ID)).thenReturn(Optional.of(BOT));

        // The joiner IS the app's own bot user (adding the app to a channel fires member_joined_channel too).
        handler.onMemberJoined(TEAM, joinEvent(BOT, CHANNEL));

        verify(messageService, never()).sendEphemeralForWorkspace(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            anyList(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void unknownWorkspace_noOp() {
        when(workspaceResolver.resolveWorkspaceId(TEAM)).thenReturn(Optional.empty());

        handler.onMemberJoined(TEAM, joinEvent(JOINER, CHANNEL));

        verifyNoInteractions(consentGate);
        verify(messageService, never()).resolveBotUserId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void blankJoinerOrChannel_noOp() {
        handler.onMemberJoined(TEAM, joinEvent("", CHANNEL));
        handler.onMemberJoined(TEAM, joinEvent(JOINER, ""));

        verifyNoInteractions(workspaceResolver, consentGate, messageService);
    }
}
