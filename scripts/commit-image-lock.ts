/** Resolve commit-tagged images and verify their build provenance before channel signing. */
import { readFile } from "node:fs/promises";

import { asArray, asRecord, asString, asStringArray, parseJson } from "./lib/json.ts";
import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";

export interface ImageInventory {
	readonly images: readonly string[];
	readonly upstream: readonly {
		readonly name: string;
		readonly repository: string;
		readonly digest: string;
	}[];
}

const COMMIT_SHA = /^[0-9a-f]{40}$/;
const DIGEST = /^sha256:[0-9a-f]{64}$/;

export function environmentKey(image: string): string {
	return `HEPHAESTUS_IMAGE_${image.toUpperCase().replaceAll("-", "_")}`;
}

export function parseInventory(value: unknown): ImageInventory {
	const record = asRecord(value, "image inventory");
	return {
		images: asStringArray(record.images, "image inventory.images"),
		upstream: asArray(record.upstream, "image inventory.upstream").map((candidate, index) => {
			const label = `upstream[${index}]`;
			const entry = asRecord(candidate, label);
			const name = asString(entry.name, `${label}.name`);
			const digest = asString(entry.digest, `${label}.digest`);
			if (!DIGEST.test(digest)) throw new Error(`upstream ${name} is not pinned by digest`);
			return { name, repository: asString(entry.repository, `${label}.repository`), digest };
		}),
	};
}

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
	return parseInventory(parseJson(await readFile(path, "utf8")));
}

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
			"--deny-self-hosted-runners",
			// Reject an older attested image retagged onto the requested commit.
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
	const [commit, owner = "hephaestus-build", inventoryPath = "security/release-images.json"] =
		process.argv.slice(2);
	if (!commit) throw new Error("usage: commit-image-lock <commit> [owner] [inventory]");
	// The inventory belongs to the commit being promoted; this file may be newer than it.
	const inventory = await readInventory(inventoryPath);
	const images = await resolveImages(inventory, commit, owner, resolveAndVerify);
	process.stdout.write(`${JSON.stringify(images, null, 2)}\n`);
}
