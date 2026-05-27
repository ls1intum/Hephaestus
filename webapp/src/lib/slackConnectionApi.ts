import environment from "@/environment";
import { keycloakService } from "@/integrations/auth";

// Slack-specific Connection endpoints excluded from openapi-ts codegen
// (springdoc-3 sealed-type bug). See webapp/openapi-ts.config.ts for the exclusion list.

async function authFetch(path: string, init?: RequestInit): Promise<Response> {
	await keycloakService.updateToken(60).catch(() => undefined);
	const token = keycloakService.getToken();
	return fetch(`${environment.serverUrl}${path}`, {
		...init,
		headers: {
			...(init?.body ? { "Content-Type": "application/json" } : {}),
			...(token ? { Authorization: `Bearer ${token}` } : {}),
			...init?.headers,
		},
	});
}

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
	const response = await authFetch(`/api/v1/workspaces/${workspaceId}/connections`, {
		method: "POST",
		body: JSON.stringify({ kind: "SLACK", userInput: {} }),
	});
	if (!response.ok) {
		throw new Error(`Slack OAuth initiate failed (HTTP ${response.status})`);
	}
	return (await response.json()) as InitiateConnectionResponse;
}

export type SlackTestMessageResponse = {
	ok: boolean;
	channelId?: string | null;
	slackError?: string | null;
};

export async function sendSlackTestMessage(workspaceId: number): Promise<SlackTestMessageResponse> {
	const response = await authFetch(
		`/api/v1/workspaces/${workspaceId}/connections/slack/test-message`,
		{ method: "POST" },
	);
	// 200 → success; 4xx Slack user-error / 502 transport → structured failure body with slackError.
	// Only treat genuine failures (no JSON body) as an exception.
	const contentType = response.headers.get("Content-Type") ?? "";
	if (contentType.includes("application/json")) {
		return (await response.json()) as SlackTestMessageResponse;
	}
	throw new Error(`Slack test-message failed (HTTP ${response.status})`);
}
