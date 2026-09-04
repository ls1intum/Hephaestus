/**
 * Builds the images a commit channel pins, so an environment can follow the default branch.
 *
 * A release ships a signed lock listing every image by digest. A commit has no release, but it has
 * everything the lock is made of: the upstream images are pinned in `security/release-images.json`,
 * which is part of the commit, and the first-party images are published by CI under the commit's
 * own tag. Resolving those tags to digests here produces the same pinning without a release.
 *
 * Provenance comes from GitHub's artifact attestations rather than from a signature over the list:
 * `gh attestation verify` fails unless this repository's build workflow signed that exact digest on
 * a GitHub-hosted runner, which is the claim a release lock's signature makes as well.
 */
import { readFile } from "node:fs/promises";

import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";

export interface ImageInventory {
	readonly images: readonly string[];
	readonly upstream: readonly {
		readonly name: string;
		readonly repository: string;
		readonly digest: string;
	}[];
}

export interface ResolvedImage {
	readonly key: string;
	readonly reference: string;
}

const COMMIT_SHA = /^[0-9a-f]{40}$/;
const DIGEST = /^sha256:[0-9a-f]{64}$/;

/** `application-server` is read from the environment as HEPHAESTUS_IMAGE_APPLICATION_SERVER. */
export function environmentKey(image: string): string {
	return `HEPHAESTUS_IMAGE_${image.toUpperCase().replaceAll("-", "_")}`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}

function stringField(source: Record<string, unknown>, field: string, label: string): string {
	const value = source[field];
	if (typeof value !== "string") throw new Error(`${label} has no ${field}`);
	return value;
}

export function parseInventory(value: unknown): ImageInventory {
	if (!isRecord(value)) throw new Error("image inventory is not an object");
	const { images, upstream } = value;
	if (!Array.isArray(images) || !images.every((image) => typeof image === "string"))
		throw new Error("image inventory lists no first-party images");
	if (!Array.isArray(upstream)) throw new Error("image inventory lists no upstream images");
	return {
		images,
		upstream: upstream.map((entry, index) => {
			const label = `upstream[${index}]`;
			if (!isRecord(entry)) throw new Error(`${label} is not an object`);
			const digest = stringField(entry, "digest", label);
			if (!DIGEST.test(digest))
				throw new Error(`upstream ${stringField(entry, "name", label)} is not pinned by digest`);
			return {
				name: stringField(entry, "name", label),
				repository: stringField(entry, "repository", label),
				digest,
			};
		}),
	};
}

/**
 * Every image the Compose files read, pinned by digest: the upstream ones as the commit pins them,
 * and the first-party ones as the registry answers for this commit's tag.
 */
export async function resolveImages(
	inventory: ImageInventory,
	commit: string,
	owner: string,
	resolve: (repository: string, commit: string) => Promise<string>,
): Promise<Record<string, string>> {
	if (!COMMIT_SHA.test(commit)) throw new Error(`expected a full commit, got ${commit}`);
	const images: Record<string, string> = {};
	for (const entry of inventory.upstream)
		images[environmentKey(entry.name)] = `${entry.repository}@${entry.digest}`;
	for (const image of inventory.images) {
		const repository = `ghcr.io/${owner}/${image}`;
		const digest = await resolve(repository, commit);
		if (!DIGEST.test(digest)) throw new Error(`${image} did not resolve to a digest at ${commit}`);
		images[environmentKey(image)] = `${repository}@${digest}`;
	}
	return images;
}

export async function readInventory(path: string): Promise<ImageInventory> {
	return parseInventory(JSON.parse(await readFile(path, "utf8")));
}

/**
 * Resolves a first-party image and refuses it unless GitHub attests that this repository's build
 * workflow produced that exact digest on a hosted runner. That attestation is what stands in for a
 * release lock's signature: both say "our CI built this", and this one is checked per image.
 */
async function resolveAndVerify(repository: string, commit: string): Promise<string> {
	const { execFileSync } = await import("node:child_process");
	const ghRepository = process.env.GITHUB_REPOSITORY;
	if (!ghRepository) throw new Error("GITHUB_REPOSITORY is required to verify build provenance");

	const digest = execFileSync(
		"docker",
		[
			"buildx",
			"imagetools",
			"inspect",
			`${repository}:${commit}`,
			"--format",
			"{{.Manifest.Digest}}",
		],
		{ encoding: "utf8", maxBuffer: CAPTURE_LIMIT_BYTES },
	).trim();

	execFileSync(
		"gh",
		[
			"attestation",
			"verify",
			`oci://${repository}@${digest}`,
			"--repo",
			ghRepository,
			"--signer-workflow",
			`${ghRepository}/.github/workflows/reusable-docker-build.yml`,
			// The runner is part of what the signature attests to, and no self-hosted runner is in
			// this repository's build path.
			"--deny-self-hosted-runners",
			// Without this the check proves only that this workflow attested this digest at some
			// point, so an older image retagged onto the commit would pass. This binds the
			// attestation to the source revision being deployed.
			"--source-digest",
			commit,
			"--predicate-type",
			"https://slsa.dev/provenance/v1",
		],
		{ stdio: ["ignore", "ignore", "inherit"], maxBuffer: CAPTURE_LIMIT_BYTES },
	);
	return digest;
}

if (import.meta.main) {
	const [commit, owner = "hephaestus-build"] = process.argv.slice(2);
	if (!commit) throw new Error("usage: commit-image-lock <commit> [owner]");
	const inventory = await readInventory("security/release-images.json");
	const images = await resolveImages(inventory, commit, owner, resolveAndVerify);
	process.stdout.write(`${JSON.stringify(images, null, 2)}\n`);
}
