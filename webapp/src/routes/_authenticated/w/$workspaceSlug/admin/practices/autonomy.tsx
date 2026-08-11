import { createFileRoute, redirect } from "@tanstack/react-router";
import { z } from "zod";

/**
 * Retired. "Review autonomy" is now the *How much* section of the one Review page.
 *
 * <p>A redirect rather than a deletion: this URL has been in the sidebar, in the admin docs and in
 * people's bookmarks, and the cost of keeping it resolving is one file. The overrides filter travels
 * with it — a link to "show me only what was set by hand" is the one deep link into this screen
 * anybody had a reason to save.
 */
const autonomySearchSchema = z.object({
	overrides: z.boolean().optional().catch(undefined),
});

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/autonomy")({
	validateSearch: autonomySearchSchema,
	beforeLoad: ({ params, search }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices/review",
			params,
			// `how-much` is the default section, so it is left out of the address rather than spelled
			// into it: the redirect and the sidebar's own link land on the same URL.
			search: { overrides: search.overrides },
		});
	},
});
