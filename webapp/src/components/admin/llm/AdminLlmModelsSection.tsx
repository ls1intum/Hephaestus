import { Bot, Pencil, Plus, ShieldCheck, Trash2 } from "lucide-react";
import { useState } from "react";
import type { LlmModel } from "@/api/types.gen";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { priceFieldsOf, priceLabel } from "@/lib/llmPricing";
import type { WorkspaceMultiSelectOption } from "./WorkspaceMultiSelect";

export interface AdminLlmModelsSectionProps {
	connectionDisplayName: string;
	connectionEnabled: boolean;
	workspaceOptions: WorkspaceMultiSelectOption[];
	models: LlmModel[];
	mutatingId: number | null;
	onAdd: () => void;
	onEdit: (model: LlmModel) => void;
	onManageAccess: (model: LlmModel) => void;
	onDelete: (model: LlmModel) => void;
}

function readinessLabel(model: LlmModel, connectionEnabled: boolean): string {
	if (!model.currentPrice || model.currentPrice.pricingMode === "UNPRICED") return "Price missing";
	if (!connectionEnabled) return "Connection off";
	if (!model.enabled) return "Model off";
	if (model.visibility === "GRANTED" && model.grantedWorkspaceIds.length === 0) {
		return "No workspace access";
	}
	return "Ready";
}

function shareLabel(model: LlmModel, workspaces: WorkspaceMultiSelectOption[]): string {
	if (model.visibility === "PUBLIC") return "All workspaces";
	if (model.grantedWorkspaceIds.length === 0) return "No workspaces";
	const firstName = workspaces.find(
		(workspace) => workspace.id === model.grantedWorkspaceIds[0],
	)?.displayName;
	if (!firstName) return `${model.grantedWorkspaceIds.length} workspaces`;
	return model.grantedWorkspaceIds.length === 1
		? firstName
		: `${firstName} + ${model.grantedWorkspaceIds.length - 1} more`;
}

/** Models under one instance connection (#1368), including an explicit workspace-access action. */
export function AdminLlmModelsSection({
	connectionDisplayName,
	connectionEnabled,
	workspaceOptions,
	models,
	mutatingId,
	onAdd,
	onEdit,
	onManageAccess,
	onDelete,
}: AdminLlmModelsSectionProps) {
	const [deleting, setDeleting] = useState<LlmModel | null>(null);
	const isDeletePending = deleting != null && mutatingId === deleting.id;

	return (
		<div className="space-y-3">
			<div className="flex items-center justify-between">
				<h3 className="text-sm font-medium">Models on {connectionDisplayName}</h3>
				<Button size="sm" variant="outline" onClick={onAdd}>
					<Plus className="size-4" aria-hidden />
					Add model
				</Button>
			</div>

			{models.length === 0 ? (
				<Empty className="border border-dashed">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<Bot aria-hidden />
						</EmptyMedia>
						<EmptyTitle>No models yet</EmptyTitle>
						<EmptyDescription>Add a model so workspaces can pick it.</EmptyDescription>
					</EmptyHeader>
					<EmptyContent>
						<Button size="sm" onClick={onAdd}>
							<Plus className="size-4" aria-hidden />
							Add model
						</Button>
					</EmptyContent>
				</Empty>
			) : (
				<Table containerClassName="rounded-md border">
					<TableHeader>
						<TableRow>
							<TableHead>Model</TableHead>
							<TableHead>Price</TableHead>
							<TableHead>Workspace access</TableHead>
							<TableHead>Status</TableHead>
							<TableHead className="text-right">Actions</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{models.map((model) => {
							const busy = mutatingId === model.id;
							const status = readinessLabel(model, connectionEnabled);
							return (
								<TableRow key={model.id}>
									<TableCell className="font-medium">{model.displayName}</TableCell>
									<TableCell>{priceLabel(priceFieldsOf(model), "instance")}</TableCell>
									<TableCell>{shareLabel(model, workspaceOptions)}</TableCell>
									<TableCell>
										<Badge variant={status === "Ready" ? "default" : "secondary"}>{status}</Badge>
									</TableCell>
									<TableCell className="text-right">
										<div className="flex justify-end gap-1">
											<Button
												type="button"
												variant="outline"
												size="sm"
												aria-label={`Manage access for ${model.displayName}`}
												disabled={busy}
												onClick={() => onManageAccess(model)}
											>
												<ShieldCheck className="size-4" aria-hidden />
												Access
											</Button>
											<Button
												type="button"
												variant="ghost"
												size="icon"
												aria-label={`Edit ${model.displayName}`}
												disabled={busy}
												onClick={() => onEdit(model)}
											>
												<Pencil className="size-4" aria-hidden />
											</Button>
											<Button
												type="button"
												variant="ghost"
												size="icon"
												aria-label={`Delete ${model.displayName}`}
												disabled={busy}
												onClick={() => setDeleting(model)}
											>
												<Trash2 className="size-4 text-destructive" aria-hidden />
											</Button>
										</div>
									</TableCell>
								</TableRow>
							);
						})}
					</TableBody>
				</Table>
			)}

			<AlertDialog
				open={deleting != null}
				onOpenChange={(open) => {
					if (!open && !isDeletePending) setDeleting(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Delete “{deleting?.displayName}”?</AlertDialogTitle>
						<AlertDialogDescription>
							A model still bound to a workspace's agent can't be deleted. This cannot be undone.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={isDeletePending}>Cancel</AlertDialogCancel>
						<AlertDialogAction
							variant="destructive"
							disabled={isDeletePending}
							onClick={() => deleting && onDelete(deleting)}
						>
							{isDeletePending ? "Deleting…" : "Delete"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
}
