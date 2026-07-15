import { FolderGitIcon, InfoIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
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

/**
 * Interface for repository item
 */
export interface RepositoryItem {
	nameWithOwner: string;
}

interface AdminRepositoriesSettingsProps {
	repositories: RepositoryItem[];
	isLoading: boolean;
	error: Error | null;
	addRepositoryError: Error | null;
	isAddingRepository: boolean;
	isRemovingRepository: boolean;
	/** Whether repository management is disabled (for GitHub App Installation workspaces) */
	isReadOnly?: boolean;
	onAddRepository: (nameWithOwner: string) => void;
	onRemoveRepository: (nameWithOwner: string) => void;
}

/**
 * Admin surface for the monitored-repositories plane: which repositories Hephaestus watches for
 * practice detection, plus adding and removing them. GitHub App Installation workspaces are managed
 * upstream by the installation, so for those (`isReadOnly`) the list is read-only and the manual
 * add/remove controls are withheld. Pure presentation — data and mutations live in the container.
 */
export function AdminRepositoriesSettings({
	repositories = [],
	isLoading,
	error,
	addRepositoryError,
	isAddingRepository,
	isRemovingRepository,
	isReadOnly = false,
	onAddRepository,
	onRemoveRepository,
}: AdminRepositoriesSettingsProps) {
	const [repositoryInput, setRepositoryInput] = useState("");
	const isValidInput = repositoryInput?.includes("/");

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
					{isReadOnly && (
						<Alert>
							<InfoIcon />
							<AlertTitle>Managed by a GitHub App Installation</AlertTitle>
							<AlertDescription>
								Repositories are synced automatically from the installation&apos;s configuration, so
								they cannot be added or removed here.
							</AlertDescription>
						</Alert>
					)}

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
									{!isReadOnly && (
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
														<AlertDialogTitle>
															Stop monitoring {repo.nameWithOwner}?
														</AlertDialogTitle>
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
									)}
								</Item>
							))}
						</ItemGroup>
					) : (
						<Empty>
							<EmptyHeader>
								<EmptyMedia variant="icon">
									<FolderGitIcon />
								</EmptyMedia>
								<EmptyTitle>
									{isReadOnly ? "No repositories synced yet" : "No repositories monitored yet"}
								</EmptyTitle>
								<EmptyDescription>
									{isReadOnly
										? "Repositories will appear here once the GitHub App Installation syncs them."
										: "Add a repository below to start monitoring it for practice detection."}
								</EmptyDescription>
							</EmptyHeader>
						</Empty>
					)}

					{!isReadOnly && (
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
					)}
				</CardContent>
			</Card>
		</div>
	);
}
