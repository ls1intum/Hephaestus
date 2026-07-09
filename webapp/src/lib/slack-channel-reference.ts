const SLACK_CHANNEL_ID = /^[CG][A-Z0-9]{8,}$/;
const SLACK_CHANNEL_ID_IN_TEXT = /[CG][A-Z0-9]{8,}/;
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

	const mention = SLACK_MENTION.exec(trimmed);
	if (mention) {
		return {
			channelId: mention[1],
			channelName: mention[2],
		};
	}

	const id = SLACK_CHANNEL_ID_IN_TEXT.exec(trimmed)?.[0];
	return id ? { channelId: id } : null;
}
