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

	it("rejects names that do not carry the stable Slack id", () => {
		expect(parseSlackChannelReference("#team-standup")).toBeNull();
	});
});
