import { createFileRoute, Outlet } from "@tanstack/react-router";

import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/scans")({
	head: workspaceAdminHead("Scans"),
	component: Outlet,
});
