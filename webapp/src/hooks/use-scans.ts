import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";

import {
	listPracticesOptions,
	listReviewItemsOptions,
	listReviewItemsQueryKey,
	listScansOptions,
	listScansQueryKey,
	startScanMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Practice, ScanSummary, StartScanRequest } from "@/api/types.gen";
import { isActiveScan, SCAN_POLL_MS } from "@/components/admin/practice-scans/scan-display";
import type { ScansListState } from "@/components/admin/practice-scans/ScansPage";
import type {
	ScanPracticeOption,
	StartScanDraft,
	StartScanOptions,
	StartScanRefusal,
} from "@/components/admin/practice-scans/StartScanForm";
import { isRecord } from "@/lib/is-record";
import { ITEM_KIND } from "@/lib/item-kinds";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";

export const SCANS_PAGE_SIZE = 20;
/** One page of repositories is the whole offer: a workspace scope holds far fewer. */
const REPOSITORY_PAGE_SIZE = 200;

/** The workspace's scans, one page at a time, read again while any scan on the page is active. */
export function useScanList(
	workspaceSlug: string,
	page: number,
	onPageChange: (page: number) => void,
): ScansListState {
	const query = useQuery({
		...listScansOptions({ path: { workspaceSlug }, query: { page, size: SCANS_PAGE_SIZE } }),
		placeholderData: keepPreviousData,
		refetchInterval: (current) =>
			(current.state.data?.content ?? []).some((scan) => isActiveScan(scan.status))
				? SCAN_POLL_MS
				: false,
	});
	if (query.data !== undefined) {
		return {
			status: "ready",
			scans: query.data.content ?? [],
			page,
			totalPages: query.data.page?.totalPages ?? 0,
			onPageChange,
		};
	}
	if (query.isError) {
		return {
			status: "error",
			error: query.error,
			onRetry: () => {
				void query.refetch();
			},
		};
	}
	return { status: "loading" };
}

/**
 * A practice a scan can review: its current definition says how to scan it, and Hephaestus, not
 * people or nobody, reviews it. A version whose definition was not recorded scans nothing.
 */
function scanPracticeOption(practice: Practice): ScanPracticeOption | undefined {
	const { definition } = practice.currentVersion;
	return definition !== null &&
		definition.scan !== null &&
		definition.reviewedBy.by === "HEPHAESTUS"
		? { slug: practice.slug, name: definition.name }
		: undefined;
}

function scanPracticeOptions(practices: readonly Practice[]): ScanPracticeOption[] {
	return practices.flatMap((practice) => scanPracticeOption(practice) ?? []);
}

/** A top-level string property of a refusal body, which the server writes beside RFC 9457's own. */
function refusalProperty(error: unknown, key: string): string | undefined {
	if (!isRecord(error)) {
		return undefined;
	}
	const value = error[key];
	return typeof value === "string" ? value : undefined;
}

/**
 * Whether the server answered, so a repeat of the same request cannot change the outcome. No answer
 * at all, or a server failure, may not have reached the point where the scan was recorded: only the
 * same Idempotency-Key can safely ask again.
 */
function isDefinitive(error: unknown): boolean {
	const status = problemStatusOf(error);
	return status !== undefined && status >= 400 && status < 500;
}

function refusalOf(error: unknown): StartScanRefusal {
	const activeScanId = refusalProperty(error, "activeScanId");
	if (activeScanId !== undefined) {
		return { kind: "active-scan", scanId: activeScanId };
	}
	const currentVersion = refusalProperty(error, "currentVersion");
	if (currentVersion !== undefined) {
		return { kind: "version-changed", currentVersion };
	}
	if (isDefinitive(error)) {
		return { kind: "refused", message: problemDetailOf(error) };
	}
	return { kind: "failed", message: problemDetailOf(error, "The server did not answer.") };
}

export interface StartScan {
	options: StartScanOptions;
	pending: boolean;
	refusal?: StartScanRefusal;
	expectedVersion?: string;
	start: (draft: StartScanDraft) => Promise<ScanSummary>;
}

/**
 * Starting one scan. Each attempt carries an Idempotency-Key, kept for an explicit retry of the
 * same draft after no answer or a server failure, so a retry joins the scan the first attempt may
 * have started rather than starting a second. A changed draft, or a definitive refusal, takes a new
 * key. A version conflict is shown and remembered for the next attempt; it is never retried here.
 */
export function useStartScan(
	workspaceSlug: string,
	onStarted: (scan: ScanSummary) => void,
): StartScan {
	const queryClient = useQueryClient();
	const repositoriesQuery = useQuery(
		listReviewItemsOptions({
			path: { workspaceSlug },
			query: { itemKind: ITEM_KIND.repository, size: REPOSITORY_PAGE_SIZE },
		}),
	);
	const practicesQuery = useQuery(listPracticesOptions({ path: { workspaceSlug } }));
	const attempt = useRef<{ key: string; body: string }>(undefined);
	const [refusal, setRefusal] = useState<StartScanRefusal>();
	const [expectedVersion, setExpectedVersion] = useState<string>();
	const mutation = useMutation({
		...startScanMutation(),
		onSuccess: (scan) => {
			attempt.current = undefined;
			void queryClient.invalidateQueries({
				queryKey: listScansQueryKey({ path: { workspaceSlug } }),
			});
			onStarted(scan);
		},
	});

	// A second press before the first answer joins the first request rather than sending another.
	const inFlight = useRef<Promise<ScanSummary>>(undefined);

	const send = async (draft: StartScanDraft) => {
		const body: StartScanRequest = {
			repositoryItemId: draft.repositoryId,
			ref: null,
			expectedVersion: expectedVersion ?? null,
			practiceSlugs: [...draft.practiceSlugs].sort(),
			budgetUsd: draft.budgetUsd,
			reuse: draft.reuse,
		};
		const fingerprint = JSON.stringify(body);
		if (attempt.current?.body !== fingerprint) {
			attempt.current = { key: crypto.randomUUID(), body: fingerprint };
		}
		const { key } = attempt.current;
		setRefusal(undefined);
		try {
			return await mutation.mutateAsync({
				path: { workspaceSlug },
				body,
				headers: { "Idempotency-Key": key },
			});
		} catch (error) {
			const next = refusalOf(error);
			if (next.kind !== "failed") {
				attempt.current = undefined;
			}
			if (next.kind === "version-changed") {
				setExpectedVersion(next.currentVersion);
				void queryClient.invalidateQueries({
					queryKey: listReviewItemsQueryKey({ path: { workspaceSlug } }),
				});
			}
			setRefusal(next);
			throw error;
		}
	};

	const start = async (draft: StartScanDraft) => {
		if (inFlight.current === undefined) {
			inFlight.current = send(draft).finally(() => {
				inFlight.current = undefined;
			});
		}
		return inFlight.current;
	};

	let options: StartScanOptions;
	if (repositoriesQuery.data !== undefined && practicesQuery.data !== undefined) {
		options = {
			status: "ready",
			repositories: repositoriesQuery.data.content ?? [],
			practices: scanPracticeOptions(practicesQuery.data),
		};
	} else if (repositoriesQuery.isError || practicesQuery.isError) {
		options = {
			status: "error",
			error: repositoriesQuery.error ?? practicesQuery.error,
			onRetry: () => {
				void repositoriesQuery.refetch();
				void practicesQuery.refetch();
			},
		};
	} else {
		options = { status: "loading" };
	}

	return { options, pending: mutation.isPending, refusal, expectedVersion, start };
}
