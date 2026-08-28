import { afterEach, expect, test } from "bun:test";
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
	const process = Bun.spawn([join(directory, "setup.sh")], {
		env: path === undefined ? Bun.env : { ...Bun.env, PATH: path },
		stdout: "pipe",
		stderr: "pipe",
	});
	const [exitCode, output] = await Promise.all([
		process.exited,
		new Response(process.stdout).text(),
	]);
	return { exitCode, output };
}

function managedValues(environment: string): string[] {
	const keys = new Set([
		"POSTGRES_PASSWORD",
		"HEPHAESTUS_SECURITY_ENCRYPTION_KEY",
		"HEPHAESTUS_AUTH_STATE_COOKIE_KEY",
		"WEBHOOK_SECRET",
	]);
	return environment
		.split("\n")
		.map((line) => line.split("=", 2))
		.filter(([key, value]) => keys.has(key ?? "") && value !== undefined)
		.map(([, value]) => value ?? "");
}

test("generates protected secrets without printing them", async () => {
	const directory = await fixture();
	const result = await setup(directory);
	const environmentPath = join(directory, ".env");
	const environment = await readFile(environmentPath, "utf8");

	expect(result.exitCode).toBe(0);
	expect(environment).toMatch(/^POSTGRES_PASSWORD=[0-9a-f]{32}$/m);
	expect(environment).toMatch(/^HEPHAESTUS_SECURITY_ENCRYPTION_KEY=[0-9a-f]{32}$/m);
	expect(environment).toMatch(/^HEPHAESTUS_AUTH_STATE_COOKIE_KEY=[A-Za-z0-9+/]{43}=$/m);
	expect(environment).toMatch(/^WEBHOOK_SECRET=[0-9a-f]{64}$/m);
	expect((await lstat(environmentPath)).mode & 0o777).toBe(0o600);
	for (const secret of managedValues(environment)) expect(result.output).not.toContain(secret);

	const second = await setup(directory);
	expect(second.exitCode).toBe(0);
	expect(await readFile(environmentPath, "utf8")).toBe(environment);
});

test("preserves configured values and fills missing assignments", async () => {
	const directory = await fixture();
	const example = await readFile(join(directory, ".env.example"), "utf8");
	await writeFile(
		join(directory, ".env"),
		example
			.replace("POSTGRES_PASSWORD=", "POSTGRES_PASSWORD=preserved")
			.replace(/^WEBHOOK_SECRET=.*\n/m, ""),
	);

	expect((await setup(directory)).exitCode).toBe(0);
	const environment = await readFile(join(directory, ".env"), "utf8");
	expect(environment).toContain("POSTGRES_PASSWORD=preserved\n");
	expect(environment).toMatch(/^WEBHOOK_SECRET=[0-9a-f]{64}$/m);
});

test("rejects duplicate managed assignments", async () => {
	const directory = await fixture();
	const examplePath = join(directory, ".env.example");
	await writeFile(
		examplePath,
		`${await readFile(examplePath, "utf8")}POSTGRES_PASSWORD=duplicate\n`,
	);

	expect((await setup(directory)).exitCode).not.toBe(0);
	expect(await Bun.file(join(directory, ".env")).exists()).toBeFalse();
});

test("leaves an existing environment unchanged when generation fails", async () => {
	const directory = await fixture();
	const environmentPath = join(directory, ".env");
	const original = await readFile(join(directory, ".env.example"), "utf8");
	await writeFile(environmentPath, original);
	const binaryDirectory = join(directory, "bin");
	await mkdir(binaryDirectory);
	const openssl = join(binaryDirectory, "openssl");
	await writeFile(openssl, "#!/bin/sh\nexit 1\n");
	await chmod(openssl, 0o700);

	expect((await setup(directory, `${binaryDirectory}:${Bun.env.PATH ?? ""}`)).exitCode).not.toBe(0);
	expect(await readFile(environmentPath, "utf8")).toBe(original);
});

test("refuses to write through an environment symlink", async () => {
	const directory = await fixture();
	const target = join(directory, "target");
	await writeFile(target, "unchanged");
	await symlink(target, join(directory, ".env"));

	expect((await setup(directory)).exitCode).not.toBe(0);
	expect(await readFile(target, "utf8")).toBe("unchanged");
});
