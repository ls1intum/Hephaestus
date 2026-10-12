import type { ReviewSubject } from "@/api/types.gen";
import { firstNonBlank } from "@/lib/text";

/**
 * Formatters over values that are not enums. A label map, badge variant or icon for an enum value
 * does not belong here: it goes in that enum's defs module under `@/components/practice-vocabulary`,
 * so the filter dropdown, the row badge and the detail header all read one entry.
 */
export function subjectLabel(subject: ReviewSubject | undefined): string {
	if (!subject) {
		return "Unavailable developer";
	}
	return firstNonBlank(subject.name, subject.login) ?? `#${subject.id}`;
}

/**
 * A stored revision without its scheme marker. The server keeps revisions as `sha~<commit>`,
 * `dig~<digest>` and so on (`RevisionScheme`), so a ledger row explains its own identity; a reader
 * wants the commit or digest itself.
 */
export function revisionLabel(revision: string): string {
	return revision.replace(/^[a-z]+~/u, "");
}
