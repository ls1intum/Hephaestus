import { createFileRoute, redirect } from "@tanstack/react-router";

// Back-compat redirect: this surface lives at /admin/practices (catalog / review settings / runs).
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/ai/practice-detection",
)({
	beforeLoad: ({ params }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices",
			params: { workspaceSlug: params.workspaceSlug },
			search: (prev) => prev,
		});
	},
});
