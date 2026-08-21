import { createFileRoute, redirect } from "@tanstack/react-router";

/**
 * Retired as a page. Reviewing a catalog practice now opens over Practice setup, so an existing
 * link lands on the library with that practice's drawer already open.
 */
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/available/$catalogSlug",
)({
	beforeLoad: ({ params: { workspaceSlug, catalogSlug } }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices",
			params: { workspaceSlug },
			search: { library: true, detail: [`practice:${catalogSlug}`] },
		});
	},
});
