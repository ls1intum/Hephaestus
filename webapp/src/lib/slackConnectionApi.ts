import { client } from "@/api/client.gen";

// Slack-specific Connection endpoints excluded from openapi-ts codegen
// (springdoc-3 sealed-type bug — see webapp/openapi-ts.config.ts).
// Going through the shared `client` reuses main.tsx's Bearer-token + refresh
// interceptor and the configured baseUrl. The `unknown` generic dodges hey-api's
// grouped-response type indirection; we cast the body shape at the boundary.

// Mirrors server-side InitiateConnectionResponse sealed type. The @JsonTypeInfo
// discriminator emits lowercase names ("redirect" / "linked") and the Redirect
// variant carries `vendorUrl` (not `url`). Match exactly.
export type InitiateConnectionRedirect = { type: "redirect"; vendorUrl: string; state: string };
export type InitiateConnectionLinked = { type: "linked"; connectionId: number };
export type InitiateConnectionResponse = InitiateConnectionRedirect | InitiateConnectionLinked;

export async function initiateSlackConnection(
	workspaceId: number,
): Promise<InitiateConnectionResponse> {
	// userInput is empty for Slack — the strategy needs no extra inputs at initiate time.
	const result = await client.post({
		url: `/api/v1/workspaces/${workspaceId}/connections`,
		body: { kind: "SLACK", userInput: {} },
		throwOnError: true,
	});
	return result.data as InitiateConnectionResponse;
}

export type SlackTestMessageResponse = {
	ok: boolean;
	channelId?: string | null;
	slackError?: string | null;
};

export async function sendSlackTestMessage(workspaceId: number): Promise<SlackTestMessageResponse> {
	// 200 → success, 4xx Slack user-error, 502 transport — all return a structured
	// SlackTestMessageResponse JSON body. Only network/parse failures throw.
	const result = await client.post({
		url: `/api/v1/workspaces/${workspaceId}/connections/slack/test-message`,
	});
	if (result.data !== undefined) {
		return result.data as SlackTestMessageResponse;
	}
	if (result.error !== undefined) {
		return result.error as SlackTestMessageResponse;
	}
	throw new Error(`Slack test-message failed (HTTP ${result.response?.status ?? "unknown"})`);
}
