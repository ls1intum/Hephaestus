import { spawnSync } from "node:child_process";
import { readFileSync, statSync } from "node:fs";

interface CleanupConfig {
	applicationId: string;
	appUuid: string;
	graceAttempts: number;
}

export interface DockerClient {
	run: (arguments_: readonly string[], allowFailure?: boolean) => string;
}

type CleanupCommand =
	| { name: "cleanup"; prNumber: number }
	| { name: "list" }
	| { name: "prune" }
	| { name: "version" };

// Stamped at build time (docker/preview/README.md) with the hash of this file's source, so the
// nightly job can report a host binary that no longer matches this repository. Undefined when the
// script runs from source, which reports as `unbuilt`.
declare const CLEANUP_BUILD_ID: string;

export function buildId(): string {
	return typeof CLEANUP_BUILD_ID === "string" ? CLEANUP_BUILD_ID : "unbuilt";
}

const IDENTIFIER_PATTERN = /^[A-Za-z0-9_-]+$/;
const POSITIVE_INTEGER_PATTERN = /^[1-9][0-9]*$/;
const CONFIG_KEYS = new Set([
	"COOLIFY_APPLICATION_ID",
	"COOLIFY_APP_UUID",
	"COOLIFY_CLEANUP_GRACE_ATTEMPTS",
]);

function positiveSafeInteger(value: string): number | undefined {
	if (!POSITIVE_INTEGER_PATTERN.test(value)) return undefined;
	const parsed = Number(value);
	return Number.isSafeInteger(parsed) && String(parsed) === value ? parsed : undefined;
}

function lines(value: string): string[] {
	return value
		.split("\n")
		.map((line) => line.trim())
		.filter(Boolean);
}

function escapeRegExp(value: string): string {
	return value.replaceAll(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

export function parseConfig(source: string): CleanupConfig {
	const values = new Map<string, string>();
	for (const [index, rawLine] of source.split("\n").entries()) {
		const line = rawLine.trim();
		if (!line || line.startsWith("#")) continue;
		const separator = line.indexOf("=");
		if (separator <= 0) throw new Error(`invalid config line ${index + 1}`);
		const key = line.slice(0, separator);
		const value = line.slice(separator + 1);
		if (values.has(key)) throw new Error(`duplicate config key ${key}`);
		if (!CONFIG_KEYS.has(key)) {
			throw new Error(`unknown config key ${key}`);
		}
		values.set(key, value);
	}
	const appUuid = values.get("COOLIFY_APP_UUID") ?? "";
	const applicationId = values.get("COOLIFY_APPLICATION_ID") ?? "";
	const graceAttemptsText = values.get("COOLIFY_CLEANUP_GRACE_ATTEMPTS") ?? "12";
	const graceAttempts = Number(graceAttemptsText);
	if (!IDENTIFIER_PATTERN.test(appUuid)) throw new Error("invalid application UUID");
	if (!POSITIVE_INTEGER_PATTERN.test(applicationId)) throw new Error("invalid application ID");
	if (!Number.isSafeInteger(graceAttempts) || graceAttempts < 0 || graceAttempts > 60) {
		throw new Error("invalid cleanup grace period");
	}
	return { applicationId, appUuid, graceAttempts };
}

export function parseCommand(value: string): CleanupCommand {
	if (value === "list") return { name: "list" };
	if (value === "version") return { name: "version" };
	if (value === "prune") return { name: "prune" };
	const match = /^cleanup ([1-9][0-9]*)$/.exec(value);
	if (!match?.[1]) throw new Error("command not allowed");
	const prNumber = positiveSafeInteger(match[1]);
	if (prNumber === undefined) throw new Error("command not allowed");
	return { name: "cleanup", prNumber };
}

function containerPrs(config: CleanupConfig, docker: DockerClient): number[] {
	return lines(
		docker.run([
			"ps",
			"-a",
			"--filter",
			"label=coolify.managed=true",
			"--filter",
			`label=coolify.applicationId=${config.applicationId}`,
			"--format",
			'{{.Label "coolify.pullRequestId"}}',
		]),
	)
		.map(positiveSafeInteger)
		.filter((value): value is number => value !== undefined);
}

function volumePrs(config: CleanupConfig, docker: DockerClient): number[] {
	const pattern = new RegExp(`^${escapeRegExp(config.appUuid)}_.+-pr-([1-9][0-9]*)$`);
	return lines(docker.run(["volume", "ls", "--format", "{{.Name}}"]))
		.map((name) => pattern.exec(name)?.[1])
		.filter((value): value is string => value !== undefined)
		.map(positiveSafeInteger)
		.filter((value): value is number => value !== undefined);
}

function networkPrs(config: CleanupConfig, docker: DockerClient): number[] {
	const escaped = escapeRegExp(config.appUuid);
	const pattern = new RegExp(`^(?:${escaped}-|${escaped}_.+-pr-)([1-9][0-9]*)$`);
	return lines(docker.run(["network", "ls", "--format", "{{.Name}}"]))
		.map((name) => pattern.exec(name)?.[1])
		.filter((value): value is string => value !== undefined)
		.map(positiveSafeInteger)
		.filter((value): value is number => value !== undefined);
}

export function listPreviews(config: CleanupConfig, docker: DockerClient): number[] {
	return [
		...new Set([
			...containerPrs(config, docker),
			...volumePrs(config, docker),
			...networkPrs(config, docker),
		]),
	].toSorted((left, right) => left - right);
}

// Labels are immutable after creation, so the filter is the whole selection: there is no window
// between listing and removal in which a container could stop matching.
function matchingContainers(
	config: CleanupConfig,
	docker: DockerClient,
	prNumber: number,
): string[] {
	return lines(
		docker.run([
			"ps",
			"-aq",
			"--filter",
			"label=coolify.managed=true",
			"--filter",
			`label=coolify.applicationId=${config.applicationId}`,
			"--filter",
			`label=coolify.pullRequestId=${prNumber}`,
		]),
	);
}

function matchingVolumes(config: CleanupConfig, docker: DockerClient, prNumber: number): string[] {
	const pattern = new RegExp(`^${escapeRegExp(config.appUuid)}_.+-pr-${prNumber}$`);
	return lines(docker.run(["volume", "ls", "--format", "{{.Name}}"])).filter((name) =>
		pattern.test(name),
	);
}

function matchingNetworks(config: CleanupConfig, docker: DockerClient, prNumber: number): string[] {
	const escaped = escapeRegExp(config.appUuid);
	const pattern = new RegExp(`^(?:${escaped}-${prNumber}|${escaped}_.+-pr-${prNumber})$`);
	return lines(docker.run(["network", "ls", "--format", "{{.Name}}"])).filter((name) =>
		pattern.test(name),
	);
}

function resourcesAbsent(config: CleanupConfig, docker: DockerClient, prNumber: number): boolean {
	return (
		matchingContainers(config, docker, prNumber).length === 0 &&
		matchingVolumes(config, docker, prNumber).length === 0 &&
		matchingNetworks(config, docker, prNumber).length === 0
	);
}

export async function cleanupPreview(
	config: CleanupConfig,
	docker: DockerClient,
	prNumber: number,
	sleep: (milliseconds: number) => Promise<void> = (milliseconds) =>
		new Promise((resolve) => {
			setTimeout(resolve, milliseconds);
		}),
	log: (message: string) => void = console.log,
): Promise<void> {
	// Coolify tears the stack down itself after the close event; this waits for that before forcing.
	let waited = false;
	for (let attempt = 0; attempt < config.graceAttempts; attempt += 1) {
		if (resourcesAbsent(config, docker, prNumber)) return;
		waited = true;
		await sleep(5_000);
	}
	if (waited) {
		log(`PR ${prNumber}: resources persisted through the cleanup grace period; forcing removal`);
	}

	const containers = matchingContainers(config, docker, prNumber);
	if (containers.length > 0) docker.run(["rm", "-f", ...containers]);
	const volumes = matchingVolumes(config, docker, prNumber);
	if (volumes.length > 0) docker.run(["volume", "rm", ...volumes]);
	for (const network of matchingNetworks(config, docker, prNumber)) {
		docker.run(["network", "disconnect", "-f", network, "coolify-proxy"], true);
		docker.run(["network", "rm", network]);
	}
	if (!resourcesAbsent(config, docker, prNumber)) {
		throw new Error(`resources remain for PR ${prNumber}`);
	}
}

function systemDocker(): DockerClient {
	return {
		run: (arguments_, allowFailure = false) => {
			const result = spawnSync("docker", [...arguments_], { encoding: "utf8" });
			if (result.status !== 0 && !allowFailure) {
				throw new Error(`docker ${arguments_[0] ?? "command"} failed`);
			}
			return result.stdout;
		},
	};
}

function readConfig(forced: boolean): CleanupConfig {
	const path = forced
		? "/etc/hephaestus-preview-cleanup.conf"
		: (process.env.HEPHAESTUS_PREVIEW_CLEANUP_CONFIG ?? "/etc/hephaestus-preview-cleanup.conf");
	if (forced) {
		const statistics = statSync(path);
		if (statistics.uid !== 0 || (statistics.mode & 0o022) !== 0) {
			throw new Error(`${path} must be root-owned and not group/world-writable`);
		}
	}
	return parseConfig(readFileSync(path, "utf8"));
}

async function main(): Promise<void> {
	const originalCommand = process.env.SSH_ORIGINAL_COMMAND;
	const forced = originalCommand !== undefined;
	const command = parseCommand(forced ? originalCommand : process.argv.slice(2).join(" "));
	if (command.name === "version") {
		// Architecture included so a binary built for the wrong one says so, rather than only
		// failing to execute.
		console.log(`${process.arch}-${buildId()}`);
		return;
	}
	const config = readConfig(forced);
	const docker = systemDocker();
	if (command.name === "list") {
		for (const prNumber of listPreviews(config, docker)) console.log(prNumber);
		return;
	}
	if (command.name === "prune") {
		// Dangling only. Reclaiming superseded preview *tags* would need `-a`, which removes every
		// image no container references — and on this one daemon that includes the image staging
		// keeps solely as a rollback target. Preview and staging pull the same repositories, so no
		// filter separates them; this takes layer garbage and leaves tags alone.
		console.log(docker.run(["image", "prune", "-f"]).trim());
		return;
	}
	await cleanupPreview(config, docker, command.prNumber);
	console.log(`PR ${command.prNumber}: no matching preview resources remain`);
}

if (import.meta.main) {
	try {
		await main();
	} catch (error) {
		const message = error instanceof Error ? error.message : "unknown cleanup error";
		console.error(`preview cleanup: ${message.replaceAll(/[\r\n]+/g, " ")}`);
		process.exitCode = 1;
	}
}
