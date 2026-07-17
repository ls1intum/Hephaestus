import { LibraryIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import type { OutlineCollection } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader } from "@/components/ui/card";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Table, TableBody, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { IntegrationCardHeading } from "../IntegrationCardHeading";
import { swallow } from "../swallow";
import { TableRowsSkeleton } from "../TableRowsSkeleton";
import { AddCollectionDialog } from "./AddCollectionDialog";
import { OutlineCollectionRow, type OutlineMirrorState } from "./OutlineCollectionRow";
import { RemoveCollectionAlertDialog } from "./RemoveCollectionAlertDialog";

export type { OutlineMirrorState };

/** Shared by the loading and loaded states so the header doesn't materialise on resolve. */
function CollectionsTableHeader() {
	return (
		<TableHeader>
			<TableRow>
				<TableHead>Collection</TableHead>
				<TableHead>State</TableHead>
				<TableHead>Sync</TableHead>
				<TableHead className="text-right">Documents</TableHead>
				<TableHead>Last synced</TableHead>
				<TableHead className="w-0 text-right">
					<span className="sr-only">Actions</span>
				</TableHead>
			</TableRow>
		</TableHeader>
	);
}

export interface OutlineCollectionsSectionProps {
	workspaceSlug: string;
	collections: OutlineCollection[];
	isLoading: boolean;
	/** The collection-list query's error, if it failed — surfaced with a retry instead of the empty state. */
	error?: unknown;
	/** Re-run the failed collection list query. */
	onRetry?: () => void;
	/** Register a collection for mirroring (lands ENABLED + PENDING). Resolves on success,
	 * rejects to keep the add dialog open. */
	onRegisterCollection: (input: { collectionId: string }) => Promise<void> | void;
	/** Drive pause / resume via the target mirror state. */
	onUpdateCollectionState: (input: {
		collectionId: string;
		state: OutlineMirrorState;
	}) => Promise<void> | void;
	/** Remove the collection and erase its mirrored documents. Resolves on success, rejects to
	 * keep the confirm dialog open. */
	onRemoveCollection: (input: { collectionId: string }) => Promise<void> | void;
}

/**
 * Admin surface for the mirrored-collections plane: which Outline collections are mirrored,
 * their sync health, and their lifecycle (add → pause ⇄ resume → remove + erase). Rendered only
 * while an Outline connection is active. Pure: all data + mutations live in the container.
 */
export function OutlineCollectionsSection({
	workspaceSlug,
	collections,
	isLoading,
	error,
	onRetry,
	onRegisterCollection,
	onUpdateCollectionState,
	onRemoveCollection,
}: OutlineCollectionsSectionProps) {
	const [addOpen, setAddOpen] = useState(false);
	const [removeCollection, setRemoveCollection] = useState<OutlineCollection | null>(null);

	const hasCollections = collections.length > 0;

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader>
					<IntegrationCardHeading>Mirrored collections</IntegrationCardHeading>
					<CardDescription>
						Documents in mirrored collections are kept in sync and reach practice detection as
						context. Pausing a collection <strong>freezes syncing but keeps its documents</strong>
						{"; "}removing it <strong>erases every mirrored document</strong> from Hephaestus.
					</CardDescription>
					<CardAction>
						<Button size="sm" onClick={() => setAddOpen(true)}>
							<PlusIcon className="size-4" />
							Add collection
						</Button>
					</CardAction>
				</CardHeader>

				<CardContent className="space-y-4">
					{isLoading ? (
						<Table>
							<CollectionsTableHeader />
							<TableRowsSkeleton
								columns={["w-36", "w-16", "w-14", "w-12", "w-24", null]}
								rows={3}
							/>
						</Table>
					) : error ? (
						<QueryErrorAlert
							error={error}
							title="We couldn't load the mirrored collections"
							onRetry={onRetry}
						/>
					) : hasCollections ? (
						<Table>
							<CollectionsTableHeader />
							<TableBody>
								{collections.map((collection) => (
									<OutlineCollectionRow
										key={collection.collectionId}
										collection={collection}
										onPause={(c) =>
											swallow(
												onUpdateCollectionState({
													collectionId: c.collectionId,
													state: "PAUSED",
												}),
											)
										}
										onResume={(c) =>
											swallow(
												onUpdateCollectionState({
													collectionId: c.collectionId,
													state: "ENABLED",
												}),
											)
										}
										onRemove={setRemoveCollection}
									/>
								))}
							</TableBody>
						</Table>
					) : (
						<Empty>
							<EmptyHeader>
								<EmptyMedia variant="icon">
									<LibraryIcon />
								</EmptyMedia>
								<EmptyTitle>No collections mirrored yet</EmptyTitle>
								<EmptyDescription>
									Pick the Outline collections whose documents should reach practice detection. Only
									what you select is read.
								</EmptyDescription>
							</EmptyHeader>
							<EmptyContent>
								<Button size="sm" onClick={() => setAddOpen(true)}>
									<PlusIcon className="size-4" />
									Add collection
								</Button>
							</EmptyContent>
						</Empty>
					)}
				</CardContent>
			</Card>

			<AddCollectionDialog
				workspaceSlug={workspaceSlug}
				open={addOpen}
				onOpenChange={setAddOpen}
				onRegister={onRegisterCollection}
			/>

			<RemoveCollectionAlertDialog
				collection={removeCollection}
				onOpenChange={(open) => {
					if (!open) setRemoveCollection(null);
				}}
				onConfirm={onRemoveCollection}
			/>
		</div>
	);
}
