import { createFileRoute, redirect } from "@tanstack/react-router";
import {
	curatedAreaLevel,
	curatedCatalogSearchSchema,
} from "@/components/admin/curated-catalog/curated-catalog-search";
import { detailStackKey } from "@/components/core/detail-drawer/detail-stack";

/**
 * Instance-catalog editors are levels of the catalog's drawer stack, not pages, so the catalog stays
 * on screen while an entry is written. This path predates that and is kept because it was linked and
 * bookmarked.
 */
export const Route = createFileRoute("/_authenticated/admin/catalog/areas/new")({
	validateSearch: curatedCatalogSearchSchema,
	beforeLoad: ({ search }) => {
		throw redirect({
			to: "/admin/catalog",
			search: { ...search, detail: [detailStackKey(curatedAreaLevel())] },
		});
	},
});
