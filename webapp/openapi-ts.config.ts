import { defaultPlugins, defineConfig } from "@hey-api/openapi-ts";

export default defineConfig({
	input: "../server/openapi.yaml",
	output: "src/api",
	plugins: [
		...defaultPlugins,
		"@hey-api/client-fetch",
		{
			name: "@tanstack/react-query",
			queryKeys: { tags: true }
		},
		{
			dates: true,
			bigInt: false,
			name: "@hey-api/transformers"
		}
	],
	// Generated query hooks do not support SSE responses; Mentor uses use-mentor-chat.ts.
	parser: {
		filters: {
			operations: {
				exclude: ["POST /workspaces/{workspaceSlug}/mentor/chat"]
			}
		}
	}
});
