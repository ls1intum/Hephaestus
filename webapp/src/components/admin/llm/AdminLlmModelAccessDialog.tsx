import { AlertTriangle } from "lucide-react";
import { useEffect, useState } from "react";
import type { LlmModel, UpdateLlmModelSharingRequest } from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { WorkspaceMultiSelect, type WorkspaceMultiSelectOption } from "./WorkspaceMultiSelect";

export interface AdminLlmModelAccessDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	model: LlmModel | null;
	workspaceOptions: WorkspaceMultiSelectOption[];
	isLoadingWorkspaces?: boolean;
	isWorkspaceError?: boolean;
	onRetryWorkspaces?: () => void;
	isSubmitting: boolean;
	onSave: (body: UpdateLlmModelSharingRequest) => void;
}

/** Dedicated access editor for an instance model. Access changes take effect at request time. */
export function AdminLlmModelAccessDialog({
	open,
	onOpenChange,
	model,
	workspaceOptions,
	isLoadingWorkspaces = false,
	isWorkspaceError = false,
	onRetryWorkspaces,
	isSubmitting,
	onSave,
}: AdminLlmModelAccessDialogProps) {
	const [scope, setScope] = useState<"ALL" | "SELECTED">("SELECTED");
	const [workspaceIds, setWorkspaceIds] = useState<number[]>([]);

	useEffect(() => {
		if (!open || !model) return;
		setScope(model.visibility === "PUBLIC" ? "ALL" : "SELECTED");
		setWorkspaceIds(model.grantedWorkspaceIds);
	}, [open, model]);

	if (!model) return null;

	const removesCurrentWorkspace =
		scope === "SELECTED" &&
		(model.visibility === "PUBLIC"
			? workspaceOptions.some((workspace) => !workspaceIds.includes(workspace.id))
			: model.grantedWorkspaceIds.some((id) => !workspaceIds.includes(id)));
	const restrictsFutureWorkspaces = model.visibility === "PUBLIC" && scope === "SELECTED";
	const noWorkspaceHasAccess = scope === "SELECTED" && workspaceIds.length === 0;

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-lg">
				<DialogHeader>
					<DialogTitle>Manage access to {model.displayName}</DialogTitle>
					<DialogDescription>
						Choose which workspaces can discover and use this model. Provider credentials remain
						hidden from workspace admins.
					</DialogDescription>
				</DialogHeader>

				<RadioGroup
					value={scope}
					onValueChange={(value) => setScope(value as "ALL" | "SELECTED")}
					className="gap-3"
				>
					<label
						htmlFor="llm-model-access-all"
						className="flex cursor-pointer items-start gap-3 rounded-lg border p-3"
					>
						<RadioGroupItem id="llm-model-access-all" value="ALL" />
						<span>
							<span className="block text-sm font-medium">All workspaces</span>
							<span className="text-muted-foreground block text-xs">
								Every current and future workspace can select the model.
							</span>
						</span>
					</label>
					<label
						htmlFor="llm-model-access-selected"
						className="flex cursor-pointer items-start gap-3 rounded-lg border p-3"
					>
						<RadioGroupItem id="llm-model-access-selected" value="SELECTED" />
						<span className="min-w-0 flex-1">
							<span className="block text-sm font-medium">Selected workspaces</span>
							<span className="text-muted-foreground block text-xs">
								Only explicitly selected workspaces can select the model.
							</span>
						</span>
					</label>
				</RadioGroup>

				{scope === "SELECTED" && (
					<div className="space-y-2">
						<p className="text-sm font-medium">Workspaces</p>
						{isWorkspaceError ? (
							<Alert variant="destructive">
								<AlertTitle>Could not load workspaces</AlertTitle>
								<AlertDescription>
									Do not save until the workspace list is available.
									{onRetryWorkspaces && (
										<Button
											size="sm"
											variant="outline"
											className="mt-2"
											onClick={onRetryWorkspaces}
										>
											Retry
										</Button>
									)}
								</AlertDescription>
							</Alert>
						) : (
							<WorkspaceMultiSelect
								id="llm-model-access-workspaces"
								options={workspaceOptions}
								selectedIds={workspaceIds}
								onChange={setWorkspaceIds}
								disabled={isLoadingWorkspaces || isSubmitting}
							/>
						)}
						{isLoadingWorkspaces && (
							<p className="text-muted-foreground text-xs">Loading workspaces…</p>
						)}
					</div>
				)}

				{noWorkspaceHasAccess && !isWorkspaceError && (
					<Alert>
						<AlertTitle>No workspace will be able to use this model</AlertTitle>
						<AlertDescription>
							This is useful while staging a model. Grant access when it is ready.
						</AlertDescription>
					</Alert>
				)}

				{removesCurrentWorkspace && (
					<Alert variant="warning">
						<AlertTriangle aria-hidden />
						<AlertTitle>Access is reduced immediately</AlertTitle>
						<AlertDescription>
							Existing configurations in removed workspaces will stop running until an available
							model is selected.
						</AlertDescription>
					</Alert>
				)}
				{restrictsFutureWorkspaces && !removesCurrentWorkspace && (
					<Alert>
						<AlertTitle>Future workspaces will need an explicit grant</AlertTitle>
						<AlertDescription>
							Every current workspace remains selected, so existing configurations keep running.
						</AlertDescription>
					</Alert>
				)}

				<DialogFooter>
					<Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
						Cancel
					</Button>
					<Button
						type="button"
						disabled={
							isSubmitting || (scope === "SELECTED" && (isLoadingWorkspaces || isWorkspaceError))
						}
						onClick={() =>
							onSave(
								scope === "ALL"
									? { visibility: "PUBLIC" }
									: { visibility: "GRANTED", workspaceIds },
							)
						}
					>
						{isSubmitting ? "Saving…" : "Save access"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
