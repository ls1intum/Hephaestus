import { existsSync } from "node:fs";
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { join } from "node:path";

import { asRecord, asString, parseJson } from "./lib/json.ts";
import { output, run, succeeds } from "./lib/process.ts";

export type Stack = "proxy" | "core" | "app";

/**
 * The application server runs the Liquibase migration the webhook runtime in `core` reads, and the
 * edge comes last so it never routes to a stack that is still starting. The push deploy declares the
 * same order in `.github/workflows/deploy-locked-compose.yml`, and one test holds the two together.
 */
const STACK_ORDER: readonly Stack[] = ["app", "core", "proxy"];

/**
 * The application server never reports ready without the broker, and the broker lives in the `core`
 * project, which Compose cannot express as a dependency. PostgreSQL needs no entry: it sits in `app`
 * beside the services that declare `depends_on` on it.
 */
const FOUNDATION: Partial<Record<Stack, readonly string[]>> = {
	core: ["nats-server"],
};

export interface Channel {
	/** What this channel asks the host to run: a release tag, or the commit a build came from. */
	release: string;
	/** Set only by a commit channel: the digests to run, pinned by the channel's own signature. */
	images?: Readonly<Record<string, string>>;
	allowRollback?: boolean;
	freeze?: boolean;
}

export interface AppliedState {
	release: string;
	channelCommit: string;
	appliedAt: string;
}

export type Decision =
	| { action: "apply"; release: string }
	| { action: "noop"; reason: string }
	| { action: "refuse"; reason: string };

const RELEASE_TAG = /^v(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)$/;
const CHANNEL_NAME = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const COMMIT_SHA = /^[0-9a-f]{40}$/;
const IMAGE_KEY = /^HEPHAESTUS_IMAGE_[A-Z0-9_]+$/;
const IMAGE_DIGEST = /^[^\s@]+@sha256:[0-9a-f]{64}$/;

function optionalBoolean(value: unknown, label: string): boolean {
	if (value === undefined) return false;
	if (typeof value !== "boolean") throw new TypeError(`${label} must be a boolean`);
	return value;
}

/**
 * A channel names either a release or a commit.
 *
 * A release is a published artifact: its lock, provenance and signature are fetched and verified
 * before anything runs, which is what production is promoted with. A commit is a build of the
 * default branch, and carries the digests to run in the channel itself — they are covered by the
 * channel's own signature, so the same authority stands behind them without a release having to
 * exist. That is what lets an environment follow the default branch continuously.
 */
export function parseChannel(value: unknown): Channel {
	const record = asRecord(value, "channel");
	const allowRollback = optionalBoolean(record.allowRollback, "channel.allowRollback");
	const freeze = optionalBoolean(record.freeze, "channel.freeze");

	if (record.commit !== undefined) {
		if (record.release !== undefined)
			throw new Error("channel names both a release and a commit; it must name one");
		const commit = asString(record.commit, "channel.commit");
		if (!COMMIT_SHA.test(commit))
			throw new Error(`channel.commit must be a full 40-character commit, not ${commit}`);
		return { release: commit, images: parseImages(record.images), allowRollback, freeze };
	}

	const release = asString(record.release, "channel.release");
	if (!RELEASE_TAG.test(release))
		throw new Error(`channel.release must be an immutable vX.Y.Z tag, not ${release}`);
	return { release, allowRollback, freeze };
}

/**
 * The images a commit channel pins, as the environment the Compose files read. Every value is a
 * digest: a tag would let the registry answer with something else later, which is the whole reason
 * the release path pins digests too.
 */
function parseImages(value: unknown): Readonly<Record<string, string>> {
	const record = asRecord(value, "channel.images");
	const images: Record<string, string> = {};
	for (const [key, reference] of Object.entries(record)) {
		if (!IMAGE_KEY.test(key)) throw new Error(`channel.images has an unusable name ${key}`);
		const pinned = asString(reference, `channel.images.${key}`);
		if (!IMAGE_DIGEST.test(pinned))
			throw new Error(`channel.images.${key} must be pinned by digest, not ${pinned}`);
		images[key] = pinned;
	}
	if (Object.keys(images).length === 0) throw new Error("channel.images names no image");
	return images;
}

function compareReleases(left: string, right: string): number {
	const parts = (release: string) => release.slice(1).split(".");
	const leftParts = parts(left);
	const rightParts = parts(right);
	for (const [index, leftPart] of leftParts.entries()) {
		const rightPart = rightParts[index];
		if (rightPart === undefined) throw new Error(`invalid release ${right}`);
		if (leftPart.length !== rightPart.length) return leftPart.length - rightPart.length;
		if (leftPart !== rightPart) return leftPart < rightPart ? -1 : 1;
	}
	return 0;
}

export function decide(
	channel: Channel,
	applied: AppliedState | undefined,
	channelCommit: string,
	advances: boolean,
): Decision {
	if (applied && channelCommit !== applied.channelCommit && !advances)
		return {
			action: "refuse",
			reason: `channel commit ${channelCommit.slice(0, 8)} does not descend from the last accepted channel`,
		};
	if (channel.freeze) return { action: "noop", reason: "channel is frozen" };
	// A new channel commit naming the release already applied is a re-promotion, and re-applying is
	// how a host that was hand-patched during an incident converges again.
	if (applied?.release === channel.release && applied.channelCommit === channelCommit)
		return { action: "noop", reason: `already running ${channel.release}` };
	// Releases are ordered, so moving to an earlier one is refused on the version alone. Commits are
	// not ordered, and their protection is the channel ancestry checked above: a channel commit that
	// does not descend from the last accepted one is already refused, whichever form it names.
	const ordered = RELEASE_TAG.test(channel.release) && RELEASE_TAG.test(applied?.release ?? "");
	if (
		applied &&
		ordered &&
		compareReleases(channel.release, applied.release) < 0 &&
		!channel.allowRollback
	)
		return {
			action: "refuse",
			reason: `${channel.release} precedes ${applied.release}; set allowRollback to move back deliberately`,
		};
	return { action: "apply", release: channel.release };
}

/**
 * The environment a commit channel's images become, in the shape the Compose files already read.
 * IMAGE_TAG is what the webapp reports as its version, so an environment following the default
 * branch reports the commit it is running rather than a release it is not.
 */
export function commitLockEnvironment(
	commit: string,
	images: Readonly<Record<string, string>>,
): string {
	const lines = [`IMAGE_TAG=${commit}`, `HEPHAESTUS_RELEASE_COMMIT=${commit}`];
	for (const key of Object.keys(images).toSorted()) lines.push(`${key}=${images[key]}`);
	return `${lines.join("\n")}\n`;
}

export function renderMetrics(state: {
	channel: string;
	release: string;
	commit: string;
	success: boolean;
	now: Date;
	lastSuccessAt?: Date;
}): string {
	const seconds = (date: Date) => Math.floor(date.getTime() / 1000);
	const lines = [
		"# HELP hephaestus_deploy_info The release this host currently runs.",
		"# TYPE hephaestus_deploy_info gauge",
		`hephaestus_deploy_info{channel="${state.channel}",release="${state.release}",channel_commit="${state.commit}"} 1`,
		"# HELP hephaestus_deploy_reconcile_success Whether the last reconcile attempt succeeded.",
		"# TYPE hephaestus_deploy_reconcile_success gauge",
		`hephaestus_deploy_reconcile_success ${state.success ? 1 : 0}`,
		"# HELP hephaestus_deploy_reconcile_timestamp_seconds When the last reconcile attempt ran.",
		"# TYPE hephaestus_deploy_reconcile_timestamp_seconds gauge",
		`hephaestus_deploy_reconcile_timestamp_seconds ${seconds(state.now)}`,
	];
	if (state.lastSuccessAt) {
		lines.push(
			"# HELP hephaestus_deploy_last_success_timestamp_seconds When this host last converged.",
			"# TYPE hephaestus_deploy_last_success_timestamp_seconds gauge",
			`hephaestus_deploy_last_success_timestamp_seconds ${seconds(state.lastSuccessAt)}`,
		);
	}
	return `${lines.join("\n")}\n`;
}

/**
 * The lock only guarantees anything if nothing the release renders escapes it: a compose file that
 * hardcoded a tag would otherwise deploy an image no signature covers.
 */
export function unlockedImages(rendered: readonly string[], lockEnv: string): string[] {
	const locked = new Set(
		lockEnv
			.split("\n")
			.map((line) => line.slice(line.indexOf("=") + 1).trim())
			.filter((value) => value.includes("@sha256:")),
	);
	return rendered.filter((image) => !locked.has(image));
}

export function lockedReleaseCommit(lockEnv: string): string {
	const values = lockEnv
		.split("\n")
		.filter((line) => line.startsWith("HEPHAESTUS_RELEASE_COMMIT="))
		.map((line) => line.slice(line.indexOf("=") + 1));
	if (values.length !== 1 || !/^[a-f0-9]{40}$/.test(values[0] ?? ""))
		throw new Error("release lock must contain one source commit");
	return values[0] ?? "";
}

function isStack(name: string): name is Stack {
	return STACK_ORDER.some((stack) => stack === name);
}

export function parseStacks(value: string | undefined): Stack[] {
	const names = (value ?? "").split(/[\s,]+/).filter(Boolean);
	if (names.length === 0) throw new Error("HEPHAESTUS_STACKS must name at least one stack");
	const unknown = names.filter((name) => !isStack(name));
	if (unknown.length > 0) throw new Error(`unknown stack(s): ${unknown.join(", ")}`);
	return STACK_ORDER.filter((stack) => names.includes(stack));
}

interface HostConfig {
	channel: string;
	stacks: Stack[];
	checkout: string;
	stateDirectory: string;
	secretsDirectory: string;
	metricsFile?: string;
	waitTimeoutSeconds: number;
	promoteIdentity: string;
}

function hostConfig(environment: NodeJS.ProcessEnv): HostConfig {
	const required = (name: string): string => {
		const value = environment[name];
		if (!value) throw new Error(`${name} must be set`);
		return value;
	};
	const channel = required("HEPHAESTUS_CHANNEL");
	if (!CHANNEL_NAME.test(channel))
		throw new Error("HEPHAESTUS_CHANNEL must contain lowercase letters, digits, and hyphens");
	const stateDirectory = environment.STATE_DIRECTORY ?? "/var/lib/hephaestus";
	const waitTimeoutSeconds = Number(environment.HEPHAESTUS_WAIT_TIMEOUT ?? 600);
	if (!Number.isSafeInteger(waitTimeoutSeconds) || waitTimeoutSeconds <= 0)
		throw new Error("HEPHAESTUS_WAIT_TIMEOUT must be a positive integer");
	return {
		channel,
		stacks: parseStacks(environment.HEPHAESTUS_STACKS),
		checkout: join(stateDirectory, "checkout"),
		stateDirectory,
		secretsDirectory: environment.HEPHAESTUS_SECRETS ?? "/etc/hephaestus",
		metricsFile: environment.HEPHAESTUS_METRICS_FILE,
		waitTimeoutSeconds,
		promoteIdentity: required("HEPHAESTUS_PROMOTE_IDENTITY"),
	};
}

export async function readApplied(file: string): Promise<AppliedState | undefined> {
	try {
		const record = asRecord(parseJson(await readFile(file, "utf8")), "applied state");
		const applied = {
			release: asString(record.release, "applied.release"),
			channelCommit: asString(record.channelCommit, "applied.channelCommit"),
			appliedAt: asString(record.appliedAt, "applied.appliedAt"),
		};
		if (!RELEASE_TAG.test(applied.release)) throw new Error("applied.release must be vX.Y.Z");
		if (!/^[a-f0-9]{40}$/.test(applied.channelCommit))
			throw new Error("applied.channelCommit must be a Git commit");
		if (
			!Number.isFinite(Date.parse(applied.appliedAt)) ||
			new Date(applied.appliedAt).toISOString() !== applied.appliedAt
		)
			throw new Error("applied.appliedAt must be an ISO timestamp");
		return applied;
	} catch (error) {
		if (typeof error === "object" && error !== null && "code" in error && error.code === "ENOENT")
			return undefined;
		throw error;
	}
}

/** Renamed into place: a scrape landing mid-write would otherwise publish a truncated series. */
async function writeAtomic(file: string, contents: string): Promise<void> {
	const temporary = `${file}.tmp`;
	await writeFile(temporary, contents, { mode: 0o644 });
	await rename(temporary, file);
}

async function main(): Promise<void> {
	const config = hostConfig(process.env);
	const appliedFile = join(config.stateDirectory, "applied.json");
	const applied = await readApplied(appliedFile);
	const startedAt = new Date();

	await mkdir(config.stateDirectory, { recursive: true });

	await run(
		"git",
		["fetch", "--quiet", "origin", "+refs/heads/deploy-state:refs/remotes/origin/deploy-state"],
		{
			cwd: config.checkout,
		},
	);
	// Trimmed because the SHA is compared and interpolated; the blobs below are not, since the
	// channel must reach cosign byte for byte as it was signed.
	const channelCommit = (
		await output("git", ["rev-parse", "refs/remotes/origin/deploy-state"], {
			cwd: config.checkout,
		})
	).trim();
	const channelPath = `channels/${config.channel}.json`;
	const channelJson = await output("git", ["show", `${channelCommit}:${channelPath}`], {
		cwd: config.checkout,
	});
	const signature = await output("git", ["show", `${channelCommit}:${channelPath}.sigstore.json`], {
		cwd: config.checkout,
	});

	// Verifying before parsing keeps unverified bytes from reaching any decision.
	const scratch = join(config.stateDirectory, "channel");
	await mkdir(scratch, { recursive: true });
	await writeFile(join(scratch, "channel.json"), channelJson);
	await writeFile(join(scratch, "channel.sigstore.json"), signature);
	await run("cosign", [
		"verify-blob",
		"--bundle",
		join(scratch, "channel.sigstore.json"),
		// Cosign cannot verify the certificate's environment claim, so the gate is expressed as the
		// identity of the workflow the environment protects: only an approved run can produce it.
		"--certificate-identity",
		config.promoteIdentity,
		"--certificate-oidc-issuer",
		"https://token.actions.githubusercontent.com",
		join(scratch, "channel.json"),
	]);

	const channel = parseChannel(parseJson(channelJson));
	const advances =
		applied && channelCommit !== applied.channelCommit
			? await succeeds(
					"git",
					["merge-base", "--is-ancestor", applied.channelCommit, channelCommit],
					{
						cwd: config.checkout,
					},
				)
			: true;
	const decision = decide(channel, applied, channelCommit, advances);

	if (decision.action === "refuse") throw new Error(decision.reason);
	if (decision.action === "noop") {
		console.log(`No change: ${decision.reason}`);
		if (applied && applied.channelCommit !== channelCommit) {
			await writeAtomic(
				appliedFile,
				`${JSON.stringify({ ...applied, channelCommit }, null, "\t")}\n`,
			);
		}
		if (config.metricsFile && applied) {
			await writeAtomic(
				config.metricsFile,
				renderMetrics({
					channel: config.channel,
					release: applied.release,
					commit: channelCommit,
					success: true,
					now: startedAt,
					lastSuccessAt: new Date(applied.appliedAt),
				}),
			);
		}
		return;
	}

	const releaseTree = join(config.stateDirectory, "releases", decision.release);
	await run("git", ["fetch", "--quiet", "origin", "tag", decision.release, "--no-tags"], {
		cwd: config.checkout,
	});
	const releaseCommit = (
		await output("git", ["rev-parse", `${decision.release}^{commit}`], { cwd: config.checkout })
	).trim();
	if (existsSync(releaseTree)) {
		const worktreeCommit = (
			await output("git", ["rev-parse", "HEAD"], { cwd: releaseTree })
		).trim();
		if (worktreeCommit !== releaseCommit)
			throw new Error(`${releaseTree} is not the worktree for ${decision.release}`);
	} else {
		await run("git", ["worktree", "add", "--detach", "--quiet", releaseTree, decision.release], {
			cwd: config.checkout,
		});
	}
	// Tracked content only: Compose writes into the worktree it renders from — the proxy stack's ACME
	// store is `docker/letsencrypt/` — so counting untracked files would turn every retry after a
	// partial apply into a permanent refusal. An untracked file cannot alter a tracked Compose file,
	// and anyone who can write here already has the Docker socket.
	const worktreeChanges = await output(
		"git",
		["status", "--porcelain=v1", "--untracked-files=no"],
		{
			cwd: releaseTree,
		},
	);
	if (worktreeChanges) throw new Error(`${releaseTree} differs from ${decision.release}`);

	const lockDirectory = join(config.stateDirectory, "release-locks");
	await mkdir(lockDirectory, { recursive: true });
	const lockFile = join(lockDirectory, `${decision.release}.env`);
	if (channel.images) {
		// A commit channel carries its own digests, and the channel file they arrived in was
		// signature-verified before this point, so there is no release to fetch or verify. The
		// version the instance reports is the commit, which is what the environment is following.
		await writeFile(lockFile, commitLockEnvironment(decision.release, channel.images), {
			mode: 0o600,
		});
	} else {
		// A release must not supply the code that decides whether that same release is trusted.
		await run(
			process.execPath,
			[join(config.checkout, "scripts/prepare-release-lock.ts"), decision.release, lockFile],
			{ cwd: releaseTree },
		);
	}

	const lockEnv = await readFile(lockFile, "utf8");
	if (!channel.images && lockedReleaseCommit(lockEnv) !== releaseCommit)
		throw new Error(`signed release lock does not cover the ${decision.release} source tree`);

	const composeFor = (stack: Stack): string[] => [
		"compose",
		"--project-name",
		stack,
		"--env-file",
		join(config.secretsDirectory, `${stack}.env`),
		"--env-file",
		lockFile,
		"--file",
		join(releaseTree, `docker/compose.${stack}.yaml`),
	];

	// Every stack is verified before any container starts, so a release that renders an unlocked image
	// cannot get one running in the window before the guard refuses it.
	const composeArgsByStack = new Map<Stack, string[]>();
	for (const stack of config.stacks) {
		const composeArgs = composeFor(stack);
		const rendered = asRecord(
			parseJson(
				await output("docker", [...composeArgs, "config", "--format", "json"], {
					cwd: releaseTree,
				}),
			),
			`${stack} configuration`,
		);
		const images = Object.values(asRecord(rendered.services, `${stack}.services`)).map((service) =>
			asString(asRecord(service, `${stack} service`).image, `${stack} service image`),
		);
		const unlocked = unlockedImages(images, lockEnv);
		if (unlocked.length > 0)
			throw new Error(`${stack} renders images outside the release lock: ${unlocked.join(", ")}`);
		composeArgsByStack.set(stack, composeArgs);
	}

	for (const [stack, composeArgs] of composeArgsByStack) {
		const foundation = FOUNDATION[stack];
		if (!foundation) continue;
		await run(
			"docker",
			[
				...composeArgs,
				"up",
				"--detach",
				"--wait",
				`--wait-timeout=${config.waitTimeoutSeconds}`,
				...foundation,
			],
			{ cwd: releaseTree },
		);
	}

	for (const [, composeArgs] of composeArgsByStack) {
		await run(
			"docker",
			[
				...composeArgs,
				"up",
				"--detach",
				"--wait",
				`--wait-timeout=${config.waitTimeoutSeconds}`,
				// A service the release removed would otherwise keep running, unmanaged.
				"--remove-orphans",
			],
			{ cwd: releaseTree },
		);
	}

	const finishedAt = new Date();
	await writeAtomic(
		appliedFile,
		`${JSON.stringify({ release: decision.release, channelCommit, appliedAt: finishedAt.toISOString() }, null, "\t")}\n`,
	);
	if (config.metricsFile)
		await writeAtomic(
			config.metricsFile,
			renderMetrics({
				channel: config.channel,
				release: decision.release,
				commit: channelCommit,
				success: true,
				now: finishedAt,
				lastSuccessAt: finishedAt,
			}),
		);
	console.log(`Applied ${decision.release} to ${config.stacks.join(", ")}`);
}

/** Without a series on failure, a broken reconcile is indistinguishable from a host running none. */
async function reportFailure(): Promise<void> {
	const metricsFile = process.env.HEPHAESTUS_METRICS_FILE;
	const channel = process.env.HEPHAESTUS_CHANNEL;
	if (!metricsFile || !channel) return;
	const applied = await readApplied(
		join(process.env.STATE_DIRECTORY ?? "/var/lib/hephaestus", "applied.json"),
	);
	await writeAtomic(
		metricsFile,
		renderMetrics({
			channel,
			release: applied?.release ?? "none",
			commit: applied?.channelCommit ?? "none",
			success: false,
			now: new Date(),
			lastSuccessAt: applied ? new Date(applied.appliedAt) : undefined,
		}),
	);
}

if (process.argv[1] === import.meta.filename) {
	try {
		await main();
	} catch (error) {
		// An unwritable metric must not replace the error that caused the failure.
		await reportFailure().catch(() => {});
		throw error;
	}
}
