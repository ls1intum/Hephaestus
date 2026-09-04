import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import {
	commitLockEnvironment,
	decide,
	lockedReleaseCommit,
	parseChannel,
	parseStacks,
	readApplied,
	renderMetrics,
	unlockedImages,
} from "./reconcile-deployment.ts";

const applied = {
	release: "v0.75.2",
	channelCommit: "b".repeat(40),
	appliedAt: "2026-09-03T20:00:00.000Z",
};

await test("a channel names an immutable release", () => {
	assert.deepEqual(parseChannel({ release: "v1.2.3" }), {
		release: "v1.2.3",
		allowRollback: false,
		freeze: false,
	});
	assert.throws(() => parseChannel({ release: "main" }), /immutable vX\.Y\.Z tag/);
	assert.throws(() => parseChannel({ release: "v01.2.3" }), /immutable vX\.Y\.Z tag/);
	assert.throws(() => parseChannel({ release: "v1.2.3-rc.1" }), /immutable vX\.Y\.Z tag/);
	assert.throws(() => parseChannel({}), /channel\.release/);
	assert.throws(() => parseChannel({ release: "v1.2.3", freeze: "yes" }), /must be a boolean/);
});

await test("an unchanged channel is a no-op, so most ticks do nothing", () => {
	assert.deepEqual(decide({ release: "v0.75.2" }, applied, applied.channelCommit, false), {
		action: "noop",
		reason: "already running v0.75.2",
	});
});

await test("re-promoting the release a host already runs re-applies it, so drift converges", () => {
	assert.deepEqual(decide({ release: applied.release }, applied, "c".repeat(40), true), {
		action: "apply",
		release: applied.release,
	});
});

await test("a descendant channel commit applies", () => {
	assert.deepEqual(decide({ release: "v0.75.3" }, applied, "c".repeat(40), true), {
		action: "apply",
		release: "v0.75.3",
	});
});

await test("a replay or divergent channel history is refused", () => {
	const decision = decide({ release: "v0.75.3" }, applied, "a".repeat(40), false);
	assert.equal(decision.action, "refuse");
});

await test("a downgrade requires an explicit rollback on a new channel commit", () => {
	assert.equal(decide({ release: "v0.75.1" }, applied, "c".repeat(40), true).action, "refuse");
	assert.deepEqual(
		decide({ release: "v0.75.1", allowRollback: true }, applied, "c".repeat(40), true),
		{ action: "apply", release: "v0.75.1" },
	);
	assert.equal(
		decide({ release: "v0.75.1", allowRollback: true }, applied, "a".repeat(40), false).action,
		"refuse",
	);
});

await test("a frozen channel holds the host where it is, even against a newer release", () => {
	assert.deepEqual(decide({ release: "v0.99.0", freeze: true }, applied, "c".repeat(40), true), {
		action: "noop",
		reason: "channel is frozen",
	});
});

await test("a host with no state converges on its first run", () => {
	assert.deepEqual(decide({ release: "v0.75.2" }, undefined, "a".repeat(40), false), {
		action: "apply",
		release: "v0.75.2",
	});
});

await test("only a missing applied-state file means first run", async () => {
	const directory = await mkdtemp(join(tmpdir(), "reconcile-state-"));
	try {
		assert.equal(await readApplied(join(directory, "missing.json")), undefined);
		const corrupt = join(directory, "applied.json");
		await writeFile(corrupt, "not json");
		await assert.rejects(readApplied(corrupt), /JSON/);
		await writeFile(
			corrupt,
			JSON.stringify({
				release: "v1.2.3",
				channelCommit: "d".repeat(40),
				appliedAt: "September 4, 2026",
			}),
		);
		await assert.rejects(readApplied(corrupt), /ISO timestamp/);
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});

await test("stacks come up in dependency order regardless of how they were configured", () => {
	assert.deepEqual(parseStacks("core app"), ["app", "core"]);
	assert.deepEqual(parseStacks("proxy, core, app"), ["app", "core", "proxy"]);
	assert.throws(() => parseStacks("app database"), /unknown stack/);
	assert.throws(() => parseStacks(""), /at least one stack/);
});

await test("the version rides in labels so a dashboard can group by it", () => {
	const metrics = renderMetrics({
		channel: "staging",
		release: "v0.75.2",
		commit: "abc",
		success: true,
		now: new Date("2026-09-03T21:00:00.000Z"),
		lastSuccessAt: new Date("2026-09-03T21:00:00.000Z"),
	});
	assert.match(
		metrics,
		/hephaestus_deploy_info\{channel="staging",release="v0\.75\.2",channel_commit="abc"\} 1/,
	);
	assert.match(metrics, /hephaestus_deploy_reconcile_success 1/);
	assert.match(metrics, /hephaestus_deploy_last_success_timestamp_seconds 1788469200/);
	assert.ok(metrics.endsWith("\n"));
});

await test("a failed run still publishes a series, or silence and failure look alike", () => {
	const metrics = renderMetrics({
		channel: "production",
		release: "none",
		commit: "none",
		success: false,
		now: new Date("2026-09-03T21:00:00.000Z"),
	});
	assert.match(metrics, /hephaestus_deploy_reconcile_success 0/);
	assert.doesNotMatch(metrics, /last_success/);
});

await test("an image the release lock does not cover is refused", () => {
	const lock = [
		"HEPHAESTUS_IMAGE_APPLICATION_SERVER=ghcr.io/o/application-server@sha256:aaa",
		"HEPHAESTUS_IMAGE_POSTGRES=ghcr.io/o/postgres@sha256:bbb",
		`HEPHAESTUS_RELEASE_COMMIT=${"d".repeat(40)}`,
		"IMAGE_TAG=v1.2.3",
	].join("\n");
	assert.equal(lockedReleaseCommit(lock), "d".repeat(40));
	assert.deepEqual(
		unlockedImages(
			["ghcr.io/o/application-server@sha256:aaa", "ghcr.io/o/postgres@sha256:bbb"],
			lock,
		),
		[],
	);
	assert.deepEqual(unlockedImages(["ghcr.io/o/webapp:latest"], lock), ["ghcr.io/o/webapp:latest"]);
	assert.throws(
		() => lockedReleaseCommit(lock.replace("d".repeat(40), "invalid")),
		/source commit/,
	);
});

const commit = "c".repeat(40);
const digest = `sha256:${"1".repeat(64)}`;
const images = { HEPHAESTUS_IMAGE_WEBAPP: `ghcr.io/o/webapp@${digest}` };

await test("a channel may follow the default branch by naming a commit and what to run", () => {
	assert.deepEqual(parseChannel({ commit, images }), {
		release: commit,
		images,
		allowRollback: false,
		freeze: false,
	});
});

await test("a commit channel pins every image by digest, never by tag", () => {
	// A tag lets the registry answer with something else later, which is the reason the release
	// path pins digests as well.
	assert.throws(
		() => parseChannel({ commit, images: { HEPHAESTUS_IMAGE_WEBAPP: "ghcr.io/o/webapp:main" } }),
		/pinned by digest/,
	);
	assert.throws(() => parseChannel({ commit, images: {} }), /names no image/);
	assert.throws(
		() => parseChannel({ commit, images: { WEBAPP: `ghcr.io/o/w@${digest}` } }),
		/unusable name/,
	);
});

await test("a commit channel names a whole commit, so it cannot be an abbreviation", () => {
	assert.throws(() => parseChannel({ commit: "c".repeat(7), images }), /full 40-character commit/);
	assert.throws(() => parseChannel({ commit: "main", images }), /full 40-character commit/);
});

await test("a channel names a release or a commit, never both", () => {
	assert.throws(() => parseChannel({ release: "v1.2.3", commit, images }), /must name one/);
});

await test("a commit is applied even though it cannot be ordered against a release", () => {
	// Releases compare by version; commits have no order at all. What stops either from moving
	// backwards is the channel ancestry check, which runs before this.
	assert.deepEqual(decide({ release: commit, images }, applied, "d".repeat(40), true), {
		action: "apply",
		release: commit,
	});
});

await test("a rewind is still refused when the channel follows a commit", () => {
	assert.equal(
		decide({ release: commit, images }, applied, "a".repeat(40), false).action,
		"refuse",
	);
});

await test("an environment following the branch reports the commit it runs", () => {
	const rendered = commitLockEnvironment(commit, {
		HEPHAESTUS_IMAGE_WEBAPP: `ghcr.io/o/webapp@${digest}`,
		HEPHAESTUS_IMAGE_ALPINE: `docker.io/library/alpine@${digest}`,
	});
	assert.match(rendered, new RegExp(`^IMAGE_TAG=${commit}$`, "m"));
	// Sorted, so the same channel always renders the same file.
	assert.ok(
		rendered.indexOf("HEPHAESTUS_IMAGE_ALPINE") < rendered.indexOf("HEPHAESTUS_IMAGE_WEBAPP"),
	);
	assert.ok(rendered.endsWith("\n"));
});
