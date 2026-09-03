import { useNavigate, useParams } from "@tanstack/react-router";
import { toast } from "sonner";

export function useWorkspaceSwitcher() {
	const navigate = useNavigate();
	const params = useParams({ strict: false });
	const currentWorkspaceSlug =
		"workspaceSlug" in params && typeof params.workspaceSlug === "string"
			? params.workspaceSlug
			: undefined;
	const portable =
		currentWorkspaceSlug !== undefined &&
		Object.keys(params).every((parameter) => parameter === "workspaceSlug");

	return async (workspace: { displayName: string; workspaceSlug: string }) => {
		const { displayName, workspaceSlug } = workspace;
		if (workspaceSlug === currentWorkspaceSlug) return;

		if (portable) {
			await navigate({
				to: ".",
				params: (previous) => ({ ...previous, workspaceSlug }),
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
					"This page is specific to the previous workspace, so we opened the new workspace's home page.",
			});
		}
	};
}
