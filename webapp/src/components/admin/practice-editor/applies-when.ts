import { z } from "zod";

import type { AppliesWhen, AppliesWhenClause, PracticeItemTypeOption } from "@/api/types.gen";
import { hasText } from "@/lib/text";

import { pathPatternProblem } from "./scan-settings";

export const MAX_APPLIES_WHEN_CLAUSES = 10;
export const MAX_OTHERWISE_LENGTH = 300;

const term = z
	.string()
	.max(200)
	.refine((value) => value.trim().length > 0);

const clause = z
	.strictObject({
		changedPath: z
			.array(term.refine((value) => pathPatternProblem(value) === undefined))
			.min(1)
			.max(100)
			.optional(),
		diffContains: z.array(term).min(1).max(100).optional(),
		hasItems: z.string().trim().min(1).max(4096).optional(),
	})
	.refine(
		(value) =>
			[value.changedPath, value.diffContains, value.hasItems].filter((item) => item !== undefined)
				.length === 1,
	);

const clauses = z.array(clause).min(1).max(MAX_APPLIES_WHEN_CLAUSES);

type ApplicabilityClause = PracticeItemTypeOption["applicabilityClauses"][number];

function clauseKind(item: AppliesWhenClause): ApplicabilityClause {
	if (item.changedPath !== undefined) {
		return "CHANGED_PATH";
	}
	return item.diffContains === undefined ? "HAS_ITEMS" : "DIFF_CONTAINS";
}

/** The conditions as the author edits them: the JSON list of clauses, or blank for none. */
export function appliesWhenText(appliesWhen: AppliesWhen | null): string {
	return appliesWhen === null ? "" : JSON.stringify(appliesWhen.anyOf, null, 2);
}

export interface AppliesWhenDraft {
	/** The `anyOf` list as JSON. Blank means the practice applies to every item. */
	clauses: string;
	otherwise: string;
}

export function appliesWhenDraftOf(appliesWhen: AppliesWhen | null): AppliesWhenDraft {
	return { clauses: appliesWhenText(appliesWhen), otherwise: appliesWhen?.otherwise ?? "" };
}

export interface ParsedAppliesWhen {
	value: AppliesWhen | null;
	clausesError?: string;
	otherwiseError?: string;
}

/**
 * The draft as the definition saves it, with the reason it cannot be saved. A clause the item type
 * cannot check is refused here rather than by the server, so the author sees it beside the field.
 */
export function parseAppliesWhen(
	draft: AppliesWhenDraft,
	itemType: PracticeItemTypeOption | undefined,
): ParsedAppliesWhen {
	if (draft.clauses.trim() === "") {
		return { value: null };
	}
	let parsed: AppliesWhenClause[];
	try {
		const result = clauses.safeParse(JSON.parse(draft.clauses));
		if (!result.success) {
			return {
				value: null,
				clausesError: `Write a list of 1 to ${MAX_APPLIES_WHEN_CLAUSES} conditions, each with one of changedPath, diffContains or hasItems.`,
			};
		}
		parsed = result.data;
	} catch {
		return { value: null, clausesError: "Enter the conditions as valid JSON." };
	}
	if (
		itemType &&
		parsed.some((item) => !itemType.applicabilityClauses.includes(clauseKind(item)))
	) {
		return { value: null, clausesError: "This kind of item cannot check one of these conditions." };
	}
	const otherwise = draft.otherwise.trim();
	if (!hasText(otherwise) || otherwise.length > MAX_OTHERWISE_LENGTH) {
		return {
			value: null,
			otherwiseError: `Say in at most ${MAX_OTHERWISE_LENGTH} characters why the practice does not apply otherwise.`,
		};
	}
	return { value: { anyOf: parsed, otherwise } };
}

/** What the conditions field offers for this item type, and an example where it has one. */
export function appliesWhenPresentation(itemType: PracticeItemTypeOption | undefined) {
	const kinds = itemType?.applicabilityClauses ?? [];
	return {
		supported: kinds.length > 0,
		description:
			kinds.length === 0
				? "This kind of item cannot check conditions. Clear the conditions to continue."
				: "Leave empty to review every item. A review only runs when one of these conditions holds, whatever started it.",
		example: kinds.includes("CHANGED_PATH")
			? JSON.stringify([{ changedPath: ["**/*.swift"] }])
			: undefined,
	};
}
