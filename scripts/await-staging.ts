/**
 * Waits until staging's channel names the commit a release was cut from, so production is never
 * offered a release staging has not rehearsed. A channel that cannot be read is retried, because
 * the API answered nothing to decide on; a channel that does not parse, or is frozen, fails at once.
 */
import { setTimeout as sleep } from "node:timers/promises";

import { requiredEnv } from "./lib/env.ts";
import { parseJson } from "./lib/json.ts";
import { output } from "./lib/process.ts";
import { parseChannel } from "./reconcile-deployment.ts";

export async function awaitStaging(
	commit: string,
	readChannel: () => Promise<string>,
	signal: AbortSignal,
	pollMilliseconds = 20_000,
): Promise<void> {
	while (!signal.aborted) {
		let contents: string | undefined;
		try {
			contents = await readChannel();
		} catch (error) {
			console.error(`staging's channel could not be read: ${String(error)}`);
		}
		if (contents !== undefined) {
			const channel = parseChannel(parseJson(contents));
			if (channel.release === commit) return;
			if (channel.freeze)
				throw new Error("staging is frozen, so this release has not been rehearsed there");
		}
		await sleep(pollMilliseconds, undefined, { signal }).catch(() => {});
	}
	throw new Error(
		`staging did not reach ${commit} — production is not offered an unrehearsed release`,
	);
}

if (import.meta.main) {
	const commit = requiredEnv(process.env, "COMMIT");
	const repository = requiredEnv(process.env, "GITHUB_REPOSITORY");
	await awaitStaging(
		commit,
		async () =>
			Buffer.from(
				await output("gh", [
					"api",
					`repos/${repository}/contents/channels/staging.json?ref=deploy-state`,
					"--jq",
					".content",
				]),
				"base64",
			).toString("utf8"),
		AbortSignal.timeout(25 * 60 * 1000),
	);
	console.log(`staging is on ${commit}`);
}
