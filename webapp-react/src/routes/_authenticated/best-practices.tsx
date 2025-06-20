import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/best-practices")({
	loader: ({ context }) => {
		if (context.auth?.username) {
			redirect({
				to: "/user/$username/best-practices",
				throw: true,
				params: { username: context.auth.username },
			});
		}
	},
});
