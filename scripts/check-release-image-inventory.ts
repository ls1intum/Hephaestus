import { readFileSync } from "node:fs";

type UpstreamImage = { name: string; repository: string; digest: string };

function isUpstreamImage(value: unknown): value is UpstreamImage {
	return (
		typeof value === "object" &&
		value !== null &&
		"name" in value &&
		"repository" in value &&
		"digest" in value &&
		typeof value.name === "string" &&
		typeof value.repository === "string" &&
		typeof value.digest === "string" &&
		/^sha256:[a-f0-9]{64}$/.test(value.digest)
	);
}

export function releaseImages(workflow: string): string[] {
	return [...workflow.matchAll(/image-name:\s*["']ls1intum\/hephaestus\/([^"']+)["']/g)].map(
		(match) => match[1] ?? "",
	);
}

export function validateInventory(inventory: unknown, workflow: string): string[] {
	if (
		typeof inventory !== "object" ||
		inventory === null ||
		!("images" in inventory) ||
		!Array.isArray(inventory.images) ||
		inventory.images.some((image) => typeof image !== "string" || !/^[a-z0-9-]+$/.test(image))
	)
		throw new Error("malformed release image inventory");
	const configured = inventory.images;
	if (new Set(configured).size !== configured.length)
		throw new Error("release image inventory contains duplicates");
	if (!("upstream" in inventory) || !Array.isArray(inventory.upstream))
		throw new Error("malformed upstream image inventory");
	if (inventory.upstream.some((upstream) => !isUpstreamImage(upstream)))
		throw new Error("malformed upstream image inventory");
	return releaseImages(workflow).filter((image) => !configured.includes(image));
}

if (import.meta.main) {
	const inventory = JSON.parse(readFileSync("security/release-images.json", "utf8")) as unknown;
	const missing = validateInventory(
		inventory,
		readFileSync(".github/workflows/ci-docker-build.yml", "utf8"),
	);
	if (missing.length > 0)
		throw new Error(`release images missing from evidence inventory: ${missing.join(", ")}`);
	if (
		typeof inventory !== "object" ||
		inventory === null ||
		!("upstream" in inventory) ||
		!Array.isArray(inventory.upstream)
	)
		throw new Error("malformed upstream image inventory");
	const compose = [
		"docker/compose.app.yaml",
		"docker/compose.core.yaml",
		"docker/compose.proxy.yaml",
	]
		.map((path) => readFileSync(path, "utf8"))
		.join("\n");
	for (const image of inventory.upstream) {
		if (!isUpstreamImage(image)) throw new Error("malformed upstream image inventory");
		if (!compose.includes(`@${image.digest}`))
			throw new Error(`upstream image ${image.name} is not digest-pinned in the deployment`);
	}
	const upstreamDigests = new Set(
		inventory.upstream.filter(isUpstreamImage).map((image) => image.digest),
	);
	for (const match of compose.matchAll(/^\s*image:\s*["']?([^\s"']+)/gm)) {
		const reference = match[1] ?? "";
		if (reference.startsWith("ghcr.io/ls1intum/hephaestus/")) continue;
		const digest = reference.match(/@(sha256:[a-f0-9]{64})$/)?.[1];
		if (!digest || !upstreamDigests.has(digest))
			throw new Error(`deployed upstream image is unpinned or missing from evidence: ${reference}`);
	}
}
