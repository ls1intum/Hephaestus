import { access, rename, rm } from "node:fs/promises";
import { join } from "node:path";
import process from "node:process";

import { positivePort, readEnvFile } from "./lib/env.ts";
import { run, succeeds } from "./lib/process.ts";

const root = join(import.meta.dirname, "..");
const server = join(root, "server");
const dataDirectory = join(server, "postgres-data");
const changelog = join(server, "application/src/main/resources/db/changelog_new.xml");

interface Config {
	env: Record<string, string | undefined>;
	ci: boolean;
	host: string;
	port: number;
}

const log = (message: string): void => console.log(`ℹ️  ${message}`);

async function config(): Promise<Config> {
	const fileEnv = await readEnvFile(join(server, ".env"));
	const env = { ...fileEnv, ...process.env };
	return {
		env,
		ci: env.CI === "true",
		host: env.POSTGRES_HOST ?? "localhost",
		port: positivePort(env.POSTGRES_PORT ?? "5432", "POSTGRES_PORT"),
	};
}

async function checkEnvironment(value: Config, requirePg = false): Promise<void> {
	await access(join(server, "pom.xml"));
	await access(join(import.meta.dirname, "generate-mermaid-erd.ts"));
	if (!value.ci) {
		if (!(await succeeds("docker", ["info"])))
			throw new Error("Docker is installed but unavailable. Start the Docker daemon, then retry.");
		if (!(await succeeds("docker", ["compose", "version"])))
			throw new Error("Docker Compose is required for local database utilities.");
	} else if (requirePg && !(await succeeds("pg_isready", ["--version"]))) {
		throw new Error("CI database utilities require 'pg_isready'.");
	}
}

async function waitForPostgres(value: Config): Promise<void> {
	for (let attempt = 0; attempt < 30; attempt++) {
		if (await succeeds("pg_isready", ["-h", value.host, "-p", String(value.port)])) return;
		await new Promise((resolve) => {
			setTimeout(resolve, 1000);
		});
	}
	throw new Error("PostgreSQL failed to become ready after 30 seconds");
}

async function startPostgres(value: Config): Promise<void> {
	if (value.ci) return waitForPostgres(value);
	await run("docker", ["compose", "up", "-d", "--wait", "postgres"], { cwd: server });
}

async function stopPostgres(value: Config): Promise<void> {
	if (!value.ci) {
		await run("docker", ["compose", "stop", "postgres"], { cwd: server });
		await run("docker", ["compose", "rm", "-f", "postgres"], { cwd: server });
	}
}

async function migrate(value: Config, diff = false, signal?: AbortSignal): Promise<void> {
	// The single-module Liquibase run resolves the generated clients from the local repository. CI
	// installs them from the packaged reactor before calling this; a workstation builds them here.
	if (!value.ci) {
		await run(
			"./mvnw",
			["-pl", "generated-clients", "-am", "install", "-DskipTests", "--batch-mode"],
			{ cwd: server, env: value.env, signal },
		);
	}
	await run(
		"./mvnw",
		[
			"-f",
			"application/pom.xml",
			"liquibase:update",
			...(diff ? ["liquibase:diff"] : []),
			`-Dpostgres.port=${value.port}`,
		],
		{ cwd: server, env: { ...value.env, SPRING_PROFILES_ACTIVE: "local,dev" }, signal },
	);
}

async function generateErd(value: Config): Promise<void> {
	await run(
		"node",
		[
			"generate-mermaid-erd.ts",
			"--jdbc-url",
			`jdbc:postgresql://${value.host}:${value.port}/${value.env.POSTGRES_DB ?? "hephaestus"}`,
			"--username",
			value.env.POSTGRES_USER ?? "root",
			"--output",
			"../docs/contributor/erd/schema.mmd",
		],
		{
			cwd: import.meta.dirname,
			env: { ...value.env, POSTGRES_PASSWORD: value.env.POSTGRES_PASSWORD ?? "root" },
		},
	);
}

async function draftChangelog(value: Config): Promise<void> {
	await rm(changelog, { force: true });
	if (value.ci) {
		await waitForPostgres(value);
		await migrate(value, true);
		return;
	}
	const backup = `${dataDirectory}-temp-${process.pid}`;
	const controller = new AbortController();
	let interrupted: NodeJS.Signals | undefined;
	const interrupt = (signal: NodeJS.Signals): void => {
		interrupted = signal;
		controller.abort(new Error("database draft interrupted"));
	};
	const sigint = (): void => interrupt("SIGINT");
	const sigterm = (): void => interrupt("SIGTERM");
	process.once("SIGINT", sigint);
	process.once("SIGTERM", sigterm);
	try {
		await withDisposableDatabase(
			dataDirectory,
			backup,
			() => stopPostgres(value),
			async () => {
				controller.signal.throwIfAborted();
				await startPostgres(value);
				controller.signal.throwIfAborted();
				await migrate(value, true, controller.signal);
				controller.signal.throwIfAborted();
			},
		);
	} finally {
		if (interrupted) process.kill(process.pid, interrupted);
	}
}

export async function withDisposableDatabase(
	data: string,
	backup: string,
	stop: () => Promise<void>,
	operation: () => Promise<void>,
): Promise<void> {
	await stop();
	const backedUp = await access(data)
		.then(() => true)
		.catch(() => false);
	if (backedUp) await rename(data, backup);
	let operationError: unknown;
	try {
		await operation();
	} catch (error) {
		operationError = error;
	}
	try {
		await stop();
	} catch (cleanupError) {
		const cleanup =
			cleanupError instanceof Error ? cleanupError : new Error("Database shutdown failed");
		if (operationError) {
			const primary =
				operationError instanceof Error ? operationError : new Error("Database operation failed");
			throw new AggregateError([primary, cleanup], "Database operation and shutdown failed", {
				cause: cleanupError,
			});
		}
		throw cleanup;
	}
	await rm(data, { recursive: true, force: true });
	if (backedUp) await rename(backup, data);
	if (operationError)
		throw operationError instanceof Error ? operationError : new Error("Database operation failed");
}

async function main(): Promise<void> {
	const command = process.argv[2];
	if (!["generate-erd", "draft-changelog", "help", "-h", "--help"].includes(command ?? "")) {
		console.error(command ? `Unknown command: ${command}` : "No command specified.");
		console.error("Usage: node scripts/db-utils.ts [generate-erd|draft-changelog]");
		process.exitCode = 1;
		return;
	}
	if (["help", "-h", "--help"].includes(command ?? "")) {
		console.log("Usage: node scripts/db-utils.ts [generate-erd|draft-changelog]");
		return;
	}
	const value = await config();
	await checkEnvironment(value, value.ci);
	if (command === "generate-erd") {
		log("Starting ERD generation...");
		await startPostgres(value);
		await migrate(value);
		await generateErd(value);
		console.log("✅ ERD generation completed successfully!");
	} else {
		log("Starting changelog diff generation...");
		await draftChangelog(value);
		console.log("✅ Changelog diff process completed!");
	}
}

if (import.meta.main) await main();
