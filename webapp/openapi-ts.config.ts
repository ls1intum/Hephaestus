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
		},
		// The transformers plugin only *emits* `transformers.gen.ts`; the SDK ignores it unless asked.
		// Without this the generated types promise `Date` while the fetch client hands back the raw
		// ISO string, so anything typed against the client (`.toLocaleDateString()`) throws on the
		// first real response while every `new Date(…)` fixture stays green.
		{
			name: "@hey-api/sdk",
			transformer: true
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
