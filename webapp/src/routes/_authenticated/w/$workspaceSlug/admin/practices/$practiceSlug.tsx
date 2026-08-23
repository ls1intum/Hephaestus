import { createFileRoute, redirect } from "@tanstack/react-router";
import {
	practiceFormLevel,
	practiceSetupSearchSchema,
} from "@/components/admin/practices/practice-search";
import { detailStackKey } from "@/components/core/detail-drawer/detail-stack";

/**
 * See `new.tsx`: editing is a drawer level. This path is kept because it was linked and bookmarked,
 * and because a review or an autonomy row still points a reader at one practice by slug.
 */
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/$practiceSlug",
)({
	validateSearch: practiceSetupSearchSchema,
	beforeLoad: ({ params, search }) => {
		const { practiceSlug, ...rest } = params;
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices",
			params: rest,
			search: { ...search, detail: [detailStackKey(practiceFormLevel(practiceSlug))] },
		});
	},
});
