import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import {
	environmentKey,
	parseInventory,
	readInventory,
	resolveImages,
} from "./commit-image-lock.ts";

const commit = "c".repeat(40);
const digest = `sha256:${"1".repeat(64)}`;
const resolver = () => Promise.resolve(digest);

await test("a commit channel pins every image the stacks actually read", async () => {
	// The point of the lock is that nothing renders from a floating tag. If the stacks grow an
	// image this does not resolve, Compose would fail to render on the host rather than here.
	const inventory = await readInventory(
		new URL("../security/release-images.json", import.meta.url).pathname,
	);
	const images = await resolveImages(inventory, commit, "hephaestus-build", resolver);

	const referenced = new Set<string>();
	for (const stack of ["app", "core", "proxy"]) {
		const file = readFileSync(new URL(`../docker/compose.${stack}.yaml`, import.meta.url), "utf8");
		for (const [, name] of file.matchAll(/\$\{(HEPHAESTUS_IMAGE_[A-Z0-9_]+)/g))
			if (name) referenced.add(name);
	}
	assert.ok(referenced.size > 0, "the stacks reference no images at all");
	for (const name of referenced)
		assert.ok(name in images, `${name} is read by a stack but not pinned for a commit deploy`);
});

await test("an upstream image is pinned as the commit pins it, not looked up", async () => {
	const inventory = parseInventory({
		images: [],
		upstream: [{ name: "nginx", repository: "docker.io/library/nginx", digest }],
	});
	const images = await resolveImages(inventory, commit, "o", () =>
		Promise.reject(new Error("upstream must not be resolved from a registry")),
	);
	assert.equal(images.HEPHAESTUS_IMAGE_NGINX, `docker.io/library/nginx@${digest}`);
});

await test("a first-party image is taken from this commit's own build", async () => {
	const seen: string[] = [];
	const images = await resolveImages(
		parseInventory({ images: ["application-server"], upstream: [] }),
		commit,
		"hephaestus-build",
		(repository, at) => {
			seen.push(`${repository}@${at}`);
			return Promise.resolve(digest);
		},
	);
	assert.deepEqual(seen, [`ghcr.io/hephaestus-build/application-server@${commit}`]);
	assert.equal(
		images.HEPHAESTUS_IMAGE_APPLICATION_SERVER,
		`ghcr.io/hephaestus-build/application-server@${digest}`,
	);
});

await test("anything that is not a digest is refused rather than deployed", async () => {
	const inventory = parseInventory({ images: ["webapp"], upstream: [] });
	await assert.rejects(
		resolveImages(inventory, commit, "o", () => Promise.resolve("main")),
		/did not resolve to a digest/,
	);
	await assert.rejects(resolveImages(inventory, "abc", "o", resolver), /expected a full commit/);
	assert.throws(
		() =>
			parseInventory({ images: [], upstream: [{ name: "n", repository: "r", digest: "latest" }] }),
		/not pinned by digest/,
	);
});

await test("the environment name is the one the Compose files read", () => {
	assert.equal(environmentKey("application-server"), "HEPHAESTUS_IMAGE_APPLICATION_SERVER");
	assert.equal(environmentKey("agent-pi"), "HEPHAESTUS_IMAGE_AGENT_PI");
});
