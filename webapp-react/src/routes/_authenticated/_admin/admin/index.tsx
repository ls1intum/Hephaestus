import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/_admin/admin/")({
	loader: () => {
		redirect({ to: "/admin/members", throw: true });
	},
});
