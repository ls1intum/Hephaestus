package de.tum.cit.aet.hephaestus.integration.slack.domain;

import java.util.Collection;
import java.util.List;

public interface SlackMessageAccess {
    List<SlackMessageKeyRow> findReadableMessageKeysByWorkspaceId(
            long workspaceId, Collection<SlackMessageKeyRow> messages);
}
