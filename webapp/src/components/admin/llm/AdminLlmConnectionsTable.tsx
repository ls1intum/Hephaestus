import { Pencil, Plug, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import type { LlmConnection } from "@/api/types.gen";
import { TableRowsSkeleton } from "@/components/admin/integrations/TableRowsSkeleton";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
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
import { PROVIDER_PRESET_LABELS, presetForConnection } from "@/lib/llm-provider-type";

export interface AdminLlmConnectionsTableProps {
	connections: LlmConnection[];
	/** Model count per connection id, computed by the container from the (unscoped) models list. */
	modelCounts: Record<number, number>;
	/** False while the model catalog is unavailable; destructive connection actions are blocked. */
	modelCountsAvailable?: boolean;
	isLoading: boolean;
	isError: boolean;
	error?: unknown;
	onRetry?: () => void;
	/**
	 * Ids of the rows with a write in flight, read from the mutation cache by the container. A set,
	 * not one id: two rows can be mid-write at once, and a single id re-enables the still-running row
	 * as soon as the other settles.
	 */
	mutatingIds: ReadonlySet<number>;
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

/**
 * One width per column, so the placeholder reserves the real column box instead of promising a
 * uniform grid. `null` for the trailing action slot: it has nothing to promise.
 */
const SKELETON_COLUMNS = ["w-40", "w-24", "w-6", "w-9", null];

/** Mounted by both the loading and the loaded table, so the skeleton lines up with the real columns. */
function ConnectionsTableHeader() {
	return (
		<TableHeader>
			<TableRow>
				<TableHead>Connection</TableHead>
				<TableHead>API</TableHead>
				<TableHead>Models</TableHead>
				<TableHead>Active</TableHead>
				<TableHead className="text-right">Actions</TableHead>
			</TableRow>
		</TableHeader>
	);
}

/** Instance-admin provider connections list (#1368) — never shows the API key, only "ends in ····last4". */
export function AdminLlmConnectionsTable({
	connections,
	modelCounts,
	modelCountsAvailable = true,
	isLoading,
	isError,
	error,
	onRetry,
	mutatingIds,
	selectedId,
	onSelect,
	onEdit,
	onToggleEnabled,
	onDelete,
	onAdd,
}: AdminLlmConnectionsTableProps) {
	const [deleting, setDeleting] = useState<LlmConnection | null>(null);
	const [turningOff, setTurningOff] = useState<LlmConnection | null>(null);
	const modelsOn = (connection: LlmConnection) => modelCounts[connection.id] ?? 0;

	if (isError) {
		return <QueryErrorAlert error={error} title="Could not load connections" onRetry={onRetry} />;
	}

	if (isLoading) {
		return (
			<Table containerClassName="rounded-md border">
				<ConnectionsTableHeader />
				<TableRowsSkeleton columns={SKELETON_COLUMNS} rows={3} />
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
				<ConnectionsTableHeader />
				<TableBody>
					{connections.map((connection) => {
						const busy = mutatingIds.has(connection.id);
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
								<TableCell className="tabular-nums">
									{modelCountsAvailable ? (modelCounts[connection.id] ?? 0) : "—"}
								</TableCell>
								<TableCell>
									<div className="flex items-center gap-2">
										<Switch
											checked={connection.enabled}
											disabled={busy || (connection.enabled && !modelCountsAvailable)}
											aria-busy={busy}
											aria-label={`${connection.enabled ? "Turn off" : "Turn on"} ${connection.displayName}`}
											onCheckedChange={(checked) => {
												if (checked) onToggleEnabled(connection, true);
												// A connection with no models on it stops nothing, so there is nothing to
												// confirm. The switch is already disabled while the count is unknown, so
												// zero here means zero. Turning it back on is one click away.
												else if (modelsOn(connection) === 0) onToggleEnabled(connection, false);
												else setTurningOff(connection);
											}}
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

			<ConfirmDialog
				subject={deleting}
				onClose={() => setDeleting(null)}
				title={(connection) => `Delete “${connection.displayName}”?`}
				description="A connection with models still on it can't be deleted. Delete its models first. This cannot be undone."
				confirmLabel="Delete"
				onConfirm={onDelete}
			/>

			<ConfirmDialog
				subject={turningOff}
				onClose={() => setTurningOff(null)}
				title={(connection) => `Turn off “${connection.displayName}”?`}
				description={(connection) => (
					<>
						{/* The confirm is only reached with at least one model on the connection, so the
						    sentence never has to describe nothing — but it does have to count: "all 1
						    models" is not English. */}
						This immediately stops requests through{" "}
						{modelsOn(connection) === 1 ? "the model" : `all ${modelsOn(connection)} models`} on
						this connection. Practice detection and Mentor can't run on them until you turn the
						connection back on, or until each workspace picks another model.
					</>
				)}
				confirmLabel="Turn off connection"
				cancelLabel="Keep active"
				onConfirm={(connection) => onToggleEnabled(connection, false)}
			/>
		</>
	);
}
