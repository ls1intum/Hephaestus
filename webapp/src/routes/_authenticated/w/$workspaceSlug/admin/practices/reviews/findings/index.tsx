import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/findings/",
)({
	beforeLoad: ({ params }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices/reviews/observations",
			params,
			search: true,
			replace: true,
		});
	},
});
