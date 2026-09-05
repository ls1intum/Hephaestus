import assert from "node:assert/strict";
import { test } from "node:test";

import { awaitStaging } from "./await-staging.ts";

const commit = "a".repeat(40);
const images = { HEPHAESTUS_IMAGE_WEBAPP: `ghcr.io/o/webapp@sha256:${"1".repeat(64)}` };
const channel = (fields: Record<string, unknown>): string =>
	JSON.stringify({ commit, images, ...fields });
const answers = (...replies: (string | Error)[]): (() => Promise<string>) => {
	const queue = [...replies];
	return () => {
		const reply = queue.shift();
		if (reply === undefined) throw new Error("staging was polled once too often");
		return reply instanceof Error ? Promise.reject(reply) : Promise.resolve(reply);
	};
};
const patient = new AbortController().signal;

await test("returns once staging's channel names the commit", async () => {
	await awaitStaging(commit, answers(channel({})), patient, 0);
});

await test("keeps polling while staging is on an older build or a release", async () => {
	await awaitStaging(
		commit,
		answers(
			channel({ commit: "b".repeat(40) }),
			JSON.stringify({ release: "v1.2.3" }),
			channel({}),
		),
		patient,
		0,
	);
});

await test("a channel the API could not serve is retried, not decided on", async () => {
	await awaitStaging(commit, answers(new Error("HTTP 502"), channel({})), patient, 0);
});

await test("a frozen channel and an unreadable channel fail closed at once", async () => {
	await assert.rejects(
		awaitStaging(commit, answers(channel({ commit: "b".repeat(40), freeze: true })), patient, 0),
		/frozen/,
	);
	await assert.rejects(awaitStaging(commit, answers("{"), patient, 0), SyntaxError);
	await assert.rejects(
		awaitStaging(commit, answers(JSON.stringify({ release: "main" })), patient, 0),
		/immutable vX\.Y\.Z/,
	);
});

await test("gives up at the deadline", async () => {
	const older = channel({ commit: "b".repeat(40) });
	await assert.rejects(
		awaitStaging(commit, () => Promise.resolve(older), AbortSignal.timeout(30), 5),
		/did not reach/,
	);
});
