import { mkdtemp, rename, rm, writeFile } from "node:fs/promises";
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

/**
 * A published release's assets are plain HTTPS downloads, so verifying one needs no credential and
 * no GitHub CLI — which is what lets a deployment host, or a self-hoster, upgrade without holding an
 * account that could also change the release it is verifying.
 */
async function downloadReleaseAsset(
	repository: string,
	tag: string,
	name: string,
	into: string,
): Promise<void> {
	const url = `https://github.com/${repository}/releases/download/${tag}/${name}`;
	const response = await fetch(url);
	if (!response.ok) throw new Error(`GET ${url} returned ${response.status}`);
	await writeFile(join(into, name), Buffer.from(await response.arrayBuffer()));
}

const directory = await mkdtemp(join(tmpdir(), "hephaestus-release-"));
const outputDirectory = dirname(output);
const outputTempDirectory = await mkdtemp(join(outputDirectory, `.${basename(output)}-`));
const temporaryOutput = join(outputTempDirectory, "lock.env");
const asset = `release-${release}.json`;
try {
	await Promise.all(
		[asset, `${asset}.sigstore.json`, "manifest.json"].map((name) =>
			downloadReleaseAsset(releaseSignerRepository(process.env), release, name, directory),
		),
	);
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
