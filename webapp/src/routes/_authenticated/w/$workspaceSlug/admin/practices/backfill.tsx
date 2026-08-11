import { createFileRoute, redirect } from "@tanstack/react-router";

/**
 * Retired. "Review past work" is now the *Past work* section of the one Review page — minus the
 * recurring check, which moved to *When and where* because a standing policy about recent work is a
 * trigger, not a campaign over history.
 */
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/backfill")({
	beforeLoad: ({ params }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices/review",
			params,
			search: { section: "past-work" },
		});
	},
});
