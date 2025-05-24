import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/_admin/admin/settings")({
	component: RouteComponent,
});

function RouteComponent() {
	return <div>Hello "/_authenticated/admin/settings"! Workspace settings</div>;
}
