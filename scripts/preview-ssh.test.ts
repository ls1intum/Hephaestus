import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, test } from "node:test";

import {
	buildSshArguments,
	executePreviewSsh,
	parsePreviewSshCommand,
	parseSshConfig,
	type SshRunner,
} from "./preview-ssh.ts";

const PRIVATE_KEY = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----";

function environment(runnerTemp: string): NodeJS.ProcessEnv {
	return {
		PREVIEW_HOST: "staging.example",
		PREVIEW_HOST_KEY: "staging.example ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITest host-comment",
		PREVIEW_SSH_PRIVATE_KEY: PRIVATE_KEY,
		PREVIEW_SSH_USER: "preview_cleanup",
		RUNNER_TEMP: runnerTemp,
	};
}

void describe("preview SSH trust boundary", () => {
	void test("accepts one pinned Ed25519 key for the exact configured host", () => {
		const directory = mkdtempSync(join(tmpdir(), "preview-ssh-config-"));
		try {
			const config = parseSshConfig(environment(directory));
			assert.equal(config.host, "staging.example");
			assert.equal(config.user, "preview_cleanup");
			assert.throws(
				() =>
					parseSshConfig({
						...environment(directory),
						PREVIEW_HOST_KEY: "other.example ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITest",
					}),
				/does not match/,
			);
			assert.throws(
				() =>
					parseSshConfig({
						...environment(directory),
						PREVIEW_HOST_KEY: "staging.example ssh-ed25519 AAAA\nattacker ssh-ed25519 BBBB",
					}),
				/exactly one line/,
			);
		} finally {
			rmSync(directory, { recursive: true, force: true });
		}
	});

	void test("rejects shell syntax and non-canonical PR numbers before SSH", () => {
		assert.deepEqual(parsePreviewSshCommand(["cleanup", "7"]), { name: "cleanup", prNumber: 7 });
		for (const arguments_ of [
			["cleanup", "7; id"],
			["cleanup", "07"],
			["list", "extra"],
			["version", "extra"],
		]) {
			assert.throws(() => parsePreviewSshCommand(arguments_), /usage/);
		}
	});

	void test("uses strict host checking and passes one canonical forced command", () => {
		const directory = mkdtempSync(join(tmpdir(), "preview-ssh-args-"));
		try {
			const config = parseSshConfig(environment(directory));
			const arguments_ = buildSshArguments(
				config,
				{ name: "cleanup", prNumber: 7 },
				"/tmp/key",
				"/tmp/known-hosts",
			);
			assert.ok(arguments_.includes("StrictHostKeyChecking=yes"));
			assert.deepEqual(arguments_.slice(0, 2), ["-F", "/dev/null"]);
			assert.ok(arguments_.includes("GlobalKnownHostsFile=/dev/null"));
			assert.equal(arguments_.at(-2), "preview_cleanup@staging.example");
			assert.equal(arguments_.at(-1), "cleanup 7");
			assert.equal(
				buildSshArguments(config, { name: "version" }, "/tmp/key", "/tmp/known-hosts").at(-1),
				"version",
			);
		} finally {
			rmSync(directory, { recursive: true, force: true });
		}
	});

	void test("writes mode-600 credentials, returns stdout, and always removes the files", () => {
		const directory = mkdtempSync(join(tmpdir(), "preview-ssh-run-"));
		try {
			const config = parseSshConfig(environment(directory));
			let observedKey = "";
			let observedMode = 0;
			const runner: SshRunner = {
				run: (arguments_) => {
					const keyPath = arguments_[arguments_.indexOf("-i") + 1] ?? "";
					observedKey = readFileSync(keyPath, "utf8");
					observedMode = statSync(keyPath).mode & 0o777;
					return { status: 0, stderr: "", stdout: "7\n" };
				},
			};
			assert.equal(executePreviewSsh(config, { name: "list" }, runner), "7\n");
			assert.equal(observedKey, `${PRIVATE_KEY}\n`);
			assert.equal(observedMode, 0o600);
			assert.throws(() => readFileSync(join(directory, "preview-cleanup-key")), /ENOENT/);
			assert.throws(() => readFileSync(join(directory, "preview-known-hosts")), /ENOENT/);
		} finally {
			rmSync(directory, { recursive: true, force: true });
		}
	});

	void test("removes credentials when the SSH process fails", () => {
		const directory = mkdtempSync(join(tmpdir(), "preview-ssh-failure-"));
		try {
			const config = parseSshConfig(environment(directory));
			const runner: SshRunner = {
				run: () => ({ status: 255, stderr: "connection failed", stdout: "" }),
			};
			assert.throws(() => executePreviewSsh(config, { name: "list" }, runner), /connection failed/);
			assert.throws(() => readFileSync(join(directory, "preview-cleanup-key")), /ENOENT/);
			assert.throws(() => readFileSync(join(directory, "preview-known-hosts")), /ENOENT/);
		} finally {
			rmSync(directory, { recursive: true, force: true });
		}
	});
});
