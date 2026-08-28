import { spawnSync } from "node:child_process";
import { rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";

import { requiredEnv as required } from "./lib/env.ts";

interface SshConfig {
	host: string;
	hostKey: string;
	privateKey: string;
	runnerTemp: string;
	user: string;
}

export type PreviewSshCommand =
	| { name: "cleanup"; prNumber: number }
	| { name: "list" }
	| { name: "prune" }
	| { name: "version" };

export interface SshRunner {
	run: (arguments_: readonly string[]) => { status: number | null; stderr: string; stdout: string };
}

const HOST_PATTERN = /^[A-Za-z0-9.-]+$/;
const USER_PATTERN = /^[a-z_][a-z0-9_-]*$/;
const KEY_PATTERN = /^[A-Za-z0-9+/]+={0,3}$/;

export function parseSshConfig(environment: NodeJS.ProcessEnv): SshConfig {
	const host = required(environment, "PREVIEW_HOST");
	const user = required(environment, "PREVIEW_SSH_USER");
	const hostKey = required(environment, "PREVIEW_HOST_KEY");
	if (!HOST_PATTERN.test(host)) throw new Error("PREVIEW_HOST is malformed.");
	if (!USER_PATTERN.test(user)) throw new Error("PREVIEW_SSH_USER is malformed.");
	if (hostKey.includes("\n") || hostKey.includes("\r")) {
		throw new Error("PREVIEW_HOST_KEY must contain exactly one line.");
	}
	const [keyHost, keyType, keyData] = hostKey.split(/\s+/);
	if (keyHost !== host || keyType !== "ssh-ed25519" || !keyData || !KEY_PATTERN.test(keyData)) {
		throw new Error("PREVIEW_HOST_KEY does not match the configured Ed25519 host key.");
	}
	return {
		host,
		hostKey,
		privateKey: required(environment, "PREVIEW_SSH_PRIVATE_KEY"),
		runnerTemp: required(environment, "RUNNER_TEMP"),
		user,
	};
}

export function parsePreviewSshCommand(arguments_: readonly string[]): PreviewSshCommand {
	if (arguments_.length === 1 && arguments_[0] === "list") return { name: "list" };
	if (arguments_.length === 1 && arguments_[0] === "version") return { name: "version" };
	if (arguments_.length === 1 && arguments_[0] === "prune") return { name: "prune" };
	if (arguments_.length === 2 && arguments_[0] === "cleanup") {
		const prNumber = Number(arguments_[1]);
		if (Number.isSafeInteger(prNumber) && prNumber > 0 && String(prNumber) === arguments_[1]) {
			return { name: "cleanup", prNumber };
		}
	}
	throw new Error("usage: preview-ssh.ts list | version | prune | cleanup <pr>");
}

export function buildSshArguments(
	config: SshConfig,
	command: PreviewSshCommand,
	keyPath: string,
	knownHostsPath: string,
): string[] {
	return [
		"-F",
		"/dev/null",
		"-T",
		"-i",
		keyPath,
		"-o",
		"BatchMode=yes",
		"-o",
		"ConnectTimeout=15",
		"-o",
		"ServerAliveInterval=15",
		"-o",
		"ServerAliveCountMax=8",
		"-o",
		"IdentitiesOnly=yes",
		"-o",
		"StrictHostKeyChecking=yes",
		"-o",
		"GlobalKnownHostsFile=/dev/null",
		"-o",
		`UserKnownHostsFile=${knownHostsPath}`,
		`${config.user}@${config.host}`,
		command.name === "cleanup" ? `cleanup ${command.prNumber}` : command.name,
	];
}

export function executePreviewSsh(
	config: SshConfig,
	command: PreviewSshCommand,
	runner: SshRunner,
): string {
	const keyPath = join(config.runnerTemp, "preview-cleanup-key");
	const knownHostsPath = join(config.runnerTemp, "preview-known-hosts");
	try {
		writeFileSync(keyPath, `${config.privateKey.trimEnd()}\n`, { mode: 0o600 });
		writeFileSync(knownHostsPath, `${config.hostKey}\n`, { mode: 0o600 });
		const result = runner.run(buildSshArguments(config, command, keyPath, knownHostsPath));
		if (result.status !== 0) {
			throw new Error(`preview cleanup SSH command failed: ${result.stderr.trim().slice(0, 300)}`);
		}
		return result.stdout;
	} finally {
		rmSync(keyPath, { force: true });
		rmSync(knownHostsPath, { force: true });
	}
}

function systemRunner(): SshRunner {
	return {
		run: (arguments_) => {
			// The host commands wait on Coolify's own teardown, so this is generous — but a hung
			// connection must not sit until the job timeout with no status.
			const result = spawnSync("ssh", [...arguments_], { encoding: "utf8", timeout: 600_000 });
			return { status: result.status, stderr: result.stderr, stdout: result.stdout };
		},
	};
}

if (import.meta.main) {
	try {
		const command = parsePreviewSshCommand(process.argv.slice(2));
		process.stdout.write(executePreviewSsh(parseSshConfig(process.env), command, systemRunner()));
	} catch (error) {
		const message = error instanceof Error ? error.message : "unknown preview SSH error";
		console.error(`::error::${message.replaceAll(/[\r\n]+/g, " ")}`);
		process.exitCode = 1;
	}
}
