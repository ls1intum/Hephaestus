import { mkdtemp, rename, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, dirname, join } from "node:path";

import { run } from "./lib/process.ts";
import { releaseCertificateIdentity } from "./lib/release-identities.ts";
import { releaseSignerRepository } from "./lib/release-signer.ts";
import { isRelease } from "./release-image-lock.ts";

const repositoryRoot = join(import.meta.dirname, "..");
const [release, output = join(repositoryRoot, "docker/self-host/release-lock.env")] =
	process.argv.slice(2);
if (!release || !isRelease(release)) throw new Error("usage: prepare-release-lock vX.Y.Z [output]");

const directory = await mkdtemp(join(tmpdir(), "hephaestus-release-"));
const outputDirectory = dirname(output);
const outputTempDirectory = await mkdtemp(join(outputDirectory, `.${basename(output)}-`));
const temporaryOutput = join(outputTempDirectory, "lock.env");
const asset = `release-${release}.json`;
try {
	await run("gh", [
		"release",
		"download",
		release,
		"--repo",
		releaseSignerRepository(process.env),
		"--dir",
		directory,
		"--pattern",
		asset,
		"--pattern",
		`${asset}.sigstore.json`,
		"--pattern",
		"manifest.json",
	]);
	await run("cosign", [
		"verify-blob",
		"--bundle",
		join(directory, `${asset}.sigstore.json`),
		"--certificate-identity",
		// Locks signed before the repository transfer carry the old owner/repo in their
		// certificate, so the expected identity is the release's, not the run's (issue #1599).
		releaseCertificateIdentity(release, process.env),
		"--certificate-oidc-issuer",
		"https://token.actions.githubusercontent.com",
		join(directory, asset),
	]);
	await run(process.execPath, [
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
