import { createFileRoute, Outlet, retainSearchParams } from "@tanstack/react-router";
import {
	CURATED_CATALOG_SEARCH_PARAMS,
	curatedCatalogSearchSchema,
} from "@/components/admin/curated-catalog/curated-catalog-search";

export const Route = createFileRoute("/_authenticated/admin/catalog")({
	validateSearch: curatedCatalogSearchSchema,
	search: { middlewares: [retainSearchParams(CURATED_CATALOG_SEARCH_PARAMS)] },
	component: Outlet,
});
