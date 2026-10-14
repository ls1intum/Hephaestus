import deepEqual from "fast-deep-equal";

import type {
	AppliesWhen,
	PracticeDefinition,
	PracticeReadSource,
	PracticeReleaseField,
	PracticeVersion,
	ReviewedBy,
	ScanDefinition,
} from "@/api/types.gen";
import { aboutLabel } from "@/components/practice-vocabulary/about-role-labels";
import { hasText } from "@/lib/text";

/**
 * A practice's current definition. Only a historical version can lack one, so a current version
 * without it is a broken server contract, not a state to render.
 */
export function currentDefinition(practice: {
	slug: string;
	currentVersion: Pick<PracticeVersion, "definition">;
}): PracticeDefinition {
	const { definition } = practice.currentVersion;
	if (definition === null) {
		throw new Error(`The current version of practice ${practice.slug} has no definition.`);
	}
	return definition;
}

/** A part of a practice's definition that can change on its own. */
export type PracticeDefinitionField = PracticeReleaseField["field"];

/**
 * The name of each part of a practice's definition, and where it sits on the definition: one list
 * for a catalog release's review, a reset's comparison, and a practice's version history.
 */
export const PRACTICE_DEFINITION_FIELDS = {
	NAME: { label: "Name", key: "name" },
	REVIEW_ON: { label: "Review on", key: "reviewOn" },
	APPLIES_WHEN: { label: "Applies when", key: "appliesWhen" },
	ABOUT: { label: "About", key: "about" },
	READS: { label: "Reads", key: "reads" },
	REVIEWED_BY: { label: "Reviewed by", key: "reviewedBy" },
	CRITERIA: { label: "Review criteria", key: "criteria" },
	SCRIPT: { label: "Practice script", key: "script" },
	SCAN: { label: "Scans", key: "scan" },
	LINE_NOTES: { label: "Line notes", key: "lineNotes" },
	GUIDANCE: { label: "Guidance", key: "guidance" },
} satisfies Record<PracticeDefinitionField, { label: string; key: keyof PracticeDefinition }>;

const FIELD_ORDER = [
	"NAME",
	"REVIEW_ON",
	"APPLIES_WHEN",
	"ABOUT",
	"READS",
	"REVIEWED_BY",
	"CRITERIA",
	"SCRIPT",
	"SCAN",
	"LINE_NOTES",
	"GUIDANCE",
] as const satisfies readonly PracticeDefinitionField[];

/** The fields where `current` and `offered` differ, in definition order. */
export function definitionDifferences(
	current: PracticeDefinition,
	offered: PracticeDefinition,
): PracticeDefinitionField[] {
	return FIELD_ORDER.filter((field) => {
		const { key } = PRACTICE_DEFINITION_FIELDS[field];
		return !deepEqual(current[key], offered[key]);
	});
}

/** Who reviews the work, in the words the definition editor uses. */
export const REVIEWED_BY_LABELS: Record<ReviewedBy["by"], string> = {
	HEPHAESTUS: "Heph",
	PEOPLE: "People",
	NOBODY: "Nobody",
};

/** How much of a source a review needs, as a short phrase after the source's name. */
export const READ_NEED_LABELS: Record<PracticeReadSource["need"], string> = {
	REQUIRED: "required",
	COMPLETE: "required in full",
	OPTIONAL: "optional",
};

const NOBODY_SENTENCE = "This practice offers guidance without automated review.";

/** Who reviews the work and why, as the lines an admin reads. */
export function reviewedByLines(reviewedBy: ReviewedBy): string[] {
	const reason = reviewedBy.humanReason;
	return [
		REVIEWED_BY_LABELS[reviewedBy.by],
		...(reviewedBy.by === "NOBODY" ? [NOBODY_SENTENCE] : []),
		...(reason === null ? [] : [reason.description]),
		...reviewedBy.limits.map((limit) => limit.description),
	];
}

/** The conditions under which a practice applies, or that it always does. */
export function appliesWhenLines(appliesWhen: AppliesWhen | null): string[] {
	if (appliesWhen === null) {
		return ["Always"];
	}
	return [
		...appliesWhen.anyOf.flatMap((clause) => [
			...(clause.changedPath ? [`Changes a path matching ${clause.changedPath.join(", ")}`] : []),
			...(clause.diffContains ? [`The change contains ${clause.diffContains.join(", ")}`] : []),
			...(hasText(clause.hasItems) ? [`Has ${clause.hasItems}`] : []),
		]),
		`Otherwise: ${appliesWhen.otherwise}`,
	];
}

/** Which files a scan reviews, or that the practice is not part of scans. */
export function scanLines(scan: ScanDefinition | null): string[] {
	if (scan === null) {
		return ["Not included in scans"];
	}
	return [
		`Include: ${scan.include.join(", ")}`,
		...(scan.exclude.length > 0 ? [`Exclude: ${scan.exclude.join(", ")}`] : []),
		...(scan.context.length > 0 ? [`Context: ${scan.context.join(", ")}`] : []),
	];
}

/**
 * One field of a definition as plain text, for comparing two versions side by side. The visual and
 * the guide are pictures and Markdown, so `PracticeDefinitionFieldValue` draws those itself.
 */
export function definitionFieldText(
	definition: PracticeDefinition,
	field: PracticeDefinitionField,
): string {
	switch (field) {
		case "NAME": {
			return definition.name;
		}
		case "CRITERIA": {
			return definition.criteria;
		}
		case "SCRIPT": {
			return definition.script ?? "Built-in";
		}
		case "ABOUT": {
			return aboutLabel(definition.about);
		}
		case "LINE_NOTES": {
			return definition.lineNotes ? "Leaves line notes" : "Summary only";
		}
		case "REVIEWED_BY": {
			return reviewedByLines(definition.reviewedBy).join("\n");
		}
		case "APPLIES_WHEN": {
			return appliesWhenLines(definition.appliesWhen).join("\n");
		}
		case "SCAN": {
			return scanLines(definition.scan).join("\n");
		}
		case "READS": {
			return definition.reads.sources
				.map(({ source, need }) => `${source} (${READ_NEED_LABELS[need]})`)
				.join("\n");
		}
		case "REVIEW_ON": {
			return [
				...definition.reviewOn.events,
				...Object.entries(definition.reviewOn.state).map(
					([dimension, values]) => `${dimension}: ${values.join(", ")}`,
				),
			].join("\n");
		}
		case "GUIDANCE": {
			const { whyItMatters, whatGoodLooksLike, goingWell } = definition.guidance;
			return [
				`Why it matters: ${whyItMatters ?? "Not set"}`,
				`What good looks like: ${whatGoodLooksLike ?? "Not set"}`,
				`Going well: ${goingWell ?? "Not set"}`,
			].join("\n");
		}
	}
}
