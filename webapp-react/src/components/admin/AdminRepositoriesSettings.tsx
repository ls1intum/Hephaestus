import { Trash2 } from "lucide-react";
import { useState } from "react";
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
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";

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
	onAddRepository: (nameWithOwner: string) => void;
	onRemoveRepository: (nameWithOwner: string) => void;
}

/**
 * Component for managing monitored repositories in admin settings
 */
export function AdminRepositoriesSettings({
	repositories = [],
	isLoading,
	error,
	addRepositoryError,
	isAddingRepository,
	isRemovingRepository,
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

	return (
		<div className="space-y-6">
			<div>
				<h2 className="text-lg font-semibold mb-4">Monitored Repositories</h2>
				<Card>
					<CardContent>
						<div className="space-y-4">
							{/* Repository List */}
							<div className="space-y-2">
								{repositories.map((repo) => (
									<div
										key={repo.nameWithOwner}
										className="flex items-center gap-2"
									>
										<AlertDialog>
											<AlertDialogTrigger asChild>
												<Button
													variant="outline"
													size="icon"
													aria-label={`Remove ${repo.nameWithOwner}`}
												>
													<Trash2 className="h-4 w-4" />
												</Button>
											</AlertDialogTrigger>
											<AlertDialogContent>
												<AlertDialogHeader>
													<AlertDialogTitle>
														Stop monitoring {repo.nameWithOwner}?
													</AlertDialogTitle>
													<AlertDialogDescription>
														Are you sure you want to stop monitoring this
														repository? This action cannot be undone and will
														remove all data associated with this repository.
													</AlertDialogDescription>
												</AlertDialogHeader>
												<AlertDialogFooter>
													<AlertDialogCancel>Cancel</AlertDialogCancel>
													<AlertDialogAction
														onClick={() =>
															onRemoveRepository(repo.nameWithOwner)
														}
														disabled={isRemovingRepository}
													>
														Stop Monitoring
													</AlertDialogAction>
												</AlertDialogFooter>
											</AlertDialogContent>
										</AlertDialog>
										<div className="bg-accent/50 p-2 px-4 rounded-md">
											{repo.nameWithOwner}
										</div>
									</div>
								))}{" "}
								{isLoading && (
									<div className="flex items-center justify-center py-4">
										<div className="animate-pulse h-8 w-32 bg-muted rounded" />
									</div>
								)}
								{error && (
									<div className="text-destructive text-sm">
										Failed to load repositories. Please try again.
									</div>
								)}
							</div>

							{/* Add Repository Input */}
							<div className="space-y-2">
								<div className="flex items-center gap-2">
									<Input
										placeholder="Add a repository (owner/name)"
										value={repositoryInput}
										onChange={(e) => setRepositoryInput(e.target.value)}
										className="flex-1"
									/>
									<Button
										onClick={handleAddRepository}
										disabled={!isValidInput || isAddingRepository}
									>
										Add
									</Button>
								</div>
								{addRepositoryError && (
									<div className="text-destructive text-sm">
										An error occurred while adding the repository.
									</div>
								)}
							</div>
						</div>
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
