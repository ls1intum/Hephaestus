import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import {
	chmod,
	copyFile,
	lstat,
	mkdir,
	mkdtemp,
	readFile,
	rm,
	symlink,
	writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, test } from "node:test";

const sourceDirectory = join(import.meta.dirname, "..", "docker", "self-host");
const posixOnly = { skip: process.platform === "win32" && "setup.sh is a POSIX shell script" };
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

/**
 * Every run gets a `docker` of its own, so no test depends on the volumes of the machine it runs
 * on. The stub answers from `docker-answer` (`absent` by default) and records what it was asked.
 */
async function installDockerStub(
	directory: string,
	answer: "present" | "absent" | "error",
): Promise<string> {
	const bin = join(directory, "bin");
	await mkdir(bin, { recursive: true });
	await writeFile(join(directory, "docker-answer"), answer);
	await writeFile(
		join(bin, "docker"),
		`#!/bin/sh
printf '%s\\n' "$*" >> "${join(directory, "docker-args")}"
case $(cat "${join(directory, "docker-answer")}") in
	present) printf 'hephaestus_postgresql-data\\n' ;;
	absent) printf '%s\\n' 'Error response from daemon: get hephaestus_postgresql-data: no such volume' >&2; exit 1 ;;
	*) printf '%s\\n' 'Cannot connect to the Docker daemon at unix:///var/run/docker.sock. Is the docker daemon running?' >&2; exit 1 ;;
esac
`,
		{ mode: 0o755 },
	);
	return bin;
}

async function setup(
	directory: string,
	options: { path?: string; database?: "present" | "absent" | "error" } = {},
): Promise<{ exitCode: number; output: string }> {
	const bin = await installDockerStub(directory, options.database ?? "absent");
	const path = options.path ?? `${bin}:${process.env.PATH ?? ""}`;
	return await new Promise((resolve) => {
		execFile(
			join(directory, "setup.sh"),
			{ env: { ...process.env, PATH: path } },
			(error, stdout, stderr) => {
				resolve({
					exitCode: typeof error?.code === "number" ? error.code : error ? 1 : 0,
					output: `${stdout}${stderr}`,
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

await test("generates protected secrets without printing them", posixOnly, async () => {
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

await test("preserves configured values and fills missing assignments", posixOnly, async () => {
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

await test("rejects duplicate managed assignments", posixOnly, async () => {
	const directory = await fixture();
	const examplePath = join(directory, ".env.example");
	await writeFile(
		examplePath,
		`${await readFile(examplePath, "utf8")}POSTGRES_PASSWORD=duplicate\n`,
	);

	assert.notEqual((await setup(directory)).exitCode, 0);
	await assert.rejects(lstat(join(directory, ".env")));
});

await test(
	"leaves an existing environment unchanged when generation fails",
	posixOnly,
	async () => {
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
			(await setup(directory, { path: `${binaryDirectory}:${process.env.PATH ?? ""}` })).exitCode,
			0,
		);
		assert.equal(await readFile(environmentPath, "utf8"), original);
	},
);

await test("refuses to write through an environment symlink", posixOnly, async () => {
	const directory = await fixture();
	const target = join(directory, "target");
	await writeFile(target, "unchanged");
	await symlink(target, join(directory, ".env"));

	assert.notEqual((await setup(directory)).exitCode, 0);
	assert.equal(await readFile(target, "utf8"), "unchanged");
});

await test(
	"asks docker about the exact volume the supported topology uses",
	posixOnly,
	async () => {
		const directory = await fixture();
		const result = await setup(directory);
		assert.equal(result.exitCode, 0);
		assert.equal(
			await readFile(join(directory, "docker-args"), "utf8"),
			"volume inspect --format {{.Name}} hephaestus_postgresql-data\n",
		);
		assert.match(result.output, /Generated HEPHAESTUS_SECURITY_ENCRYPTION_KEY/);
	},
);

await test(
	"refuses to generate the encryption key over an existing database",
	posixOnly,
	async () => {
		const directory = await fixture();
		const refused = await setup(directory, { database: "present" });
		assert.equal(refused.exitCode, 1);
		assert.match(
			refused.output,
			/already exists on this host .*Set HEPHAESTUS_SECURITY_ENCRYPTION_KEY/,
		);
		assert.doesNotMatch(refused.output, /Generated/);
		await assert.rejects(readFile(join(directory, ".env"), "utf8"), { code: "ENOENT" });
	},
);

await test(
	"an existing database with its master key still derives the credential key",
	posixOnly,
	async () => {
		// The v0.74 to v0.75 path: older installations carried only the master key, and the
		// credential key was that key. Refusing here would strand every one of them.
		const directory = await fixture();
		await writeFile(
			join(directory, ".env"),
			"HEPHAESTUS_SECURITY_ENCRYPTION_KEY=0123456789abcdef0123456789abcdef\n",
		);
		const result = await setup(directory, { database: "present" });
		assert.equal(result.exitCode, 0);
		const environment = await readFile(join(directory, ".env"), "utf8");
		assert.match(
			environment,
			/^HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY=0123456789abcdef0123456789abcdef$/m,
		);
		assert.match(environment, /^POSTGRES_PASSWORD=.+$/m);
	},
);

await test("a docker that cannot answer is not taken for an empty host", posixOnly, async () => {
	const directory = await fixture();
	const result = await setup(directory, { database: "error" });
	assert.equal(result.exitCode, 1);
	assert.match(result.output, /Could not tell whether a Hephaestus database already exists/);
	await assert.rejects(readFile(join(directory, ".env"), "utf8"), { code: "ENOENT" });
});

await test("docker is not asked when the key is already set", posixOnly, async () => {
	const directory = await fixture();
	await writeFile(
		join(directory, ".env"),
		"HEPHAESTUS_SECURITY_ENCRYPTION_KEY=0123456789abcdef0123456789abcdef\n",
	);
	const result = await setup(directory, { database: "error" });
	assert.equal(result.exitCode, 0);
	await assert.rejects(readFile(join(directory, "docker-args"), "utf8"), { code: "ENOENT" });
});
