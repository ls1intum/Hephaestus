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
import { IntegrationCardHeading } from "./IntegrationCardHeading";

interface RepositoryItem {
	nameWithOwner: string;
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
					<IntegrationCardHeading>Monitored repositories</IntegrationCardHeading>
					<CardDescription>
						Repositories Hephaestus watches for practice detection and mentoring.
					</CardDescription>
				</CardHeader>

				<CardContent className="space-y-4">
					{isLoading ? (
						<div className="space-y-2">
							<Skeleton className="h-10 w-full" />
							<Skeleton className="h-10 w-full" />
							<Skeleton className="h-10 w-full" />
						</div>
					) : error ? (
						<QueryErrorAlert error={error} title="We couldn't load the monitored repositories" />
					) : hasRepositories ? (
						<ItemGroup className="max-h-80 overflow-y-auto pr-1">
							{repositories.map((repo) => (
								<Item key={repo.nameWithOwner} variant="outline" size="sm">
									<ItemContent>
										<ItemTitle className="font-mono">{repo.nameWithOwner}</ItemTitle>
									</ItemContent>
									<ItemActions>
										<AlertDialog>
											<AlertDialogTrigger
												render={
													<Button
														variant="outline"
														size="icon"
														aria-label={`Remove ${repo.nameWithOwner}`}
													>
														<Trash2Icon className="size-4" />
													</Button>
												}
											/>
											<AlertDialogContent>
												<AlertDialogHeader>
													<AlertDialogTitle>Stop monitoring {repo.nameWithOwner}?</AlertDialogTitle>
													<AlertDialogDescription>
														Are you sure you want to stop monitoring this repository? This action
														cannot be undone and will remove all data associated with this
														repository.
													</AlertDialogDescription>
												</AlertDialogHeader>
												<AlertDialogFooter>
													<AlertDialogCancel>Cancel</AlertDialogCancel>
													<AlertDialogAction
														variant="destructive"
														onClick={() => onRemoveRepository(repo.nameWithOwner)}
														disabled={isRemovingRepository}
													>
														Stop monitoring
													</AlertDialogAction>
												</AlertDialogFooter>
											</AlertDialogContent>
										</AlertDialog>
									</ItemActions>
								</Item>
							))}
						</ItemGroup>
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
						{addRepositoryError && (
							<FieldError>An error occurred while adding the repository.</FieldError>
						)}
					</Field>
				</CardContent>
			</Card>
		</div>
	);
}
