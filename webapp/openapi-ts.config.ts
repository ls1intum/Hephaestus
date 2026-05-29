import { defaultPlugins, defineConfig } from "@hey-api/openapi-ts";

export default defineConfig({
	input: "../server/openapi.yaml",
	output: "src/api",
	plugins: [
		...defaultPlugins,
		"@hey-api/client-fetch",
		"@tanstack/react-query",
		{
			dates: true,
			bigInt: false,
			name: "@hey-api/transformers"
		}
	],
	// Exclude SSE endpoints - openapi-ts react-query plugin doesn't handle them correctly
	// (tries to destructure 'data' from ServerSentEventsResult which has 'stream' instead)
	// The mentor chat uses a custom transport in useMentorChat.ts.
	//
	// The Slack connection helper endpoint still flows through the thin
	// `src/lib/slackConnectionApi.ts` wrapper (custom SlackTestMessageResponse shape +
	// SLACK-only initiate), so test-message stays excluded. The generic connection-registry
	// admin endpoints (list / initiate / read / audit / suspend / reactivate / disconnect)
	// are now generated: the sealed InitiateConnectionResponse and ReasonRequest records
	// carry explicit @Schema(name=...) wiring on the server, so they surface as named
	// component schemas and codegen resolves the $refs cleanly. They back the
	// workspace-admin Login-providers UI.
	parser: {
		filters: {
			operations: {
				exclude: [
					"POST /workspaces/{workspaceSlug}/mentor/chat",
					"POST /api/v1/workspaces/{workspaceId}/connections/slack/test-message"
				]
			}
		}
	}
});
