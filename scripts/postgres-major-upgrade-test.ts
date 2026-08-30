import { spawnSync } from "node:child_process";
import { randomUUID } from "node:crypto";

const id = `postgres-upgrade-${randomUUID().slice(0, 8)}`;
const source = `${id}-17`;
const target = `${id}-18`;
const sourceVolume = `${source}-data`;
const targetVolume = `${target}-data`;

function run(command: string, args: string[], input?: Buffer): string {
	const result = spawnSync(command, args, { encoding: "utf8", input, maxBuffer: 64 * 1024 * 1024 });
	if (result.status !== 0) {
		throw new Error(`${command} ${args.join(" ")} failed:\n${result.stdout}${result.stderr}`);
	}
	return result.stdout.trim();
}

function docker(...args: string[]): string {
	return run("docker", args);
}

function sql(container: string, query: string): string {
	return docker(
		"exec",
		container,
		"psql",
		"-U",
		"root",
		"-d",
		"hephaestus",
		"-v",
		"ON_ERROR_STOP=1",
		"-Atc",
		query,
	);
}

function wait(container: string): void {
	for (let attempt = 0; attempt < 60; attempt++) {
		const result = spawnSync("docker", [
			"exec",
			container,
			"psql",
			"-U",
			"root",
			"-d",
			"hephaestus",
			"-c",
			"SELECT 1",
		]);
		if (result.status === 0) return;
		Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1_000);
	}
	throw new Error(`${container} did not become ready`);
}

function start(container: string, volume: string, mount: string, image: string): number {
	docker(
		"run",
		"-d",
		"--name",
		container,
		"-p",
		"127.0.0.1::5432",
		"-v",
		`${volume}:${mount}`,
		"-e",
		"POSTGRES_DB=hephaestus",
		"-e",
		"POSTGRES_USER=root",
		"-e",
		"POSTGRES_PASSWORD=root",
		image,
	);
	wait(container);
	const mapping = docker("port", container, "5432/tcp");
	return Number(mapping.slice(mapping.lastIndexOf(":") + 1));
}

function fingerprint(container: string): string {
	return sql(
		container,
		"SELECT count(*) || ':' || md5(string_agg(id || ':' || author || ':' || filename, '|' ORDER BY orderexecuted)) FROM databasechangelog",
	);
}

try {
	run("docker", [
		"build",
		"--build-arg",
		"PG_MAJOR=17",
		"--build-arg",
		"PARTMAN_VERSION=5.4.3-1.pgdg12+1",
		"-t",
		`${id}:17`,
		"docker/postgres",
	]);
	run("docker", ["build", "-t", `${id}:18`, "docker/postgres"]);
	docker("volume", "create", sourceVolume);
	docker("volume", "create", targetVolume);

	const sourcePort = start(source, sourceVolume, "/var/lib/postgresql/data", `${id}:17`);
	if (sql(source, "SHOW server_version_num").slice(0, 2) !== "17")
		throw new Error("source is not PostgreSQL 17");

	// liquibase:update is a single-module invocation, so the reactor sibling the application
	// depends on must be installed to the local repository first — a warm CI cache is not a given.
	run("node", [
		"scripts/run-mvnw.ts",
		"-pl",
		"generated-clients",
		"-am",
		"install",
		"-DskipTests",
		"--quiet",
	]);
	run("node", [
		"scripts/run-mvnw.ts",
		"-f",
		"application/pom.xml",
		"liquibase:update",
		`-Dpostgres.port=${sourcePort}`,
		"--quiet",
	]);
	if (sql(source, "SELECT extversion FROM pg_extension WHERE extname='pg_partman'") !== "5.4.3") {
		throw new Error("source pg_partman is not 5.4.3");
	}
	sql(
		source,
		"CREATE TABLE upgrade_qualification(id bigint PRIMARY KEY, value text NOT NULL); INSERT INTO upgrade_qualification VALUES (1, 'preserved')",
	);
	sql(source, "CALL partman.run_maintenance_proc()");
	const sourceFingerprint = fingerprint(source);
	const partmanConfig = sql(
		source,
		"SELECT parent_table || ':' || partition_interval || ':' || premake || ':' || retention FROM partman.part_config WHERE parent_table='public.auth_event'",
	);
	if (!partmanConfig) throw new Error("auth_event is not registered with pg_partman");

	const dump = spawnSync("docker", ["exec", source, "pg_dump", "-U", "root", "-Fc", "hephaestus"], {
		maxBuffer: 64 * 1024 * 1024,
	}).stdout;
	if (!(dump instanceof Buffer) || dump.length === 0) throw new Error("source dump is empty");
	const listing = spawnSync("docker", ["exec", "-i", source, "pg_restore", "--list"], {
		input: dump,
	});
	if (listing.status !== 0) throw new Error("source dump is unreadable");
	docker("stop", source);

	start(target, targetVolume, "/var/lib/postgresql", `${id}:18`);
	docker("exec", target, "dropdb", "-U", "root", "hephaestus");
	docker("exec", target, "createdb", "-U", "root", "hephaestus");
	const restore = spawnSync(
		"docker",
		[
			"exec",
			"-i",
			target,
			"pg_restore",
			"-U",
			"root",
			"-d",
			"hephaestus",
			"--no-owner",
			"--no-acl",
			"--single-transaction",
		],
		{ input: dump, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 },
	);
	if (restore.status !== 0) throw new Error(`restore failed:\n${restore.stdout}${restore.stderr}`);
	sql(target, "ALTER EXTENSION pg_partman UPDATE");

	if (sql(target, "SHOW server_version_num").slice(0, 2) !== "18")
		throw new Error("target is not PostgreSQL 18");
	if (sql(target, "SELECT extversion FROM pg_extension WHERE extname='pg_partman'") !== "5.5.0")
		throw new Error("target pg_partman is not 5.5.0");
	if (sql(target, "SELECT value FROM upgrade_qualification WHERE id=1") !== "preserved")
		throw new Error("qualification row was not restored");
	if (fingerprint(target) !== sourceFingerprint)
		throw new Error("Liquibase history changed during restore");
	if (
		sql(
			target,
			"SELECT parent_table || ':' || partition_interval || ':' || premake || ':' || retention FROM partman.part_config WHERE parent_table='public.auth_event'",
		) !== partmanConfig
	)
		throw new Error("pg_partman configuration changed during restore");
	sql(target, "CALL partman.run_maintenance_proc()");
	if (
		sql(
			target,
			"SELECT count(*) > 0 FROM pg_inherits WHERE inhparent = 'public.auth_event'::regclass",
		) !== "t"
	)
		throw new Error("auth_event partitions were not restored");

	docker("stop", target);
	docker("start", source);
	wait(source);
	if (sql(source, "SELECT value FROM upgrade_qualification WHERE id=1") !== "preserved")
		throw new Error("PostgreSQL 17 rollback volume is unreadable");
} finally {
	for (const container of [source, target]) spawnSync("docker", ["rm", "-f", container]);
	for (const volume of [sourceVolume, targetVolume])
		spawnSync("docker", ["volume", "rm", "-f", volume]);
	for (const image of [`${id}:17`, `${id}:18`]) spawnSync("docker", ["rmi", "-f", image]);
}
