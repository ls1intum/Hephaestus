import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/_admin")({
	loader: ({ context }) => {
		if (!context.auth?.hasRole("admin")) {
			redirect({ to: "/", throw: true });
		}
	},
});
