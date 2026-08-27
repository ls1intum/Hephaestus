import { SessionManager } from "@earendil-works/pi-coding-agent";

export interface SessionFork {
	key: string;
	sessionFile: string;
}

export interface ForkSessionsOptions {
	seedSessionFile: string;
	checkpointEntryId: string;
	keys: readonly string[];
	sessionDir?: string;
}

export function forkSessions({
	seedSessionFile,
	checkpointEntryId,
	keys,
	sessionDir,
}: ForkSessionsOptions): SessionFork[] {
	const uniqueKeys = new Set<string>();
	for (const key of keys) {
		if (!key || uniqueKeys.has(key)) {
			throw new Error(`Keys must be non-empty and unique: ${key}`);
		}
		uniqueKeys.add(key);
	}

	const forks: SessionFork[] = [];
	for (const key of keys) {
		// createBranchedSession replaces the manager's active file, so every fork must
		// start from a newly opened view of the immutable seed.
		const seed = SessionManager.open(seedSessionFile, sessionDir);
		const sessionFile = seed.createBranchedSession(checkpointEntryId);
		if (!sessionFile) {
			throw new Error("Persistent Pi session fork did not produce a session file");
		}
		forks.push({ key, sessionFile });
	}

	return forks;
}
