import { Pencil, Trash2 } from "lucide-react";
import { useState } from "react";
import type { AgentConfig } from "@/api/types.gen";
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
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { type ConfigDesignation, LLM_PROVIDER_LABELS } from "./utils";

interface AgentConfigCardProps {
	config: AgentConfig;
	/** How this config is wired into workspace AI features. */
	designation?: ConfigDesignation;
	/** Whether this card's config is the one currently open in the form. */
	selected?: boolean;
	isDeleting?: boolean;
	onEdit: (config: AgentConfig) => void;
	onDelete: (config: AgentConfig) => void;
}

export function AgentConfigCard({
	config,
	designation,
	selected = false,
	isDeleting = false,
	onEdit,
	onDelete,
}: AgentConfigCardProps) {
	const [confirmOpen, setConfirmOpen] = useState(false);

	return (
		<Card className={selected ? "border-primary" : config.enabled ? "" : "opacity-70"}>
			<CardHeader>
				<div className="flex items-start justify-between gap-3">
					<div className="min-w-0 flex-1">
						<div className="flex items-center gap-2">
							<CardTitle className="text-base">{config.name}</CardTitle>
						</div>
						<p className="mt-0.5 text-xs text-muted-foreground">
							{LLM_PROVIDER_LABELS[config.llmProvider]}
							{config.modelName ? ` · ${config.modelName}` : ""}
						</p>
					</div>
					<div className="flex shrink-0 items-center gap-2">
						<Badge variant={config.enabled ? "default" : "secondary"} className="text-xs">
							{config.enabled ? "Enabled" : "Disabled"}
						</Badge>
						<Button
							variant="ghost"
							size="icon-sm"
							onClick={() => onEdit(config)}
							aria-label={`Edit ${config.name}`}
						>
							<Pencil className="h-4 w-4" />
						</Button>
						<Button
							variant="ghost"
							size="icon-sm"
							onClick={() => setConfirmOpen(true)}
							aria-label={`Delete ${config.name}`}
						>
							<Trash2 className="h-4 w-4" />
						</Button>
					</div>
				</div>
			</CardHeader>

			{designation && (
				<CardContent>
					<div className="flex flex-wrap gap-1.5">
						{(designation === "practice" || designation === "both") && (
							<Badge variant="secondary" className="text-xs">
								Powers practice detection
							</Badge>
						)}
						{(designation === "mentor" || designation === "both") && (
							<Badge variant="secondary" className="text-xs">
								Powers mentor
							</Badge>
						)}
					</div>
				</CardContent>
			)}

			<AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Delete &ldquo;{config.name}&rdquo;?</AlertDialogTitle>
						<AlertDialogDescription>
							This permanently removes this model. A model bound to practice detection or the mentor
							can't be deleted until you unbind it.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
						<AlertDialogAction
							variant="destructive"
							disabled={isDeleting}
							onClick={() => {
								onDelete(config);
								setConfirmOpen(false);
							}}
						>
							{isDeleting ? "Deleting…" : "Delete model"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</Card>
	);
}
