/**
 * Brings one host to the release its channel names. Runs from a systemd timer; the host dials out
 * and nothing dials in, so no deploy credential exists off the host to be stolen or to expire.
 *
 * The channel is a commit on the `deploy-state` branch, and that is what makes the pointer safe to
 * act on. A signature proves who wrote a pointer, never that it is the *current* one — a valid old
 * pointer replayed under a moving tag verifies perfectly — so freshness has to come from somewhere
 * with an order. Git has one: a channel commit that is an ancestor of the applied commit is a move
 * backwards, and is refused unless the pointer says the rollback is deliberate. That is the whole of
 * the rollback protection TUF builds timestamp and snapshot roles for, borrowed from a history the
 * repository already keeps.
 *
 * Two verifications, not one: the pointer is signed by the promotion workflow (which a GitHub
 * environment gates, so a signature from that identity is proof the approval happened), and the
 * release lock is signed by the release workflow. The lock is what pins every image by digest.
 *
 * A failed apply stops here and says so. It does not roll back: schema changes are forward-only in
 * this project, so putting the previous images back would leave old code on a migrated database —
 * the failure mode the rollback was supposed to prevent. Alerting a human is the honest response.
 */
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { join } from "node:path";

import { asRecord, asString, parseJson } from "./lib/json.ts";
import { output, run, succeeds } from "./lib/process.ts";

/** A stack is a Compose project this host owns. The name is also the Compose project name, so the
 * containers a previous deploy created are adopted rather than duplicated. */
export type Stack = "proxy" | "core" | "app";

const STACK_ORDER: readonly Stack[] = ["proxy", "core", "app"];

export interface Channel {
	/** The release this environment should run, as an immutable `vX.Y.Z` tag. */
	release: string;
	/** Set when a promotion deliberately moves backwards; without it a rewind is refused. */
	allowRollback?: boolean;
	/** Set to hold the environment where it is, for an incident. */
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

const RELEASE_TAG = /^v\d+\.\d+\.\d+$/;

export function parseChannel(value: unknown): Channel {
	const record = asRecord(value, "channel");
	const release = asString(record.release, "channel.release");
	if (!RELEASE_TAG.test(release))
		throw new Error(`channel.release must be an immutable vX.Y.Z tag, not ${release}`);
	return {
		release,
		allowRollback: record.allowRollback === true,
		freeze: record.freeze === true,
	};
}

/**
 * `rewinds` answers "is the channel commit an ancestor of the one already applied", which the caller
 * resolves with git. Passing it in keeps the decision itself free of process calls, so every branch
 * below is covered by a test rather than by a deployment.
 */
export function decide(
	channel: Channel,
	applied: AppliedState | undefined,
	channelCommit: string,
	rewinds: boolean,
): Decision {
	if (channel.freeze) return { action: "noop", reason: "channel is frozen" };
	if (applied?.release === channel.release && applied.channelCommit === channelCommit)
		return { action: "noop", reason: `already running ${channel.release}` };
	if (rewinds && !channel.allowRollback)
		return {
			action: "refuse",
			reason: `channel commit ${channelCommit.slice(0, 8)} precedes the applied one; set allowRollback to move back deliberately`,
		};
	return { action: "apply", release: channel.release };
}

/**
 * The value carries no information a label cannot, so the series is a constant 1 and everything
 * interesting rides in labels — the `_info` convention Prometheus uses for build metadata, which
 * keeps the version out of the metric name where a dashboard cannot group by it.
 */
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

function isStack(name: string): name is Stack {
	return STACK_ORDER.some((stack) => stack === name);
}

export function parseStacks(value: string | undefined): Stack[] {
	const names = (value ?? "").split(/[\s,]+/).filter(Boolean);
	if (names.length === 0) throw new Error("HEPHAESTUS_STACKS must name at least one stack");
	const unknown = names.filter((name) => !isStack(name));
	if (unknown.length > 0) throw new Error(`unknown stack(s): ${unknown.join(", ")}`);
	// Order is the dependency order, not the order the operator happened to type: the broker comes
	// up before the application that waits on it, and the edge before either.
	return STACK_ORDER.filter((stack) => names.includes(stack));
}

/** Everything below this line touches the host. */

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
	return {
		channel: required("HEPHAESTUS_CHANNEL"),
		stacks: parseStacks(environment.HEPHAESTUS_STACKS),
		checkout: required("HEPHAESTUS_CHECKOUT"),
		stateDirectory: environment.HEPHAESTUS_STATE ?? "/var/lib/hephaestus",
		secretsDirectory: environment.HEPHAESTUS_SECRETS ?? "/etc/hephaestus",
		metricsFile: environment.HEPHAESTUS_METRICS_FILE,
		waitTimeoutSeconds: Number(environment.HEPHAESTUS_WAIT_TIMEOUT ?? 600),
		promoteIdentity: required("HEPHAESTUS_PROMOTE_IDENTITY"),
	};
}

async function readApplied(file: string): Promise<AppliedState | undefined> {
	try {
		const record = asRecord(parseJson(await readFile(file, "utf8")), "applied state");
		return {
			release: asString(record.release, "applied.release"),
			channelCommit: asString(record.channelCommit, "applied.channelCommit"),
			appliedAt: asString(record.appliedAt, "applied.appliedAt"),
		};
	} catch {
		// A host that has never converged has no state, which is not an error — it is the first run.
		return undefined;
	}
}

/** Written to a temporary file and renamed, because the textfile collector reads whole files and a
 * scrape landing mid-write would publish a truncated series. */
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

	// The channel branch carries no code and is never merged, so fetching it costs one commit and
	// cannot drag the checkout's working tree along with it.
	await run(
		"git",
		["fetch", "--quiet", "origin", "+refs/heads/deploy-state:refs/remotes/origin/deploy-state"],
		{
			cwd: config.checkout,
		},
	);
	const channelCommit = await output("git", ["rev-parse", "refs/remotes/origin/deploy-state"], {
		cwd: config.checkout,
	});
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
		// The promotion workflow is the only identity allowed to move an environment, and a GitHub
		// environment gates that workflow — so a signature from it is proof the approval happened.
		// Cosign cannot read the certificate's environment claim, which is why the identity is the
		// workflow file itself rather than the environment name.
		"--certificate-identity",
		config.promoteIdentity,
		"--certificate-oidc-issuer",
		"https://token.actions.githubusercontent.com",
		join(scratch, "channel.json"),
	]);

	const channel = parseChannel(parseJson(channelJson));
	const rewinds = applied
		? await succeeds("git", ["merge-base", "--is-ancestor", channelCommit, applied.channelCommit], {
				cwd: config.checkout,
			})
		: false;
	const decision = decide(channel, applied, channelCommit, rewinds);

	if (decision.action === "refuse") throw new Error(decision.reason);
	if (decision.action === "noop") {
		console.log(`No change: ${decision.reason}`);
		if (config.metricsFile && applied) {
			await writeAtomic(
				config.metricsFile,
				renderMetrics({
					channel: config.channel,
					release: applied.release,
					commit: applied.channelCommit,
					success: true,
					now: startedAt,
					lastSuccessAt: new Date(applied.appliedAt),
				}),
			);
		}
		return;
	}

	// The compose files must come from the release being deployed, not from whatever the checkout
	// happens to sit on, so each release is materialised as its own worktree.
	const releaseTree = join(config.stateDirectory, "releases", decision.release);
	await run("git", ["fetch", "--quiet", "origin", "tag", decision.release, "--no-tags"], {
		cwd: config.checkout,
	});
	if (!(await succeeds("test", ["-d", releaseTree])))
		await run("git", ["worktree", "add", "--detach", "--quiet", releaseTree, decision.release], {
			cwd: config.checkout,
		});

	const lockFile = join(releaseTree, "release-lock.env");
	await run(
		process.execPath,
		[join(releaseTree, "scripts/prepare-release-lock.ts"), decision.release, lockFile],
		{
			cwd: releaseTree,
		},
	);

	for (const stack of config.stacks) {
		await run(
			"docker",
			[
				"compose",
				"--project-name",
				stack,
				"--env-file",
				join(config.secretsDirectory, `${stack}.env`),
				"--env-file",
				lockFile,
				"--file",
				join(releaseTree, `docker/compose.${stack}.yaml`),
				"up",
				"--detach",
				"--wait",
				`--wait-timeout=${config.waitTimeoutSeconds}`,
				// Without this a service removed by the release keeps running, unmanaged, until
				// someone notices it in `docker ps`.
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

/**
 * A failed run must still publish a series, or the alert that matters — "this host has not converged
 * in N minutes" — cannot tell a broken reconcile from a host that stopped running one at all.
 */
async function reportFailure(): Promise<void> {
	const metricsFile = process.env.HEPHAESTUS_METRICS_FILE;
	const channel = process.env.HEPHAESTUS_CHANNEL;
	if (!metricsFile || !channel) return;
	const applied = await readApplied(
		join(process.env.HEPHAESTUS_STATE ?? "/var/lib/hephaestus", "applied.json"),
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
		await reportFailure().catch(() => {
			// A metric that cannot be written must not replace the error that caused the failure.
		});
		throw error;
	}
}
