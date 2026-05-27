import environment from "@/environment";
import { keycloakService } from "@/integrations/auth";

/**
 * Thin wrappers around the Slack-related Connection endpoints that are
 * excluded from openapi-ts codegen.
 *
 *  - {@code POST /api/v1/workspaces/{workspaceId}/connections} — initiate OAuth.
 *    Excluded from codegen because its response body is a sealed type
 *    (Redirect / Linked) that springdoc-3 doesn't emit as a named schema.
 *  - {@code POST /api/v1/workspaces/{workspaceId}/connections/slack/test-message}
 *    — connectivity probe. Excluded for the same nested-record reason.
 *
 * Channel + team + enabled config still flows through the existing
 * generated {@code updateNotifications} mutation.
 */

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

export type InitiateConnectionRedirect = { type: "REDIRECT"; url: string };
export type InitiateConnectionLinked = { type: "LINKED"; connectionId: number };
export type InitiateConnectionResponse = InitiateConnectionRedirect | InitiateConnectionLinked;

export async function initiateSlackConnection(
	workspaceId: number,
): Promise<InitiateConnectionResponse> {
	const response = await authFetch(`/api/v1/workspaces/${workspaceId}/connections`, {
		method: "POST",
		body: JSON.stringify({ kind: "SLACK" }),
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
	// 200 → success; 502 carries structured failure with slackError; everything else is a real fault.
	if (response.status === 200 || response.status === 502) {
		return (await response.json()) as SlackTestMessageResponse;
	}
	throw new Error(`Slack test-message failed (HTTP ${response.status})`);
}
