import { AdminLeagueSettings } from "./AdminLeagueSettings";
import { AdminRepositoriesSettings } from "./AdminRepositoriesSettings";

// Use the RepositoryItem type from the AdminRepositoriesSettings component
type RepositoryItem = {
	nameWithOwner: string;
};

export interface AdminSettingsPageProps {
	repositories: RepositoryItem[];
	isLoadingRepositories: boolean;
	repositoriesError: Error | null;
	addRepositoryError: Error | null;
	isAddingRepository: boolean;
	isRemovingRepository: boolean;
	isResettingLeagues: boolean;
	/** Whether repository management is disabled (for GitHub App Installation workspaces) */
	isAppInstallationWorkspace?: boolean;
	onAddRepository: (nameWithOwner: string) => void;
	onRemoveRepository: (nameWithOwner: string) => void;
	onResetLeagues: () => void;
}

/**
 * Presentational component for the admin settings page
 */
export function AdminSettingsPage({
	repositories,
	isLoadingRepositories,
	repositoriesError,
	addRepositoryError,
	isAddingRepository,
	isRemovingRepository,
	isResettingLeagues,
	isAppInstallationWorkspace = false,
	onAddRepository,
	onRemoveRepository,
	onResetLeagues,
}: AdminSettingsPageProps) {
	return (
		<div className="container mx-auto py-6 max-w-4xl">
			<h1 className="text-3xl font-bold mb-8">Workspace Settings</h1>

			<div className="space-y-10">
				{/* Repositories Settings */}
				<AdminRepositoriesSettings
					repositories={repositories}
					isLoading={isLoadingRepositories}
					error={repositoriesError}
					addRepositoryError={addRepositoryError}
					isAddingRepository={isAddingRepository}
					isRemovingRepository={isRemovingRepository}
					isReadOnly={isAppInstallationWorkspace}
					onAddRepository={onAddRepository}
					onRemoveRepository={onRemoveRepository}
				/>

				{/* Leagues Settings */}
				<AdminLeagueSettings
					isResetting={isResettingLeagues}
					onResetLeagues={onResetLeagues}
				/>
			</div>
		</div>
	);
}
