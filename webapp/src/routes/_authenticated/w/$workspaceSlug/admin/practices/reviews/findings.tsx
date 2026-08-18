import { createFileRoute, Outlet } from "@tanstack/react-router";
import { observationsSearchSchema } from "@/components/admin/practice-reviews/review-search";

/**
 * The former spelling of Observations, kept only to redirect.
 *
 * The screens, the wire contract and the URL all said "findings" for a concept the product calls an
 * observation. The URL was the last of the three to change, and a bookmark or a link in a chat
 * thread is the one copy of it nobody can be asked to update — so both leaves under here forward to
 * the new path, carrying their filters with them.
 *
 * <p>The search schema is repeated here rather than inherited, because a redirect that preserves
 * search has to parse it first: without this, a link to a filtered list would arrive unfiltered.
 */
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/findings",
)({
	validateSearch: observationsSearchSchema,
	component: Outlet,
});
