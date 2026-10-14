import type { PracticeDefinition } from "@/api/types.gen";

/** A role a practice's result is about, or null when the practice is about the work itself. */
export type AboutRole = NonNullable<PracticeDefinition["about"]>;

/**
 * Who a practice's result is about, in words. A plain map rather than a `StatusDefs` registry: the
 * role is a setting a practice was given, not a state anything is in, so it has no badge to draw.
 * Keyed on the wire union, so a role the server adds fails the build here rather than reaching an
 * admin as a constant name.
 */
export const ABOUT_ROLE_LABELS: Record<AboutRole, string> = {
	AUTHOR: "Author",
	ASSIGNEE: "Assignee",
	REVIEWER: "Reviewer",
	MERGER: "Whoever merged it",
};

/** The About value as a sentence fragment: a role, or the work itself when it has none. */
export function aboutLabel(about: AboutRole | null): string {
	return about === null ? "The work" : ABOUT_ROLE_LABELS[about];
}
