import type { ItemRef, ScanSummary } from "@/api/types.gen";
import { accountName } from "@/components/admin/users/account-labels";
import { REVIEW_ORIGIN_DEFS } from "@/components/practice-vocabulary/review-origin-defs";
import {
	isActiveReview,
	REVIEW_STOP_REASON_SENTENCES,
	type ReviewStopReason,
} from "@/components/practice-vocabulary/review-status-defs";

import { NOT_REVIEWED_SENTENCE } from "@/components/practice-vocabulary/scan-entry-defs";
import { itemName } from "@/lib/item-kinds";
import { hasText } from "@/lib/text";

/** How often an active scan is read again. A terminal scan is not polled. */
export const SCAN_POLL_MS = 5000;

export const isActiveScan = (status: ScanSummary["status"]) => isActiveReview(status);

/** Who started a scan: the person, or, when none is recorded, what started it. */
export function scanStartedBy(scan: Pick<ScanSummary, "requestedBy" | "origin">): string {
	return scan.requestedBy === null
		? REVIEW_ORIGIN_DEFS[scan.origin].label
		: accountName(scan.requestedBy);
}

/**
 * A repository by its full name. The column, title or field it sits in already says "Repository",
 * so the noun `itemName` leads with would be said twice.
 */
export function repositoryName(repository: Pick<ItemRef, "kind" | "provider" | "label">): string {
	return hasText(repository.label) ? repository.label : itemName(repository);
}

/** A commit-like version is shortened to the seven characters a reader compares by eye. */
export function shortVersion(version: string): string {
	return /^[0-9a-f]{12,}$/iu.test(version) ? version.slice(0, 7) : version;
}

/** "3" when the total is not known yet, "3 of 12" once the scan has listed every file. */
export function ofTotal(count: number, total: number | null): string {
	return total === null ? String(count) : `${count} of ${total}`;
}

const isStopReason = (code: string): code is ReviewStopReason =>
	Object.hasOwn(REVIEW_STOP_REASON_SENTENCES, code);

const WITHHELD_FALLBACK = "Withheld for a recorded reason that this page cannot describe yet.";

/**
 * The sentence for a reason the server recorded as a code. The server's own description of these
 * reasons is the code again, so it is never shown: a code this page has no words for reads as the
 * plain fallback instead.
 */
export function knownReasonSentence(code: string | null): string | undefined {
	return code !== null && isStopReason(code) ? REVIEW_STOP_REASON_SENTENCES[code] : undefined;
}

export function withheldSentence(code: string | null): string {
	return knownReasonSentence(code) ?? WITHHELD_FALLBACK;
}

/** Why a file was not reviewed: the scan ended first, and, where it is known, why it ended. */
export function notReviewedSentence(code: string | null): string {
	const known = knownReasonSentence(code);
	return known === undefined ? NOT_REVIEWED_SENTENCE : `${NOT_REVIEWED_SENTENCE} ${known}`;
}
