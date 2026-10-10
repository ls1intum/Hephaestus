import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";

function runEntrypoint(enabled: boolean) {
	const directory = mkdtempSync(path.join(os.tmpdir(), "workspace-address-entrypoint-"));
	try {
		const html = path.join(directory, "html");
		const script = readFileSync("webapp/docker/entrypoint.sh", "utf8")
			.replaceAll("/usr/share/nginx/html", html)
			.replaceAll("/app/src/environment/index.ts", path.resolve("webapp/src/environment/index.ts"));
		const result = spawnSync("bash", [], {
			input: script,
			encoding: "utf8",
			timeout: 10_000,
			env: {
				PATH: process.env.PATH,
				HEPHAESTUS_WORKSPACE_SUBDOMAINS_ENABLED: String(enabled),
				APPLICATION_CLIENT_URL: "",
			},
		});
		return { ...result, wroteConfig: existsSync(path.join(html, ".well-known/hephaestus.json")) };
	} finally {
		rmSync(directory, { recursive: true, force: true });
	}
}

void test("workspace addresses require an apex URL before the container writes runtime files", () => {
	const result = runEntrypoint(true);
	assert.equal(result.status, 1);
	assert.match(result.stderr, /APPLICATION_CLIENT_URL is required/u);
	assert.equal(result.wroteConfig, false);
});

void test("the container accepts an empty client URL with workspace addresses off", () => {
	const result = runEntrypoint(false);
	assert.equal(result.status, 0, result.stderr);
	assert.equal(result.wroteConfig, true);
});
