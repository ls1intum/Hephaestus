import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/findings/$findingId",
)({
	beforeLoad: ({ params }) => {
		const { workspaceSlug, findingId } = params;
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices/reviews/observations/$observationId",
			params: { workspaceSlug, observationId: findingId },
			search: true,
			replace: true,
		});
	},
});
