import { describe, expect, it } from "vitest";
import { parseSlackChannelReference } from "./slack-channel-reference";

describe("parseSlackChannelReference", () => {
	it("accepts raw Slack channel ids", () => {
		expect(parseSlackChannelReference(" C0974LJBPBK ")).toEqual({
			channelId: "C0974LJBPBK",
		});
	});

	it("extracts ids and names from Slack channel mentions", () => {
		expect(parseSlackChannelReference("<#C0974LJBPBK|team-standup>")).toEqual({
			channelId: "C0974LJBPBK",
			channelName: "team-standup",
		});
	});

	it("extracts ids from Slack channel URLs", () => {
		expect(parseSlackChannelReference("https://example.slack.com/archives/C0974LJBPBK")).toEqual({
			channelId: "C0974LJBPBK",
		});
	});

	it("extracts ids from an archive URL pasted alongside other text", () => {
		expect(
			parseSlackChannelReference("here it is: https://example.slack.com/archives/G0974LJBPBK/p123"),
		).toEqual({ channelId: "G0974LJBPBK" });
	});

	it("rejects names that do not carry the stable Slack id", () => {
		expect(parseSlackChannelReference("#team-standup")).toBeNull();
	});

	it("does not mistake a shouted, all-caps prose word for a channel id", () => {
		// Starts with C and is 11 uppercase/digit chars — matches the old unanchored pattern,
		// but it is not a Slack archive URL, so it must not be extracted.
		expect(parseSlackChannelReference("See CHANNELS123 for details")).toBeNull();
	});
});
