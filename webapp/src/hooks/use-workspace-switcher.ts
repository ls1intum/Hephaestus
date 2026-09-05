import { useNavigate, useParams } from "@tanstack/react-router";
import { toast } from "sonner";

export function useWorkspaceSwitcher() {
	const navigate = useNavigate();
	const params = useParams({ strict: false });
	const currentWorkspaceSlug = params.workspaceSlug;
	// Any path param besides `workspaceSlug` names a row of the current workspace, which the new one
	// does not have.
	const portable =
		currentWorkspaceSlug !== undefined &&
		Object.keys(params).every((parameter) => parameter === "workspaceSlug");

	// `to: "."` resolves from the current location's deepest match, not from the route that rendered
	// the caller, so the switcher can live in the app chrome. The destination's search middleware runs
	// after this updater, so clearing the search leaves each route to declare which of its own options
	// are portable through `retainSearchParams`.
	return async (workspace: { displayName: string; workspaceSlug: string }) => {
		const { displayName, workspaceSlug } = workspace;
		if (workspaceSlug === currentWorkspaceSlug) return;

		if (portable) {
			await navigate({
				to: ".",
				params: { workspaceSlug },
				search: () => ({}),
				replace: true,
			});
			return;
		}

		await navigate({
			to: "/w/$workspaceSlug",
			params: { workspaceSlug },
			search: () => ({}),
			replace: true,
		});
		if (currentWorkspaceSlug !== undefined) {
			toast.info(`Switched to ${displayName}`, {
				description:
					"This page is specific to the previous workspace, so Hephaestus opened the new workspace's home page.",
			});
		}
	};
}
