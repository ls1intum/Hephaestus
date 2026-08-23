import { createFileRoute, redirect } from "@tanstack/react-router";

/** Kept so a bookmarked link lands on Practice setup with that practice's drawer open. */
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/available/$catalogSlug",
)({
	beforeLoad: ({ params: { workspaceSlug, catalogSlug } }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices",
			params: { workspaceSlug },
			search: { library: true, detail: [`catalog-practice:${catalogSlug}`] },
		});
	},
});
