import { createFileRoute, redirect } from "@tanstack/react-router";

// Back-compat redirect: instance AI models live at /admin/models, matching the workspace twin.
export const Route = createFileRoute("/_authenticated/admin/llm")({
	beforeLoad: () => {
		throw redirect({ to: "/admin/models", search: (prev) => prev });
	},
});
