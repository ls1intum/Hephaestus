import { Bot, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";
import type { WorkspaceLlmModel } from "@/api/types.gen";
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
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { priceLabel } from "@/lib/llmPricing";

export interface WorkspaceLlmModelsTableProps {
	models: WorkspaceLlmModel[];
	mutatingId: number | null;
	onEdit: (model: WorkspaceLlmModel) => void;
	onDelete: (model: WorkspaceLlmModel) => void;
}

/** Models on the workspace's own connected provider (#1368) — price framing uses workspace wording. */
export function WorkspaceLlmModelsTable({
	models,
	mutatingId,
	onEdit,
	onDelete,
}: WorkspaceLlmModelsTableProps) {
	const [deleting, setDeleting] = useState<WorkspaceLlmModel | null>(null);
	const isDeletePending = deleting != null && mutatingId === deleting.id;

	if (models.length === 0) {
		return (
			<Empty className="border border-dashed">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Bot />
					</EmptyMedia>
					<EmptyTitle>No models yet</EmptyTitle>
				</EmptyHeader>
			</Empty>
		);
	}

	return (
		<>
			<Table containerClassName="rounded-md border">
				<TableHeader>
					<TableRow>
						<TableHead>Model</TableHead>
						<TableHead>Price</TableHead>
						<TableHead>Active</TableHead>
						<TableHead className="text-right">Actions</TableHead>
					</TableRow>
				</TableHeader>
				<TableBody>
					{models.map((model) => {
						const busy = mutatingId === model.id;
						return (
							<TableRow key={model.id}>
								<TableCell>
									<div className="font-medium">{model.displayName}</div>
								</TableCell>
								<TableCell>{priceLabel(model, "workspace")}</TableCell>
								<TableCell>
									<Badge variant={model.enabled ? "default" : "secondary"}>
										{model.enabled ? "Active" : "Off"}
									</Badge>
								</TableCell>
								<TableCell className="text-right">
									<div className="flex justify-end gap-1">
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
							Any agent bound to this model will stop working until it's rebound. This cannot be
							undone.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={isDeletePending}>Cancel</AlertDialogCancel>
						<AlertDialogAction
							variant="destructive"
							disabled={isDeletePending}
							onClick={() => {
								if (deleting) onDelete(deleting);
								setDeleting(null);
							}}
						>
							{isDeletePending ? "Deleting…" : "Delete"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</>
	);
}
