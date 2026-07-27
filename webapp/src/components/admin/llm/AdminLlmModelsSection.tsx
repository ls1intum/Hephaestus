import { Bot, Pencil, Plus, ShieldCheck, Trash2 } from "lucide-react";
import { useState } from "react";
import type { LlmModel } from "@/api/types.gen";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
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
	TableCaption,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { priceFieldsOf, priceLabel } from "@/lib/llm-pricing";
import type { WorkspaceOption } from "./workspace-options";

export interface AdminLlmModelsSectionProps {
	connectionDisplayName: string;
	connectionEnabled: boolean;
	workspaceOptions: WorkspaceOption[];
	models: LlmModel[];
	/** Ids of the models with a write in flight — see {@link usePendingMutationIds}. */
	mutatingIds: ReadonlySet<number>;
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

function shareLabel(model: LlmModel, workspaces: WorkspaceOption[]): string {
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

/** Models under one instance connection, including an explicit workspace-access action. */
export function AdminLlmModelsSection({
	connectionDisplayName,
	connectionEnabled,
	workspaceOptions,
	models,
	mutatingIds,
	onAdd,
	onEdit,
	onManageAccess,
	onDelete,
}: AdminLlmModelsSectionProps) {
	const [deleting, setDeleting] = useState<LlmModel | null>(null);

	return (
		<div className="space-y-3">
			<div className="flex items-center justify-between">
				{/* One level below the page's `h1`. `CardTitle` and this table's own caption are `<div>`
				    and `<caption>`, so nothing between them contributes to the outline and an `h3` would
				    skip a level (WCAG SC 1.3.1). Level is independent of the `text-sm` sizing. */}
				<h2 className="text-sm font-medium">Models on {connectionDisplayName}</h2>
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
					<TableCaption className="sr-only">Models on {connectionDisplayName}</TableCaption>
					<TableHeader>
						<TableRow>
							<TableHead scope="col">Model</TableHead>
							<TableHead scope="col">Price</TableHead>
							<TableHead scope="col">Workspace access</TableHead>
							<TableHead scope="col">Status</TableHead>
							<TableHead scope="col" className="text-right">
								Actions
							</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{models.map((model) => {
							const busy = mutatingIds.has(model.id);
							const status = readinessLabel(model, connectionEnabled);
							return (
								<TableRow key={model.id}>
									<TableCell className="font-medium">{model.displayName}</TableCell>
									{/* Left-aligned: `priceLabel` is a sentence, not a figure. `tabular-nums` only
									    lines the digits inside it up down the column. */}
									<TableCell className="tabular-nums">
										{priceLabel(priceFieldsOf(model), "instance")}
									</TableCell>
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

			<ConfirmDialog
				subject={deleting}
				onClose={() => setDeleting(null)}
				title={(model) => `Delete “${model.displayName}”?`}
				description="A model still bound to a workspace's agent can't be deleted. This cannot be undone."
				confirmLabel="Delete"
				onConfirm={onDelete}
			/>
		</div>
	);
}
