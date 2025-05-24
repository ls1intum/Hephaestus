import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/_admin/admin/")({
	component: RouteComponent,
});

function RouteComponent() {
	return (
		<div>Hello "/_authenticated/admin"! Placeholder for "Manage Members"</div>
	);
}

// Invite People button
// Invite links

// Workspace Owner
// Workspace Admin
// Regular Member
// Invited

// Transfer Ownership

// Date joined
