import type { ReviewedBy, ReviewLimit } from "@/api/types.gen";
import { hasText } from "@/lib/text";

import type { ReviewedByKind } from "./review-settings";

/**
 * Who reviews a practice, in words. A plain map rather than a `StatusDefs` registry: Reviewed by is a
 * setting a practice was given, not a state anything is in. Keyed on the wire union, so a value the
 * server adds fails the build here.
 */
export const REVIEWED_BY_LABELS: Record<ReviewedByKind, string> = {
	HEPHAESTUS: "Hephaestus",
	PEOPLE: "People",
	NOBODY: "Nobody",
};

export const REVIEWED_BY_DESCRIPTIONS: Record<ReviewedByKind, string> = {
	HEPHAESTUS:
		"An automated review reads the work and records a result. When a source it must read cannot be captured, the review is withheld rather than guessed.",
	PEOPLE:
		"A developer, peer or mentor reviews this practice. No automated review runs, and the practice still offers its guidance.",
	NOBODY: "This practice offers guidance without automated review.",
};

export const MAX_REASON_LENGTH = 500;

const LIMIT_CODE = /^[A-Z][A-Z0-9_]{2,63}$/u;

// Codes must be deterministic because the server digests them with the reason text.
export function limitCodeFor(description: string): string {
	const slug = description
		.toUpperCase()
		.replaceAll(/[^A-Z0-9]+/gu, "_")
		.replaceAll(/^_+|_+$/gu, "")
		.slice(0, 63);
	return LIMIT_CODE.test(slug) ? slug : `LIMIT_${fnv1a(description)}`;
}

function fnv1a(input: string) {
	let hash = 0x81_1c_9d_c5;
	for (let index = 0; index < input.length; index += 1) {
		// oxlint-disable-next-line unicorn/prefer-code-point -- The code is stored, so the hash stays over UTF-16 units; code points would change it for an astral character.
		const unit = input.charCodeAt(index);
		// oxlint-disable-next-line no-bitwise -- FNV-1a is defined over 32-bit integer operations.
		hash = Math.imul(hash ^ unit, 0x01_00_01_93) >>> 0;
	}
	return hash.toString(16).toUpperCase().padStart(8, "0");
}

export function limitWithText(description: string): ReviewLimit {
	return { code: limitCodeFor(description), description };
}

/** Keep reasons and limits across draft choices; discard incompatible fields only on save. */
export interface ReviewedByDraft {
	by: ReviewedByKind;
	humanReason: ReviewLimit;
	limits: ReviewLimit[];
}

export function reviewedByDraftOf(reviewedBy: ReviewedBy): ReviewedByDraft {
	return {
		by: reviewedBy.by,
		humanReason: reviewedBy.humanReason ?? limitWithText(""),
		limits: reviewedBy.limits,
	};
}

export function reviewedByOf(draft: ReviewedByDraft): ReviewedBy {
	return {
		by: draft.by,
		humanReason: draft.by === "PEOPLE" ? trimmed(draft.humanReason) : null,
		limits: draft.by === "HEPHAESTUS" ? draft.limits.map(trimmed) : [],
	};
}

function trimmed(limit: ReviewLimit): ReviewLimit {
	return { code: limit.code, description: limit.description.trim() };
}

export const HUMAN_REASON_FIELD_ID = "practice-human-reason";
export const REVIEWED_BY_FIELD_ID = "practice-reviewed-by";

export function limitFieldId(index: number): string {
	return `practice-limit-description-${index}`;
}

export interface ReviewedByProblem {
	message: string;
	focusId: string;
}

export function reviewedByProblem(draft: ReviewedByDraft): ReviewedByProblem | undefined {
	if (draft.by === "PEOPLE") {
		const reason = draft.humanReason.description;
		if (!hasText(reason.trim()) || reason.length > MAX_REASON_LENGTH) {
			return {
				message: "Say why people review this practice, in at most 500 characters.",
				focusId: HUMAN_REASON_FIELD_ID,
			};
		}
	}
	if (draft.by !== "HEPHAESTUS") {
		return undefined;
	}
	const invalid = draft.limits.findIndex(
		(limit) =>
			!LIMIT_CODE.test(limit.code) ||
			!hasText(limit.description.trim()) ||
			limit.description.length > MAX_REASON_LENGTH,
	);
	if (invalid !== -1) {
		return {
			message: "Each limit needs a description of 1–500 characters.",
			focusId: limitFieldId(invalid),
		};
	}
	return new Set(draft.limits.map((limit) => limit.code)).size === draft.limits.length
		? undefined
		: { message: "Describe each limit once.", focusId: REVIEWED_BY_FIELD_ID };
}
