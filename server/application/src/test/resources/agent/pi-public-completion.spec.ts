import assert from "node:assert/strict";
import { once } from "node:events";
import { createServer, type Server } from "node:http";
import { test } from "node:test";

import { completePublicReview } from "../../../main/resources/agent/pi-public-completion.ts";

const digest = "a".repeat(64);
const review = { summary: null, inline: [], withheld: [] };

async function listen(server: Server): Promise<string> {
	server.listen(0, "127.0.0.1");
	await once(server, "listening");
	const address = server.address();
	assert.ok(address !== null && typeof address !== "string");
	return `http://127.0.0.1:${address.port}/internal/llm/complete-public-review`;
}

void test("lost acknowledgement replays identical bytes, including a quiet review", async (t) => {
	const bodies: string[] = [];
	const requestReview = {
		...review,
		withheld: [{ basedOn: ["00000000-0000-4000-8000-000000000011"], reason: "BELOW_BAR" }],
	};
	const original = structuredClone(requestReview);
	const server = createServer((request, response) => {
		assert.equal(request.url, "/internal/llm/complete-public-review");
		assert.equal(request.headers.authorization, "Bearer synthetic-token");
		let body = "";
		request.on("data", (chunk: unknown) => {
			assert.ok(Buffer.isBuffer(chunk));
			body += chunk.toString("utf8");
		});
		request.on("end", () => {
			bodies.push(body);
			if (bodies.length === 1) {
				const withheld = requestReview.withheld[0];
				assert.ok(withheld !== undefined);
				withheld.reason = "NO_MATERIAL_CHANGE";
				response.destroy();
				return;
			}
			response.setHeader("Content-Type", "application/json");
			response.end(JSON.stringify({ schemaVersion: 1, accepted: true, reviewDigest: digest }));
		});
	});
	t.after(() => {
		server.closeAllConnections();
		server.close();
	});
	const url = await listen(server);
	const result = await completePublicReview({
		url,
		token: "synthetic-token",
		admissionDigest: digest,
		review: requestReview,
		timeoutMs: 10_000,
	});
	assert.deepEqual(result, { state: "ACCEPTED", reviewDigest: digest });
	assert.equal(bodies.length, 2);
	assert.equal(bodies[0], bodies[1]);
	assert.deepEqual(JSON.parse(bodies[0] ?? "null"), {
		schemaVersion: 1,
		admissionDigest: digest,
		review: original,
	});
});

void test("an explicit refusal, including an unreadable HTTP refusal, is final", async (t) => {
	for (const status of [200, 403]) {
		let calls = 0;
		const server = createServer((_request, response) => {
			calls += 1;
			response.writeHead(status, { "Content-Type": "application/json" });
			response.end(
				status === 200
					? JSON.stringify({ schemaVersion: 1, accepted: false, reasons: ["Refused"] })
					: "not JSON",
			);
		});
		t.after(() => {
			server.closeAllConnections();
			server.close();
		});
		const url = await listen(server);
		assert.deepEqual(
			await completePublicReview({
				url,
				token: "synthetic-token",
				admissionDigest: digest,
				review,
				timeoutMs: 10_000,
			}),
			{ state: "REFUSED" },
		);
		assert.equal(calls, 1);
	}
});

void test("an unanswered request remains unavailable without retrying a server still processing it", async (t) => {
	let calls = 0;
	const server = createServer(() => {
		calls += 1;
	});
	t.after(() => {
		server.closeAllConnections();
		server.close();
	});
	const url = await listen(server);
	assert.deepEqual(
		await completePublicReview({
			url,
			token: "synthetic-token",
			admissionDigest: digest,
			review,
			timeoutMs: 500,
		}),
		{ state: "UNAVAILABLE" },
	);
	assert.equal(calls, 1);
});
