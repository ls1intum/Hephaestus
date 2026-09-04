import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import { automaticPromotion } from "./automatic-promotion.ts";
import { parseChannel } from "./reconcile-deployment.ts";

const current = "a".repeat(40);
const next = "b".repeat(40);
const channel = parseChannel({
	commit: current,
	images: { HEPHAESTUS_IMAGE_WEBAPP: `ghcr.io/example/webapp@sha256:${"c".repeat(64)}` },
});

await test("automatic promotion preserves a hold without consulting commit history", async () => {
	assert.equal(
		await automaticPromotion({ ...channel, freeze: true }, next, () => {
			throw new Error("a frozen channel must not query history");
		}),
		false,
	);
});

await test("automatic promotion advances but never replays or rewinds a completed build", async () => {
	for (const [status, expected] of [
		["ahead", true],
		["identical", false],
		["behind", false],
	] as const) {
		assert.equal(
			await automaticPromotion(channel, next, (base, head) => {
				assert.equal(base, current);
				assert.equal(head, next);
				return Promise.resolve(status);
			}),
			expected,
		);
	}
});

await test("unknown history and API failures cannot authorize promotion", async () => {
	for (const status of ["diverged", "unexpected"])
		await assert.rejects(
			automaticPromotion(channel, next, () => Promise.resolve(status)),
			/history/,
		);
	await assert.rejects(
		automaticPromotion(channel, next, () => Promise.reject(new Error("unavailable"))),
		/unavailable/,
	);
	await assert.rejects(
		automaticPromotion(channel, "main", () => Promise.resolve("ahead")),
		/full commit SHA/,
	);
});

await test("switching from a release to main cannot replay or rewind the release's source", async () => {
	const release = parseChannel({ release: "v1.2.3" });
	for (const [status, expected] of [
		["ahead", true],
		["identical", false],
		["behind", false],
	] as const) {
		assert.equal(
			await automaticPromotion(release, next, (base, head) => {
				assert.equal(base, "v1.2.3");
				assert.equal(head, next);
				return Promise.resolve(status);
			}),
			expected,
		);
	}
	await assert.rejects(
		automaticPromotion(release, next, () => Promise.reject(new Error("unknown release"))),
		/unknown release/,
	);
});

await test("the CLI fails closed for invalid channels but honors a valid hold without network access", (t) => {
	const directory = mkdtempSync(join(tmpdir(), "automatic-promotion-"));
	t.after(() => rmSync(directory, { recursive: true, force: true }));
	mkdirSync(join(directory, "deploy-state/channels"), { recursive: true });
	const outputFile = join(directory, "output");
	for (const contents of [undefined, "{", JSON.stringify({ release: "v1.2.3", freeze: "true" })]) {
		if (contents !== undefined)
			writeFileSync(join(directory, "deploy-state/channels/staging.json"), contents);
		const result = spawnSync(
			process.execPath,
			[fileURLToPath(new URL("./automatic-promotion.ts", import.meta.url))],
			{
				cwd: directory,
				encoding: "utf8",
				env: {
					...process.env,
					CHANNEL: "Staging",
					COMMIT: next,
					GITHUB_REPOSITORY: "example/repo",
					GITHUB_OUTPUT: outputFile,
				},
			},
		);
		assert.notEqual(result.status, 0, result.stderr);
		assert.equal(existsSync(outputFile), false);
	}
	writeFileSync(
		join(directory, "deploy-state/channels/staging.json"),
		JSON.stringify({ release: "v1.2.3", freeze: true }),
	);
	const held = spawnSync(
		process.execPath,
		[fileURLToPath(new URL("./automatic-promotion.ts", import.meta.url))],
		{
			cwd: directory,
			encoding: "utf8",
			env: {
				...process.env,
				PATH: "",
				CHANNEL: "Staging",
				COMMIT: next,
				GITHUB_REPOSITORY: "example/repo",
				GITHUB_OUTPUT: outputFile,
			},
		},
	);
	assert.equal(held.status, 0, held.stderr);
	assert.equal(readFileSync(outputFile, "utf8"), "apply=false\n");
});
