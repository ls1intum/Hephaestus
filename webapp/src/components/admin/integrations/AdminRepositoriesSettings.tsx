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
import { ScrollArea } from "@/components/ui/scroll-area";
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
 * Base UI's `AlertDialogAction` is a plain button, not a Close, so confirming does not dismiss the
 * dialog. That keeps it open across the mutation: the confirm shows "Stopping…", the row unmounts on
 * success (taking the dialog with it), and a failure leaves the dialog open with the confirm
 * pressable again once `isRemoving` clears.
 */
function RepositoryRow({
	repo,
	providerLabel,
	isRemoving,
	onRemove,
}: {
	repo: RepositoryItem;
	providerLabel: string;
	isRemoving: boolean;
	onRemove: (nameWithOwner: string) => void;
}) {
	const [open, setOpen] = useState(false);

	const handleRemove = () => {
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
								This stops syncing the repository and{" "}
								<strong>
									permanently erases everything Hephaestus has mirrored from it — its issues, pull
									requests, reviews, and the practice detections built from them
								</strong>
								. The repository on {providerLabel} itself is not affected, and you can start
								monitoring it again later.
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
	/** The provider these repositories live on ("GitHub"/"GitLab"), named in the remove confirm so the
	 * admin knows the upstream repository is untouched. */
	providerLabel?: string;
	isLoading: boolean;
	error: Error | null;
	addRepositoryError: Error | null;
	isAddingRepository: boolean;
	isRemovingRepository: boolean;
	onAddRepository: (nameWithOwner: string) => void;
	onRemoveRepository: (nameWithOwner: string) => void;
	/** Refetches the repository list; without it a load failure is a dead end. */
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
	providerLabel = "GitHub or GitLab",
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
						/* A compact pane that grows to content for a handful of repos and scrolls in place
						   for a large fleet. The cap goes on the Viewport (ScrollArea's viewportClassName):
						   a max-h on the Root would never clip, because the Viewport is height:100% and there
						   is no definite-height ancestor here to resolve against. */
						<ScrollArea viewportClassName="max-h-80">
							<ItemGroup className="pr-3">
								{repositories.map((repo) => (
									<RepositoryRow
										key={repo.nameWithOwner}
										repo={repo}
										providerLabel={providerLabel}
										isRemoving={isRemovingRepository}
										onRemove={onRemoveRepository}
									/>
								))}
							</ItemGroup>
						</ScrollArea>
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
