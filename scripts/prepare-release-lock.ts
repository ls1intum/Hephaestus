import { mkdtemp, rename, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, dirname, join } from "node:path";

import { isRelease } from "./release-image-lock.ts";

const repositoryRoot = join(import.meta.dirname, "..");
const [release, output = join(repositoryRoot, "docker/self-host/release-lock.env")] =
	process.argv.slice(2);
if (!release || !isRelease(release)) throw new Error("usage: prepare-release-lock vX.Y.Z [output]");

async function run(command: string[]): Promise<void> {
	const process = Bun.spawn(command, { stdout: "inherit", stderr: "inherit" });
	if ((await process.exited) !== 0) throw new Error(`${command[0]} failed`);
}

const directory = await mkdtemp(join(tmpdir(), "hephaestus-release-"));
const outputDirectory = dirname(output);
const outputTempDirectory = await mkdtemp(join(outputDirectory, `.${basename(output)}-`));
const temporaryOutput = join(outputTempDirectory, "lock.env");
const asset = `release-${release}.json`;
try {
	await run([
		"gh",
		"release",
		"download",
		release,
		"--repo",
		"ls1intum/Hephaestus",
		"--dir",
		directory,
		"--pattern",
		asset,
		"--pattern",
		`${asset}.sigstore.json`,
		"--pattern",
		"manifest.json",
	]);
	await run([
		"cosign",
		"verify-blob",
		"--bundle",
		join(directory, `${asset}.sigstore.json`),
		"--certificate-identity",
		"https://github.com/ls1intum/Hephaestus/.github/workflows/release.yml@refs/heads/main",
		"--certificate-oidc-issuer",
		"https://token.actions.githubusercontent.com",
		join(directory, asset),
	]);
	await run([
		"bun",
		join(import.meta.dirname, "release-image-lock.ts"),
		join(directory, asset),
		join(directory, "manifest.json"),
		release,
		temporaryOutput,
	]);
	await rename(temporaryOutput, output);
	console.log(`Verified ${release}; wrote ${output}`);
} finally {
	await rm(directory, { recursive: true, force: true });
	await rm(outputTempDirectory, { recursive: true, force: true });
}
