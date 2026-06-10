import { createFileRoute, Outlet } from "@tanstack/react-router";

// Layout shim: the practices area moved under /admin/ai/practice-detection/catalog.
// Child routes throw their own redirects in beforeLoad; this just renders the outlet.
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/practices")({
	component: Outlet,
});
