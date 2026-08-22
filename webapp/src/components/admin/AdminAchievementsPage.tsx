import { useMutation, useQueryClient } from "@tanstack/react-query";
import { RefreshCw, Trophy } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	recalculateUserAchievementsMutation,
	reloadAchievementsMutation,
} from "@/api/@tanstack/react-query.gen";
import type { ExtendedUserTeams } from "@/components/admin/types";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth/AuthContext";
import { queryOperationId } from "@/lib/query-operation-id";
import { AdminAchievementsTable } from "./AdminAchievementsTable";

interface AdminAchievementsPageProps {
	users: ExtendedUserTeams[];
	isLoading: boolean;
	workspaceSlug: string;
	error?: unknown;
	onRetry?: () => void;
}

export function AdminAchievementsPage({
	users,
	isLoading,
	workspaceSlug,
	error,
	onRetry,
}: AdminAchievementsPageProps) {
	const queryClient = useQueryClient();
	const { username } = useAuth();
	const [isRecalculatingAll, setIsRecalculatingAll] = useState(false);
	const [recalculatingUsers, setRecalculatingUsers] = useState<Set<string>>(new Set());

	const recalculateMutation = useMutation(recalculateUserAchievementsMutation());
	const reloadMutation = useMutation(reloadAchievementsMutation());

	const handleReload = async () => {
		toast.promise(
			reloadMutation.mutateAsync({
				path: { workspaceSlug, login: username || "" },
			}),
			{
				loading: "Reloading achievement definitions...",
				success: () => {
					void queryClient.invalidateQueries({
						predicate: (query) => {
							const id = queryOperationId(query.queryKey);
							return id === "getUserAchievements" || id === "getAllAchievementDefinitions";
						},
					});
					return "Successfully reloaded achievements from YAML";
				},
				error: "Failed to reload achievement definitions",
			},
		);
	};

	const handleRecalculateAll = async () => {
		if (!users.length) return;
		setIsRecalculatingAll(true);

		const toastId = toast.loading(`Starting recalculation for ${users.length} users...`);

		let successCount = 0;
		let failCount = 0;

		try {
			await Promise.all(
				users.map((u) =>
					recalculateMutation
						.mutateAsync({
							path: { workspaceSlug, login: u.user.login },
						})
						.then(() => successCount++)
						.catch(() => failCount++),
				),
			);

			if (failCount === 0) {
				toast.success(`Successfully dispatched recalculation for ${successCount} users`, {
					id: toastId,
				});
			} else {
				toast.warning(`Dispatched recalculation for ${successCount} users, ${failCount} failed`, {
					id: toastId,
				});
			}
		} catch (_error) {
			toast.error("An error occurred during bulk recalculation.", { id: toastId });
		} finally {
			setIsRecalculatingAll(false);
		}
	};

	const handleRecalculateSingle = async (username: string) => {
		setRecalculatingUsers((prev) => new Set(prev).add(username));
		toast.promise(
			recalculateMutation.mutateAsync({
				path: { workspaceSlug, login: username },
			}),
			{
				loading: `Recalculating achievements for ${username}...`,
				success: `Successfully dispatched recalculation for ${username}`,
				error: `Failed to recalculate achievements for ${username}`,
				finally: () => {
					setRecalculatingUsers((prev) => {
						const newSet = new Set(prev);
						newSet.delete(username);
						return newSet;
					});
				},
			},
		);
	};

	return (
		<PageLayout>
			<PageHeader
				icon={<Trophy />}
				title="Achievements"
				description="Recalculate achievements for workspace members."
				actions={
					<>
						<Button
							variant="outline"
							onClick={handleReload}
							disabled={isLoading || reloadMutation.isPending}
							className="w-full sm:w-auto"
						>
							{reloadMutation.isPending ? (
								<>
									<Spinner className="mr-2 h-4 w-4" />
									Reloading...
								</>
							) : (
								<>
									<RefreshCw className="mr-2 h-4 w-4" />
									Reload Definitions
								</>
							)}
						</Button>
						<Button
							onClick={handleRecalculateAll}
							disabled={isLoading || isRecalculatingAll || users.length === 0}
							className="w-full sm:w-auto"
						>
							{isRecalculatingAll ? (
								<>
									<Spinner className="mr-2 h-4 w-4" />
									Recalculating All...
								</>
							) : (
								<>
									<Trophy className="mr-2 h-4 w-4" />
									Recalculate All
								</>
							)}
						</Button>
					</>
				}
			/>

			{error ? (
				<QueryErrorAlert error={error} title="Couldn't load achievements" onRetry={onRetry} />
			) : (
				<AdminAchievementsTable
					users={users}
					isLoading={isLoading}
					workspaceSlug={workspaceSlug}
					onRecalculate={handleRecalculateSingle}
					recalculatingUsers={recalculatingUsers}
				/>
			)}
		</PageLayout>
	);
}
