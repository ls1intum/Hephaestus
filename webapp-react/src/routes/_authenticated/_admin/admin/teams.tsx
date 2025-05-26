import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/_admin/admin/teams")({
	component: AdminTeamsContainer,
});

function AdminTeamsContainer() {
	return <div>Hello "/_authenticated/admin/teams"!</div>;
}
