import { createFileRoute, redirect } from "@tanstack/react-router";
import {
	practiceFormLevel,
	practiceSetupSearchSchema,
} from "@/components/admin/practices/practice-search";
import { detailStackKey } from "@/components/core/detail-drawer/detail-stack";

/**
 * The editor is a level of the practice-setup drawer stack, not a page, so the tree a practice
 * belongs to stays on screen while it is written. This path predates that and is kept because it was
 * linked and bookmarked.
 */
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/new")({
	validateSearch: practiceSetupSearchSchema,
	beforeLoad: ({ params, search }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices",
			params,
			search: { ...search, detail: [detailStackKey(practiceFormLevel())] },
		});
	},
});
