import { appendFile, readdir } from "node:fs/promises";

import { isSet, requiredEnv } from "./lib/env.ts";
import { reportStem, resolvePlatformDigest } from "./lib/image-scan.ts";

const outputFile = requiredEnv(process.env, "GITHUB_OUTPUT");
const platform = requiredEnv(process.env, "SCAN_PLATFORM");
if (platform !== "linux/amd64" && platform !== "linux/arm64") {
	throw new Error("SCAN_PLATFORM must be linux/amd64 or linux/arm64");
}
const directory = process.env.DIGEST_DIRECTORY;
if (isSet(directory) === isSet(process.env.IMAGE_REF)) {
	throw new Error("Set exactly one of IMAGE_REF and DIGEST_DIRECTORY");
}
// A multi-architecture build's digest artifact names its platform's pushed image by its file name.
// The merge job refuses an index whose leaves differ from these same artifacts.
const reference = isSet(directory)
	? `${requiredEnv(process.env, "IMAGE_REPOSITORY")}@sha256:${await artifactDigest(directory)}`
	: requiredEnv(process.env, "IMAGE_REF");
if (!/^[^\s@]+@sha256:[a-f0-9]{64}$/u.test(reference)) {
	throw new Error("IMAGE_REF must be an immutable image digest reference");
}
const image = requiredEnv(process.env, "INPUT_IMAGE_NAME").split("/").at(-1);
if (image === undefined || !/^[a-z0-9][a-z0-9._-]*$/u.test(image)) {
	throw new Error("INPUT_IMAGE_NAME must end in a valid image name");
}
const digest = await resolvePlatformDigest(reference, platform);
await appendFile(
	outputFile,
	`image=${image}\ndigest=${digest}\nreport-stem=${reportStem(image, platform)}\n`,
);

async function artifactDigest(path: string): Promise<string> {
	const entries = await readdir(path, { withFileTypes: true });
	const [entry] = entries;
	if (entries.length !== 1 || entry === undefined || !entry.isFile()) {
		throw new Error("DIGEST_DIRECTORY must hold exactly one digest file");
	}
	if (!/^[a-f0-9]{64}$/u.test(entry.name)) {
		throw new Error("DIGEST_DIRECTORY must name its digest as 64 lowercase hex characters");
	}
	return entry.name;
}
