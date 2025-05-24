import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/_admin/admin/teams")({
	component: RouteComponent,
});

function RouteComponent() {
	return <div>Hello "/_authenticated/admin/teams"!</div>;
}
