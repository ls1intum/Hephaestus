import type { ReviewSubject } from "@/api/types.gen";

/**
 * What is left of this module once every enum's words moved to `@/components/practice-vocabulary`:
 * two formatters over values that are not enums at all.
 *
 * <p>Nothing else belongs here. A label map, a badge variant or an icon for an enum value goes in
 * that enum's defs module, where the filter dropdown, the table badge and the detail header all read
 * the same entry — this file having held six of them is why those three disagreed.
 */
export function confidenceLabel(confidence: number | undefined): string {
	if (confidence == null) return "—";
	return `${Math.round(confidence * 100)}%`;
}

export function subjectLabel(subject: ReviewSubject | undefined): string {
	if (!subject) return "Unavailable developer";
	return subject.name || subject.login || `#${subject.id}`;
}
