import { createFileRoute, redirect } from "@tanstack/react-router";

// Renamed: the practice-detection admin is now the domain-framed "Practices" section at
// /admin/practices (catalog / review settings / runs). This redirect keeps old links working.
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/ai/practice-detection",
)({
	beforeLoad: ({ params }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices",
			params: { workspaceSlug: params.workspaceSlug },
			search: (prev) => prev,
		});
	},
});
