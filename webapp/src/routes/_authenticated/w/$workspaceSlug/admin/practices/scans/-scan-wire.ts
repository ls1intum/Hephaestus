import { HttpResponse, http, type JsonBodyType } from "msw";
import type { z } from "zod";

import type {
	AccountRef,
	ItemRef,
	Practice,
	PracticeDefinition,
	Problem,
	ScanCounts,
	ScanDetail,
	ScanReport,
	ScanSummary,
} from "@/api/types.gen";
import type {
	scanItemsPageSchema,
	scanProblemsPageSchema,
} from "@/components/admin/practice-scans/scan-report-schemas";
import type { Wire } from "@/lib/dates";
import { ownMembership, workspaceListItem } from "@/mocks/fixtures/workspaces";

/** What the scan routes read as JSON, with ISO strings where the client makes `Date`s. */

export const SCAN_ID = "6f1c2a7e-0d4b-4a8e-9b51-1a2b3c4d5e6f";
export const COMMIT = "4f2a9c1e8b7d6a5f4e3d2c1b0a9f8e7d6c5b4a39";

const ADA: Wire<AccountRef> = {
	id: "7",
	displayName: "Ada Lovelace",
	avatarUrl: null,
	status: "ACTIVE",
	identities: [{ provider: "GITHUB", login: "ada", displayName: "Ada Lovelace", avatarUrl: null }],
};

export function wireRepository(id = "300", name = "hephaestus"): Wire<ItemRef> {
	return {
		id,
		kind: "scm.repository",
		label: `ls1intum/${name}`,
		container: "ls1intum",
		parentId: null,
		path: null,
		provider: "GITHUB",
		removedAt: null,
		title: name,
		url: `https://github.com/ls1intum/${name}`,
	};
}

export function wireFile(id: string, path: string, removedAt: string | null = null): Wire<ItemRef> {
	return {
		...wireRepository(),
		id,
		kind: "scm.file",
		label: path,
		parentId: "300",
		path,
		removedAt,
		title: path,
		url: null,
	};
}

/** Three files reviewed against two practices: six practice and file pairs. */
export const COUNTS: ScanCounts = {
	files: 3,
	practiceItemPairs: 6,
	reviewed: 4,
	reused: 1,
	met: 2,
	notMet: 1,
	doesNotApply: 1,
	couldNotDetermine: 0,
	problems: 3,
	openProblems: 3,
	withheld: 1,
	notReviewed: 1,
};

export const RUNNING_COUNTS: ScanCounts = {
	...COUNTS,
	files: null,
	practiceItemPairs: null,
	reviewed: 1,
	reused: 0,
	met: 1,
	notMet: 0,
	doesNotApply: 0,
	problems: 0,
	openProblems: 0,
	withheld: 0,
	notReviewed: 0,
};

export function wireScan(overrides: Partial<Wire<ScanSummary>> = {}): Wire<ScanSummary> {
	return {
		id: SCAN_ID,
		repository: wireRepository(),
		ref: "main",
		itemVersion: COMMIT,
		origin: "REQUEST",
		status: "COMPLETE",
		requestedBy: ADA,
		createdAt: "2026-10-14T09:00:00Z",
		startedAt: "2026-10-14T09:00:05Z",
		completedAt: "2026-10-14T09:20:00Z",
		practiceCount: 2,
		counts: COUNTS,
		baselineScanId: "0b0b0b0b-1111-4222-8333-444455556666",
		stopReason: null,
		...overrides,
	};
}

export function wireDetail(scan: Wire<ScanSummary>): Wire<ScanDetail> {
	const active = scan.status === "QUEUED" || scan.status === "RUNNING";
	return {
		...scan,
		practices: [],
		budgetUsd: "10",
		chargedCostUsd: active ? null : "1.42",
		progress: {
			listedPairs: scan.counts.practiceItemPairs,
			terminalPairs: scan.counts.reviewed + scan.counts.withheld + scan.counts.notReviewed,
			activeJobs: active ? 3 : 0,
			queuedJobs: active ? 2 : 0,
		},
	};
}

export function wireReport(
	scan: Wire<ScanSummary>,
	overrides: Partial<Wire<ScanReport>> = {},
): Wire<ScanReport> {
	const terminal = scan.status !== "QUEUED" && scan.status !== "RUNNING";
	return {
		scan,
		complete: scan.status === "COMPLETE",
		listingComplete: scan.counts.files !== null,
		baselineScanId: scan.baselineScanId,
		comparison: "COMPARABLE",
		counts: scan.counts,
		recordedCounts: terminal ? scan.counts : null,
		problemChanges: { new: 1, unchanged: 1, updated: 0, absent: 2 },
		practices: [],
		withheldReasons: [],
		notReviewedReasons: [],
		removalGuard: { state: "NOT_NEEDED", previousFiles: 3, wouldRemove: 0 },
		availability: {
			expired: false,
			rawRetentionEndsAt: null,
			withheldProofPairs: 0,
			expiredProofPairs: 0,
		},
		...overrides,
	};
}

export type WireItemsPage = z.input<typeof scanItemsPageSchema>;
export type WireProblemsPage = z.input<typeof scanProblemsPageSchema>;
type ReadProblem = WireProblemsPage["content"][number];
/** A problem as the server writes it, with the fields the report reads and the ones it does not. */
export type WireProblem = Omit<ReadProblem, "problem"> & {
	problem: ReadProblem["problem"] & Pick<Problem, "position" | "key">;
};

const entry = (fileId: string, path: string, practiceSlug: string) => ({
	item: wireFile(fileId, path),
	practiceSlug,
	practiceName:
		practiceSlug === "error-handling"
			? "Errors are handled where they occur"
			: "Comments explain why",
	itemVersion: COMMIT,
});

export const ITEMS_PAGE: WireItemsPage = {
	content: [
		{
			...entry("901", "server/src/ScanService.java", "error-handling"),
			status: "JUDGED",
			resultId: "r-1",
			sourceReviewId: SCAN_ID,
			outcome: "NOT_MET",
			severity: "MAJOR",
			summary: "A failed upload is logged and then ignored.",
			problemCount: 2,
			currentness: "CURRENT",
			recordedAt: "2026-10-14T09:10:00Z",
		},
		{
			...entry("902", "server/src/ScanController.java", "error-handling"),
			status: "REUSED",
			resultId: "r-2",
			sourceReviewId: "0b0b0b0b-1111-4222-8333-444455556666",
			outcome: "MET",
			severity: null,
			summary: null,
			problemCount: 0,
			currentness: "CURRENT",
			recordedAt: "2026-10-01T09:10:00Z",
		},
		{
			...entry("903", "webapp/src/main.tsx", "error-handling"),
			status: "WITHHELD",
			reason: "MODEL_UNAVAILABLE",
			reasonDescription: "MODEL_UNAVAILABLE",
		},
		{
			...entry("903", "webapp/src/main.tsx", "comment-quality"),
			status: "NOT_REVIEWED",
			reason: "SOMETHING_THE_PAGE_DOES_NOT_KNOW",
			reasonDescription: "SOMETHING_THE_PAGE_DOES_NOT_KNOW",
			lastResult: {
				id: "r-0",
				outcome: "MET",
				summary: "Comments say why the retry waits.",
				recordedAt: "2026-09-20T09:10:00Z",
			},
		},
		{
			...entry("905", "server/src/LegacyExporter.java", "comment-quality"),
			item: wireFile("905", "server/src/LegacyExporter.java", "2026-10-13T09:00:00Z"),
			status: "JUDGED",
			resultId: "r-5",
			sourceReviewId: SCAN_ID,
			outcome: "MET",
			severity: null,
			summary: null,
			problemCount: 0,
			currentness: "CURRENT",
			recordedAt: "2026-10-14T09:12:00Z",
		},
	],
	nextCursor: null,
	hasNext: false,
};

export function wireProblem(
	id: string,
	summary: string,
	baselineState: WireProblem["baselineState"],
	absentReason: "REPAIRED" | null = null,
): WireProblem {
	return {
		problem: {
			id,
			resultId: "r-1",
			position: 0,
			key: null,
			severity: "MAJOR",
			summary,
			explanation: null,
			nextStep: null,
			citations: [{ path: "server/src/ScanService.java", startLine: 42, endLine: 48 }],
		},
		resultId: "r-1",
		item: wireFile("901", "server/src/ScanService.java"),
		practiceSlug: "error-handling",
		practiceName: "Errors are handled where they occur",
		baselineState,
		previousProblemId: baselineState === "NEW" ? null : `prev-${id}`,
		absentReason,
	};
}

export const PROBLEMS: WireProblem[] = [
	wireProblem("p-1", "A failed upload is logged and then ignored.", "NEW"),
	wireProblem("p-2", "The retry swallows the timeout.", "UNCHANGED"),
	wireProblem("p-4", "The parser returns null on bad input.", "ABSENT"),
	wireProblem("p-5", "The upload ignores the server refusal.", "ABSENT", "REPAIRED"),
];

function definition(
	name: string,
	scanned: boolean,
	by: "HEPHAESTUS" | "PEOPLE",
): PracticeDefinition {
	return {
		name,
		about: "AUTHOR",
		appliesWhen: null,
		criteria: "Handle the error where it occurs.",
		guidance: {
			goingWell: null,
			guide: null,
			visual: null,
			whatGoodLooksLike: null,
			whyItMatters: null,
		},
		lineNotes: false,
		reads: { contract: "scm.file@1", sources: [] },
		reviewOn: { events: [], state: {} },
		reviewedBy: { by, humanReason: null, limits: [] },
		scan: scanned
			? {
					itemKind: "scm.file",
					include: ["**/*.java"],
					exclude: [],
					context: [],
					reads: { contract: "scm.file@1", sources: [] },
				}
			: null,
		script: null,
	};
}

export function wirePractice(
	slug: string,
	name: string,
	{ scanned = true, by = "HEPHAESTUS" }: { scanned?: boolean; by?: "HEPHAESTUS" | "PEOPLE" } = {},
): Wire<Practice> {
	const practiceDefinition = definition(name, scanned, by);
	return {
		id: `p-${slug}`,
		slug,
		kind: "scm.file",
		groupSlug: null,
		position: 0,
		currentVersion: {
			id: `v-${slug}`,
			number: 1,
			name,
			definition: practiceDefinition,
			definitionAvailable: true,
			historicalDefinition: {
				...practiceDefinition,
				about: "AUTHOR",
				appliesWhen: { anyOf: [], otherwise: "APPLIES" },
				knownFields: [],
				scan: {
					itemKind: "scm.file",
					include: [],
					exclude: [],
					context: [],
					reads: { contract: "scm.file@1", sources: [] },
				},
			},
			reviewDigest: `sha256:${slug}`,
			createdAt: "2026-10-01T09:00:00Z",
			createdBy: ADA,
			changed: [],
			reviewChanged: false,
		},
		feedbackMode: { effective: "OFF", override: "OFF", source: "WORKSPACE" },
		catalogOrigin: { catalogSlug: null, localEdits: [], offeredDigest: null, state: "YOURS" },
		createdAt: "2026-09-01T09:00:00Z",
		updatedAt: null,
	};
}

/** An admin of `acme`. Later `server.use` calls take precedence, so a test adds only its own. */
export function adminHandlers() {
	return [
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json(ownMembership("ADMIN")),
		),
		http.get("*/workspaces", () =>
			HttpResponse.json([workspaceListItem("acme", { practicesEnabled: true })]),
		),
	];
}

export function scanPage(scans: Wire<ScanSummary>[]) {
	return {
		content: scans,
		page: { number: 0, size: 20, totalElements: scans.length, totalPages: 1 },
	};
}

/** A read that fails on the server until `recover()`, and then answers with `body`. */
export function failingUntilRecovered(body: JsonBodyType) {
	let failing = true;
	return {
		recover: () => {
			failing = false;
		},
		respond: () =>
			failing
				? HttpResponse.json({ status: 500, title: "Internal Server Error" }, { status: 500 })
				: HttpResponse.json(body),
	};
}

/** Answers the first request with `first` and every later one with `rest`. */
export function firstThen(first: () => Response, rest: () => Response) {
	let answered = 0;
	return () => {
		answered += 1;
		return answered === 1 ? first() : rest();
	};
}

/** The table row an element sits in. */
export function rowOf(element: Element | undefined): HTMLElement {
	const row = element?.closest("tr");
	if (!row) {
		throw new Error("The element is not in a table row");
	}
	return row;
}

/** A scan in `status`. An active one is still listing files, so it knows no totals yet. */
export function wireScanIn(
	status: ScanSummary["status"],
	overrides: Partial<Wire<ScanSummary>> = {},
): Wire<ScanSummary> {
	return status === "QUEUED" || status === "RUNNING"
		? wireScan({ status, completedAt: null, counts: RUNNING_COUNTS, ...overrides })
		: wireScan({ status, ...overrides });
}
