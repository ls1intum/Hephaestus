import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { serve } from "@hono/node-server";
import createApp from "@/lib/create-app";
import mentor from "@/routes/mentor/index";
import env from "@/env";
import { pool } from "@/db";

// In tests, env.ts already provides test-friendly stubs for the model

let server: ReturnType<typeof serve> | undefined;
let dbReady = false;

describe("mentor chat", () => {
	beforeAll(async () => {
		try {
			await pool.query("select 1 from chat_thread limit 1");
			await pool.query("select 1 from chat_message limit 1");
			dbReady = true;
		} catch {
			dbReady = false;
		}
		const testApp = createApp();
		testApp.route("/", mentor);
		server = serve({ fetch: testApp.fetch, port: env.PORT });
	});

	afterAll(async () => {
		server?.close?.();
		await pool.end();
	});

	it("POST /mentor/chat streams and persists", async () => {
		if (!dbReady) {
			// eslint-disable-next-line no-console
			console.warn("[mentor-chat.test] Skipping: DB schema not ready");
			return;
		}
		// Use valid UUIDs: version 4 (third group starts with 4), variant 8/9/a/b (fourth group starts with 8)
		const threadId = "11111111-2222-4111-8111-111111111111";
		const messageId = "22222222-3333-4111-8111-222222222222";

		const res = await fetch(`http://localhost:${env.PORT}/mentor/chat`, {
			method: "POST",
			headers: {
				"content-type": "application/json",
				accept: "text/event-stream",
			},
			body: JSON.stringify({
				id: threadId,
				message: {
					id: messageId,
					role: "user",
					parts: [{ type: "text", text: "Hi test" }],
				},
			}),
		});

		const bodyText = await res.text();
		if (res.status !== 200) {
			// Log for diagnostics in CI
			// eslint-disable-next-line no-console
			console.error("POST /mentor/chat failed", res.status, bodyText);
		}
		expect(res.status).toBe(200);
		const text = bodyText;
		expect(text).toContain("data:");

		const res2 = await fetch(
			`http://localhost:${env.PORT}/mentor/threads/${threadId}`,
		);
		expect(res2.status).toBe(200);
		const json = await res2.json();
		expect(json).toHaveProperty("messages");
		expect(Array.isArray(json.messages)).toBe(true);
		expect(
			(json.messages as Array<{ id: string }>).some((m) => m.id === messageId),
		).toBe(true);
	});
});
