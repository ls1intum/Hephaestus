import { createFileRoute, redirect } from "@tanstack/react-router";

// Moved to /admin/ai/practice-detection/catalog/$practiceSlug.
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/practices/$practiceSlug",
)({
	beforeLoad: ({ params }) => {
		throw redirect({
			to: "/w/$workspaceSlug/admin/ai/practice-detection/catalog/$practiceSlug",
			params: {
				workspaceSlug: params.workspaceSlug,
				practiceSlug: params.practiceSlug,
			},
			search: (prev) => prev,
		});
	},
});
