import { createFileRoute, Outlet, retainSearchParams } from "@tanstack/react-router";

import {
	CATALOG_SEARCH_PARAMS,
	catalogSearchSchema,
} from "@/components/admin/catalog/catalog-search";

export const Route = createFileRoute("/_authenticated/admin/catalog")({
	validateSearch: catalogSearchSchema,
	search: { middlewares: [retainSearchParams(CATALOG_SEARCH_PARAMS)] },
	component: Outlet,
});
