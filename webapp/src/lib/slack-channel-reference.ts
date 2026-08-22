const SLACK_CHANNEL_ID = /^[CG][A-Z0-9]{8,}$/;
// Anchored to a Slack archive URL path segment so an unrelated all-caps word pasted in prose
// (e.g. shouting "PLEASE HELP ASAP") can't be mistaken for a channel id.
const SLACK_CHANNEL_ID_IN_TEXT = /\/archives\/([CG][A-Z0-9]{8,})(?:[/?#]|$)/;
const SLACK_MENTION = /^<#([CG][A-Z0-9]{8,})(?:\|([^>]+))?>$/;

export type SlackChannelReference = {
	channelId: string;
	channelName?: string;
};

export function parseSlackChannelReference(value: string): SlackChannelReference | null {
	const trimmed = value.trim();
	if (SLACK_CHANNEL_ID.test(trimmed)) {
		return { channelId: trimmed };
	}

	const [, mentionId, mentionName] = SLACK_MENTION.exec(trimmed) ?? [];
	if (mentionId !== undefined) {
		return { channelId: mentionId, channelName: mentionName };
	}

	const id = SLACK_CHANNEL_ID_IN_TEXT.exec(trimmed)?.[1];
	return id ? { channelId: id } : null;
}
