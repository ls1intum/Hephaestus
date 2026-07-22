import { Pencil, Plug, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import type { LlmConnection } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
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
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { PROVIDER_PRESET_LABELS, presetForConnection } from "@/lib/llmProviderType";

export interface AdminLlmConnectionsTableProps {
	connections: LlmConnection[];
	/** Model count per connection id, computed by the container from the (unscoped) models list. */
	modelCounts: Record<number, number>;
	isLoading: boolean;
	isError: boolean;
	error?: unknown;
	onRetry?: () => void;
	/** Id of the row with a mutation in flight. */
	mutatingId: number | null;
	selectedId: number | null;
	onSelect: (connection: LlmConnection) => void;
	onEdit: (connection: LlmConnection) => void;
	onToggleEnabled: (connection: LlmConnection, enabled: boolean) => void;
	onDelete: (connection: LlmConnection) => void;
	onAdd?: () => void;
}

function hostOf(baseUrl: string): string {
	try {
		return new URL(baseUrl).host;
	} catch {
		return baseUrl;
	}
}

const SKELETON_ROWS = ["a", "b", "c"];

/** Instance-admin provider connections list (#1368) — never shows the API key, only "ends in ····last4". */
export function AdminLlmConnectionsTable({
	connections,
	modelCounts,
	isLoading,
	isError,
	error,
	onRetry,
	mutatingId,
	selectedId,
	onSelect,
	onEdit,
	onToggleEnabled,
	onDelete,
	onAdd,
}: AdminLlmConnectionsTableProps) {
	const [deleting, setDeleting] = useState<LlmConnection | null>(null);
	const isDeletePending = deleting != null && mutatingId === deleting.id;

	if (isError) {
		return <QueryErrorAlert error={error} title="Could not load connections" onRetry={onRetry} />;
	}

	if (isLoading) {
		return (
			<Table containerClassName="rounded-md border">
				<TableHeader>
					<TableRow>
						<TableHead>Connection</TableHead>
						<TableHead>API</TableHead>
						<TableHead>Models</TableHead>
						<TableHead>Active</TableHead>
						<TableHead className="text-right">Actions</TableHead>
					</TableRow>
				</TableHeader>
				<TableBody>
					{SKELETON_ROWS.map((id) => (
						<TableRow key={`loading-${id}`}>
							<TableCell colSpan={5}>
								<Skeleton className="h-6 w-full" />
							</TableCell>
						</TableRow>
					))}
				</TableBody>
			</Table>
		);
	}

	if (connections.length === 0) {
		return (
			<Empty className="border">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Plug aria-hidden />
					</EmptyMedia>
					<EmptyTitle>No connections yet</EmptyTitle>
					<EmptyDescription>Add a provider connection to start sharing models.</EmptyDescription>
				</EmptyHeader>
				{onAdd && (
					<EmptyContent>
						<Button onClick={onAdd}>
							<Plus className="size-4" aria-hidden />
							Add connection
						</Button>
					</EmptyContent>
				)}
			</Empty>
		);
	}

	return (
		<>
			<Table containerClassName="rounded-md border">
				<TableHeader>
					<TableRow>
						<TableHead>Connection</TableHead>
						<TableHead>API</TableHead>
						<TableHead>Models</TableHead>
						<TableHead>Active</TableHead>
						<TableHead className="text-right">Actions</TableHead>
					</TableRow>
				</TableHeader>
				<TableBody>
					{connections.map((connection) => {
						const busy = mutatingId === connection.id;
						return (
							<TableRow
								key={connection.id}
								data-state={selectedId === connection.id ? "selected" : undefined}
							>
								<TableCell>
									<div className="font-medium">{connection.displayName}</div>
									<div className="text-xs text-muted-foreground">{hostOf(connection.baseUrl)}</div>
								</TableCell>
								<TableCell>
									<Badge variant="secondary">
										{PROVIDER_PRESET_LABELS[presetForConnection(connection)]}
									</Badge>
								</TableCell>
								<TableCell className="tabular-nums">{modelCounts[connection.id] ?? 0}</TableCell>
								<TableCell>
									<div className="flex items-center gap-2">
										<Switch
											checked={connection.enabled}
											disabled={busy}
											aria-busy={busy}
											aria-label={`${connection.enabled ? "Turn off" : "Turn on"} ${connection.displayName}`}
											onCheckedChange={(checked) => onToggleEnabled(connection, checked)}
										/>
										{busy && <Spinner className="size-3.5 text-muted-foreground" />}
									</div>
								</TableCell>
								<TableCell className="text-right">
									<div className="flex justify-end gap-1">
										<Button
											type="button"
											variant="outline"
											size="sm"
											disabled={busy}
											aria-label={`Manage models for ${connection.displayName}`}
											onClick={() => onSelect(connection)}
										>
											Manage models
										</Button>
										<Button
											type="button"
											variant="ghost"
											size="icon"
											aria-label={`Edit ${connection.displayName}`}
											disabled={busy}
											onClick={() => onEdit(connection)}
										>
											<Pencil className="size-4" aria-hidden />
										</Button>
										<Button
											type="button"
											variant="ghost"
											size="icon"
											aria-label={`Delete ${connection.displayName}`}
											disabled={busy}
											onClick={() => setDeleting(connection)}
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
							A connection with models still on it can't be deleted — delete its models first. This
							cannot be undone.
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
		</>
	);
}
