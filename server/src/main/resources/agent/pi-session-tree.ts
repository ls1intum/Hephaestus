import { SessionManager } from "@earendil-works/pi-coding-agent";

export interface PracticeSessionFork {
	practiceSlug: string;
	sessionFile: string;
}

export interface ForkPracticeSessionsOptions {
	seedSessionFile: string;
	checkpointEntryId: string;
	practiceSlugs: readonly string[];
	sessionDir?: string;
}

/**
 * Copies one settled session path into an independent session file per practice.
 * The caller owns lifecycle settlement; this function only operates on persisted session state.
 */
export function forkPracticeSessions({
	seedSessionFile,
	checkpointEntryId,
	practiceSlugs,
	sessionDir,
}: ForkPracticeSessionsOptions): PracticeSessionFork[] {
	const uniqueSlugs = new Set<string>();
	for (const practiceSlug of practiceSlugs) {
		if (!practiceSlug || uniqueSlugs.has(practiceSlug)) {
			throw new Error(`Practice slugs must be non-empty and unique: ${practiceSlug}`);
		}
		uniqueSlugs.add(practiceSlug);
	}

	const forks: PracticeSessionFork[] = [];
	for (const practiceSlug of practiceSlugs) {
		// createBranchedSession replaces the manager's active file, so every fork must
		// start from a newly opened view of the immutable seed.
		const seed = SessionManager.open(seedSessionFile, sessionDir);
		const sessionFile = seed.createBranchedSession(checkpointEntryId);
		if (!sessionFile) {
			throw new Error("Persistent Pi session fork did not produce a session file");
		}
		forks.push({ practiceSlug, sessionFile });
	}

	return forks;
}
