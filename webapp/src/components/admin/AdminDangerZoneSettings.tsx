import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useState } from "react";
import { toast } from "sonner";
import {
	getCurrentUserMembershipOptions,
	listWorkspacesQueryKey,
	purgeWorkspaceMutation,
} from "@/api/@tanstack/react-query.gen";
import type { ListWorkspacesResponse } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { problemDetailOf } from "@/lib/problem-detail";
import { DeleteWorkspaceAlertDialog } from "./DeleteWorkspaceAlertDialog";

export interface AdminDangerZoneSettingsProps {
	workspaceSlug: string;
}

export function AdminDangerZoneSettings({ workspaceSlug }: AdminDangerZoneSettingsProps) {
	const queryClient = useQueryClient();
	const [dialogOpen, setDialogOpen] = useState(false);

	// Keyed to the slug this section purges, not use-workspace-access's active workspace.
	// The server gates DELETE on OWNER regardless; this only keeps admins away from a 403.
	const {
		data: membership,
		isPending: isRolePending,
		isError: isRoleError,
	} = useQuery(getCurrentUserMembershipOptions({ path: { workspaceSlug } }));
	const isOwner = membership?.role === "OWNER";
	// Unknown role (pending or failed) must not read as "not owner" — that guess is wrong for owners.
	const unavailableReason =
		isOwner || isRolePending
			? undefined
			: isRoleError
				? "Couldn't confirm your role in this workspace. Reload to try again."
				: "Only the workspace owner can delete this workspace.";

	// Mutation-level, not mutate-level: a focus refetch can redirect and unmount this section
	// mid-DELETE, and mutate-level callbacks are dropped on unmount.
	const purgeWorkspace = useMutation({
		...purgeWorkspaceMutation(),
		onSuccess: async () => {
			setDialogOpen(false);
			toast.success("Workspace deleted", { description: "All workspace data has been purged." });
			// setQueryData is what navigates: use-active-workspace redirects away from a slug that
			// leaves this list. Written in rather than awaited, so a failed refetch cannot strand the
			// user on a dead workspace. The refetch still runs: it reconciles the rest of the list,
			// and covers the case where the list had not loaded yet and there was nothing to filter.
			queryClient.setQueryData(listWorkspacesQueryKey(), (workspaces?: ListWorkspacesResponse) =>
				workspaces?.filter((workspace) => workspace.workspaceSlug !== workspaceSlug),
			);
			await queryClient.invalidateQueries({ queryKey: listWorkspacesQueryKey() });
		},
		// No close on failure: the workspace still exists, so the dialog stays for a retry.
		onError: (e) => toast.error("Failed to delete workspace", { description: problemDetailOf(e) }),
	});

	return (
		<section aria-labelledby="workspace-danger-zone-heading">
			<h2 id="workspace-danger-zone-heading" className="text-lg font-semibold mb-4">
				Danger Zone
			</h2>
			<Card className="border-destructive/50">
				<CardContent>
					<div className="flex items-start justify-between gap-6">
						<div className="space-y-2 flex-1">
							<h3 className="text-base font-medium">Delete this workspace</h3>
							<p className="text-sm text-muted-foreground leading-relaxed">
								Permanently erases everything Hephaestus collected for this workspace. This cannot
								be undone.
							</p>
							<p className="text-sm text-muted-foreground leading-relaxed">
								Hephaestus has no workspace-level export. The{" "}
								<Link to="/settings" className="underline underline-offset-4 hover:text-foreground">
									personal export in account settings
								</Link>{" "}
								covers your own account only.
							</p>
							{unavailableReason && (
								<p id="delete-workspace-unavailable" className="text-sm text-muted-foreground">
									{unavailableReason}
								</p>
							)}
						</div>
						{/* aria-disabled, not disabled: a disabled button leaves the tab order, so a keyboard
						    user never reaches it and never hears why it is unavailable. */}
						<Button
							variant="destructive"
							className="mt-1 shrink-0 aria-disabled:pointer-events-none aria-disabled:opacity-50"
							aria-disabled={!isOwner}
							aria-describedby={unavailableReason ? "delete-workspace-unavailable" : undefined}
							onClick={() => isOwner && setDialogOpen(true)}
						>
							Delete workspace
						</Button>
					</div>
				</CardContent>
			</Card>

			<DeleteWorkspaceAlertDialog
				open={dialogOpen}
				onOpenChange={setDialogOpen}
				workspaceSlug={workspaceSlug}
				isDeleting={purgeWorkspace.isPending}
				onConfirm={() => purgeWorkspace.mutate({ path: { workspaceSlug } })}
			/>
		</section>
	);
}
