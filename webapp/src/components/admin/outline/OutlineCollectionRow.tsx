import { formatDistanceToNow } from "date-fns";
import {
	CheckIcon,
	MoreHorizontalIcon,
	PauseIcon,
	PlayIcon,
	Trash2Icon,
	TriangleAlertIcon,
} from "lucide-react";
import type { OutlineCollection } from "@/api/types.gen";
import { OutlineCollectionIcon } from "@/components/admin/outline/OutlineCollectionIcon";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Spinner } from "@/components/ui/spinner";
import { TableCell, TableRow } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

/** The mirror lifecycle states, sourced from the generated DTO so they never drift. */
export type OutlineMirrorState = OutlineCollection["state"];

export interface OutlineCollectionRowProps {
	collection: OutlineCollection;
	/** Pause an ENABLED collection (reversible, no confirm — documents are kept). */
	onPause: (collection: OutlineCollection) => void;
	/** Resume a PAUSED collection (reversible, no confirm). */
	onResume: (collection: OutlineCollection) => void;
	/** Open the remove & erase confirmation dialog. */
	onRemove: (collection: OutlineCollection) => void;
}

/**
 * One mirrored Outline collection: name with its Outline color/icon, mirror-state badge,
 * sync progress, document count, last clean sync and a state-gated row action menu.
 * Pure — every transition is delegated upward.
 */
export function OutlineCollectionRow({
	collection,
	onPause,
	onResume,
	onRemove,
}: OutlineCollectionRowProps) {
	const label = collection.name ?? collection.collectionId;
	const paused = collection.state === "PAUSED";

	return (
		<TableRow>
			<TableCell>
				<div className="flex items-center gap-2">
					<OutlineCollectionIcon icon={collection.icon} color={collection.color} />
					<span className="font-medium">{label}</span>
				</div>
				<div className="text-muted-foreground font-mono text-xs">
					{collection.urlId ?? collection.collectionId}
				</div>
			</TableCell>

			<TableCell>
				{/* Word + icon for every state (never color-only) so the status survives WCAG 1.4.1. */}
				{paused ? (
					<Badge variant="outline" className="gap-1">
						<PauseIcon className="size-3 text-muted-foreground" aria-hidden />
						Paused
					</Badge>
				) : (
					<Badge variant="outline" className="gap-1">
						<CheckIcon className="size-3 text-green-600 dark:text-green-400" aria-hidden />
						Mirroring
					</Badge>
				)}
			</TableCell>

			<TableCell>
				<div className="flex items-center gap-2">
					{collection.syncStatus === "PENDING" ? (
						<Badge variant="secondary" className="gap-1">
							{/* The visible "Syncing…" text carries the meaning; hide the spinner from AT so the
							row doesn't double-announce a generic "Loading" live region per collection. */}
							<Spinner className="size-3" role="presentation" aria-hidden />
							Syncing…
						</Badge>
					) : (
						<Badge variant="outline" className="gap-1">
							<CheckIcon className="size-3 text-muted-foreground" aria-hidden />
							Up to date
						</Badge>
					)}
					{collection.lastSyncError && (
						<Tooltip>
							{/* Default TooltipTrigger renders a real <button>, so the error detail is reachable
							by keyboard and screen-reader users — an icon-only <span> would have no focus stop. */}
							<TooltipTrigger className="text-destructive" aria-label={`Sync error for ${label}`}>
								<TriangleAlertIcon className="size-4" aria-hidden />
							</TooltipTrigger>
							<TooltipContent className="max-w-xs break-words">
								{collection.lastSyncError}
							</TooltipContent>
						</Tooltip>
					)}
				</div>
			</TableCell>

			<TableCell className="tabular-nums">
				<div className="flex items-center gap-1.5">
					<span>
						{collection.documentCount}
						{typeof collection.documentsUpstream === "number" && (
							<span className="text-muted-foreground"> / {collection.documentsUpstream}</span>
						)}
					</span>
					{!!collection.exportsSkippedForBudget && (
						<Tooltip>
							{/* Amber, not destructive — this is expected budget throttling, not an error; the
							next reconcile catches these up. */}
							<TooltipTrigger
								className="text-amber-600 dark:text-amber-400"
								aria-label={`${collection.exportsSkippedForBudget} exports skipped for budget for ${label}`}
							>
								<TriangleAlertIcon className="size-3.5" aria-hidden />
							</TooltipTrigger>
							<TooltipContent className="max-w-xs break-words">
								{collection.exportsSkippedForBudget} export
								{collection.exportsSkippedForBudget === 1 ? "" : "s"} skipped for the shared budget
								in the last pass — will catch up on the next reconcile.
							</TooltipContent>
						</Tooltip>
					)}
				</div>
			</TableCell>

			<TableCell className="text-muted-foreground text-sm">
				{collection.lastSyncedAt ? (
					formatDistanceToNow(new Date(collection.lastSyncedAt), { addSuffix: true })
				) : (
					<span aria-hidden>—</span>
				)}
			</TableCell>

			<TableCell className="text-right">
				<DropdownMenu>
					<DropdownMenuTrigger
						render={
							<Button variant="ghost" size="icon-sm" aria-label={`Actions for ${label}`}>
								<MoreHorizontalIcon className="size-4" />
							</Button>
						}
					/>
					<DropdownMenuContent align="end">
						{paused ? (
							<DropdownMenuItem onClick={() => onResume(collection)}>
								<PlayIcon className="size-4" />
								Resume
							</DropdownMenuItem>
						) : (
							<DropdownMenuItem onClick={() => onPause(collection)}>
								<PauseIcon className="size-4" />
								Pause
							</DropdownMenuItem>
						)}
						<DropdownMenuSeparator />
						<DropdownMenuItem variant="destructive" onClick={() => onRemove(collection)}>
							<Trash2Icon className="size-4" />
							Remove &amp; erase…
						</DropdownMenuItem>
					</DropdownMenuContent>
				</DropdownMenu>
			</TableCell>
		</TableRow>
	);
}
