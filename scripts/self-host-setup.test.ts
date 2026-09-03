import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import {
	chmod,
	copyFile,
	lstat,
	mkdtemp,
	mkdir,
	readFile,
	rm,
	symlink,
	writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, test } from "node:test";

const sourceDirectory = join(import.meta.dirname, "..", "docker", "self-host");
const temporaryDirectories: string[] = [];

afterEach(async () => {
	await Promise.all(
		temporaryDirectories.splice(0).map((path) => rm(path, { recursive: true, force: true })),
	);
});

async function fixture(): Promise<string> {
	const directory = await mkdtemp(join(tmpdir(), "hephaestus-self-host-setup-"));
	temporaryDirectories.push(directory);
	await copyFile(join(sourceDirectory, "setup.sh"), join(directory, "setup.sh"));
	await copyFile(join(sourceDirectory, ".env.example"), join(directory, ".env.example"));
	return directory;
}

async function setup(
	directory: string,
	path?: string,
): Promise<{ exitCode: number; output: string }> {
	return await new Promise((resolve) => {
		execFile(
			join(directory, "setup.sh"),
			{ env: path === undefined ? process.env : { ...process.env, PATH: path } },
			(error, stdout) => {
				resolve({
					exitCode: typeof error?.code === "number" ? error.code : error ? 1 : 0,
					output: stdout,
				});
			},
		);
	});
}

function managedValues(environment: string): string[] {
	const keys = new Set([
		"POSTGRES_PASSWORD",
		"HEPHAESTUS_SECURITY_ENCRYPTION_KEY",
		"HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY",
		"HEPHAESTUS_AUTH_STATE_COOKIE_KEY",
		"WEBHOOK_SECRET",
		"NATS_USERNAME",
		"NATS_PASSWORD",
	]);
	return environment
		.split("\n")
		.map((line) => line.split("=", 2))
		.filter(([key, value]) => keys.has(key ?? "") && value !== undefined)
		.map(([, value]) => value ?? "");
}

await test("generates protected secrets without printing them", async () => {
	const directory = await fixture();
	const result = await setup(directory);
	const environmentPath = join(directory, ".env");
	const environment = await readFile(environmentPath, "utf8");

	assert.equal(result.exitCode, 0);
	assert.match(environment, /^POSTGRES_PASSWORD=[0-9a-f]{32}$/m);
	assert.match(environment, /^HEPHAESTUS_SECURITY_ENCRYPTION_KEY=[0-9a-f]{32}$/m);
	const encryptionKey = environment.match(/^HEPHAESTUS_SECURITY_ENCRYPTION_KEY=(.+)$/m)?.[1];
	const credentialKey = environment.match(
		/^HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY=(.+)$/m,
	)?.[1];
	assert.equal(credentialKey, encryptionKey);
	assert.match(environment, /^HEPHAESTUS_AUTH_STATE_COOKIE_KEY=[A-Za-z0-9+/]{43}=$/m);
	assert.match(environment, /^WEBHOOK_SECRET=[0-9a-f]{64}$/m);
	// The broker's config file rejects an all-digit credential, so both carry a letter prefix.
	assert.match(environment, /^NATS_USERNAME=heph[0-9a-f]{32}$/m);
	assert.match(environment, /^NATS_PASSWORD=heph[0-9a-f]{32}$/m);
	assert.notEqual(
		environment.match(/^NATS_USERNAME=(.+)$/m)?.[1],
		environment.match(/^NATS_PASSWORD=(.+)$/m)?.[1],
	);
	assert.equal((await lstat(environmentPath)).mode & 0o777, 0o600);
	for (const secret of managedValues(environment)) assert.ok(!result.output.includes(secret));

	const second = await setup(directory);
	assert.equal(second.exitCode, 0);
	assert.equal(await readFile(environmentPath, "utf8"), environment);
});

await test("preserves configured values and fills missing assignments", async () => {
	const directory = await fixture();
	const example = await readFile(join(directory, ".env.example"), "utf8");
	await writeFile(
		join(directory, ".env"),
		example
			.replace("POSTGRES_PASSWORD=", "POSTGRES_PASSWORD=preserved")
			.replace(/^WEBHOOK_SECRET=.*\n/m, ""),
	);

	assert.equal((await setup(directory)).exitCode, 0);
	const environment = await readFile(join(directory, ".env"), "utf8");
	assert.ok(environment.includes("POSTGRES_PASSWORD=preserved\n"));
	assert.match(environment, /^WEBHOOK_SECRET=[0-9a-f]{64}$/m);
});

await test("rejects duplicate managed assignments", async () => {
	const directory = await fixture();
	const examplePath = join(directory, ".env.example");
	await writeFile(
		examplePath,
		`${await readFile(examplePath, "utf8")}POSTGRES_PASSWORD=duplicate\n`,
	);

	assert.notEqual((await setup(directory)).exitCode, 0);
	await assert.rejects(lstat(join(directory, ".env")));
});

await test("leaves an existing environment unchanged when generation fails", async () => {
	const directory = await fixture();
	const environmentPath = join(directory, ".env");
	const original = await readFile(join(directory, ".env.example"), "utf8");
	await writeFile(environmentPath, original);
	const binaryDirectory = join(directory, "bin");
	await mkdir(binaryDirectory);
	const openssl = join(binaryDirectory, "openssl");
	await writeFile(openssl, "#!/bin/sh\nexit 1\n");
	await chmod(openssl, 0o700);

	assert.notEqual(
		(await setup(directory, `${binaryDirectory}:${process.env.PATH ?? ""}`)).exitCode,
		0,
	);
	assert.equal(await readFile(environmentPath, "utf8"), original);
});

await test("refuses to write through an environment symlink", async () => {
	const directory = await fixture();
	const target = join(directory, "target");
	await writeFile(target, "unchanged");
	await symlink(target, join(directory, ".env"));

	assert.notEqual((await setup(directory)).exitCode, 0);
	assert.equal(await readFile(target, "utf8"), "unchanged");
});
