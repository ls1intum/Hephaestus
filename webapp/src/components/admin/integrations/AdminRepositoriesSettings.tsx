import { FolderGitIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";
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
	AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader } from "@/components/ui/card";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import {
	InputGroup,
	InputGroupAddon,
	InputGroupButton,
	InputGroupInput,
} from "@/components/ui/input-group";
import { Item, ItemActions, ItemContent, ItemGroup, ItemTitle } from "@/components/ui/item";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { problemDetailOf } from "@/lib/problem-detail";
import { IntegrationCardHeading } from "./IntegrationCardHeading";

interface RepositoryItem {
	nameWithOwner: string;
}

/**
 * One repository row and its removal confirm.
 *
 * The dialog is controlled so the pending state can exist at all: `AlertDialogAction` closes on
 * click, so a `disabled={isRemoving}` on an uncontrolled action unmounts before the flag ever flips —
 * the state was in the code but could never be reached, let alone seen. Holding the dialog open across
 * the mutation gives the confirm somewhere to say "Removing…", and gives the second click something to
 * bounce off.
 */
function RepositoryRow({
	repo,
	isRemoving,
	onRemove,
}: {
	repo: RepositoryItem;
	isRemoving: boolean;
	onRemove: (nameWithOwner: string) => void;
}) {
	const [open, setOpen] = useState(false);

	// The row unmounts on success, so nothing here needs to close the dialog on the happy path. A
	// failure leaves the row — and this dialog — mounted, which is what lets the flag fall back to
	// false and the confirm become pressable again.
	const handleRemove = (event: React.MouseEvent) => {
		event.preventDefault();
		onRemove(repo.nameWithOwner);
	};

	return (
		<Item variant="outline" size="sm">
			<ItemContent>
				<ItemTitle className="truncate font-mono" title={repo.nameWithOwner}>
					{repo.nameWithOwner}
				</ItemTitle>
			</ItemContent>
			<ItemActions>
				<AlertDialog open={open} onOpenChange={setOpen}>
					<AlertDialogTrigger
						render={
							<Button variant="outline" size="icon" aria-label={`Remove ${repo.nameWithOwner}`}>
								<Trash2Icon className="size-4" />
							</Button>
						}
					/>
					<AlertDialogContent>
						<AlertDialogHeader>
							<AlertDialogTitle>Stop monitoring {repo.nameWithOwner}?</AlertDialogTitle>
							<AlertDialogDescription>
								Are you sure you want to stop monitoring this repository? This action cannot be
								undone and will remove all data associated with this repository.
							</AlertDialogDescription>
						</AlertDialogHeader>
						<AlertDialogFooter>
							<AlertDialogCancel disabled={isRemoving}>Cancel</AlertDialogCancel>
							<AlertDialogAction variant="destructive" disabled={isRemoving} onClick={handleRemove}>
								{isRemoving ? (
									<>
										<Spinner className="size-4" aria-hidden />
										Stopping…
									</>
								) : (
									"Stop monitoring"
								)}
							</AlertDialogAction>
						</AlertDialogFooter>
					</AlertDialogContent>
				</AlertDialog>
			</ItemActions>
		</Item>
	);
}

interface AdminRepositoriesSettingsProps {
	repositories: RepositoryItem[];
	isLoading: boolean;
	error: Error | null;
	addRepositoryError: Error | null;
	isAddingRepository: boolean;
	isRemovingRepository: boolean;
	onAddRepository: (nameWithOwner: string) => void;
	onRemoveRepository: (nameWithOwner: string) => void;
	/** Refetches the repository list. Without it a load failure is a dead end, unlike every sibling. */
	onRetry?: () => void;
}

/**
 * Admin surface for the monitored-repositories plane: which repositories Hephaestus watches for
 * practice detection, plus adding and removing them. Rendered only for PAT-managed workspaces —
 * GitHub App Installation workspaces manage their repos upstream and surface them read-only through
 * the sync-state table instead. Pure presentation — data and mutations live in the container.
 */
export function AdminRepositoriesSettings({
	repositories,
	isLoading,
	error,
	addRepositoryError,
	isAddingRepository,
	isRemovingRepository,
	onAddRepository,
	onRemoveRepository,
	onRetry,
}: AdminRepositoriesSettingsProps) {
	const [repositoryInput, setRepositoryInput] = useState("");
	const isValidInput = repositoryInput.includes("/");

	const handleAddRepository = () => {
		if (isValidInput) {
			onAddRepository(repositoryInput);
			setRepositoryInput("");
		}
	};

	const hasRepositories = repositories.length > 0;

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader>
					<IntegrationCardHeading>Manage repositories</IntegrationCardHeading>
					<CardDescription>
						Add or remove the repositories Hephaestus watches for practice detection and mentoring.
						Their per-class sync freshness is shown in the sync-state table above.
					</CardDescription>
				</CardHeader>

				<CardContent className="space-y-4">
					{isLoading ? (
						/* Placeholder rows carry the same `Item` chrome — border, height, trailing action slot —
						   as the rows they become, so resolving swaps content into the same boxes. */
						<ItemGroup>
							{Array.from({ length: 3 }, (_, index) => (
								<Item key={index} variant="outline" size="sm">
									<ItemContent>
										<Skeleton className="h-5 w-48" />
									</ItemContent>
									<ItemActions>
										<Skeleton className="size-8 rounded-lg" />
									</ItemActions>
								</Item>
							))}
						</ItemGroup>
					) : error ? (
						<QueryErrorAlert
							error={error}
							title="We couldn't load the monitored repositories"
							onRetry={onRetry}
						/>
					) : hasRepositories ? (
						/* The sync-state table above is the canonical list of every repository; this is the
						   add/remove surface for the same set. A native max-height scroller keeps it a
						   compact pane rather than a second full-height copy of that table — max-h shrinks
						   to content for a handful of repos and scrolls in place for a large fleet. (A
						   vendored ScrollArea needs a *fixed* height to clip; with only max-h its viewport
						   never scrolls and every row renders, overflowing onto the add form below.) */
						<div className="max-h-80 overflow-y-auto pr-1">
							<ItemGroup>
								{repositories.map((repo) => (
									<RepositoryRow
										key={repo.nameWithOwner}
										repo={repo}
										isRemoving={isRemovingRepository}
										onRemove={onRemoveRepository}
									/>
								))}
							</ItemGroup>
						</div>
					) : (
						<Empty>
							<EmptyHeader>
								<EmptyMedia variant="icon">
									<FolderGitIcon />
								</EmptyMedia>
								<EmptyTitle>No repositories monitored yet</EmptyTitle>
								<EmptyDescription>
									Add a repository below to start monitoring it for practice detection.
								</EmptyDescription>
							</EmptyHeader>
						</Empty>
					)}

					<Field data-invalid={!!addRepositoryError}>
						<FieldLabel htmlFor="add-repository">Add a repository</FieldLabel>
						<InputGroup>
							<InputGroupInput
								id="add-repository"
								value={repositoryInput}
								onChange={(e) => setRepositoryInput(e.target.value)}
								placeholder="owner/name"
								disabled={isAddingRepository}
								autoComplete="off"
								aria-invalid={!!addRepositoryError}
							/>
							<InputGroupAddon align="inline-end">
								<InputGroupButton
									variant="default"
									onClick={handleAddRepository}
									disabled={!isValidInput || isAddingRepository}
								>
									Add
								</InputGroupButton>
							</InputGroupAddon>
						</InputGroup>
						<FieldDescription>
							Enter the repository as <code>owner/name</code>.
						</FieldDescription>
						{/* The server says why — "Repository not found", "Already monitored" — and that is the
						    only thing the admin can act on, so surface it instead of a fixed string. */}
						{addRepositoryError && <FieldError>{problemDetailOf(addRepositoryError)}</FieldError>}
					</Field>
				</CardContent>
			</Card>
		</div>
	);
}
