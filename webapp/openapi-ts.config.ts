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
	// The connection-registry admin endpoints reference nested records that springdoc 3.0
	// doesn't surface as named component schemas (sealed types like
	// InitiateConnectionResponse with Redirect/Linked subtypes, ReasonRequest). They have
	// no webapp consumer yet — the connection-management UI lives behind
	// ConnectionService/admin work that lands later. Exclude them so the client generation
	// isn't blocked by the dangling $ref. Re-include once the records either move to
	// top-level or get explicit @Schema(name=...) wiring.
	parser: {
		filters: {
			operations: {
				exclude: [
					"POST /workspaces/{workspaceSlug}/mentor/chat",
					"GET /api/v1/workspaces/{workspaceId}/connections",
					"POST /api/v1/workspaces/{workspaceId}/connections",
					"GET /api/v1/workspaces/{workspaceId}/connections/{id}",
					"GET /api/v1/workspaces/{workspaceId}/connections/{id}/audit",
					"POST /api/v1/workspaces/{workspaceId}/connections/{id}/disconnect",
					"POST /api/v1/workspaces/{workspaceId}/connections/{id}/reactivate",
					"POST /api/v1/workspaces/{workspaceId}/connections/{id}/suspend",
					"PATCH /api/v1/workspaces/{workspaceId}/connections/slack/notification-channel",
					"POST /api/v1/workspaces/{workspaceId}/connections/slack/test-message"
				]
			}
		}
	}
});
