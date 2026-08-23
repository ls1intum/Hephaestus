import { createFileRoute, redirect } from "@tanstack/react-router";
import {
	curatedAreaLevel,
	curatedCatalogSearchSchema,
} from "@/components/admin/curated-catalog/curated-catalog-search";
import { detailStackKey } from "@/components/core/detail-drawer/detail-stack";

/** Kept so a bookmarked link lands on the catalog with that group's editor level open. */
export const Route = createFileRoute("/_authenticated/admin/catalog/areas/$areaSlug")({
	validateSearch: curatedCatalogSearchSchema,
	beforeLoad: ({ params, search }) => {
		throw redirect({
			to: "/admin/catalog",
			search: { ...search, detail: [detailStackKey(curatedAreaLevel(params.areaSlug))] },
		});
	},
});
