import { useMutation } from "@tanstack/react-query";
import { Trophy } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { recalculateUserAchievementsMutation } from "@/api/@tanstack/react-query.gen";
import type { ExtendedUserTeams } from "@/components/admin/types";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { AdminAchievementsTable } from "./AdminAchievementsTable";

interface AdminAchievementsPageProps {
	users: ExtendedUserTeams[];
	isLoading: boolean;
	workspaceSlug: string;
}

export function AdminAchievementsPage({
	users,
	isLoading,
	workspaceSlug,
}: AdminAchievementsPageProps) {
	const [isRecalculatingAll, setIsRecalculatingAll] = useState(false);
	const [recalculatingUsers, setRecalculatingUsers] = useState<Set<string>>(new Set());

	const recalculateMutation = useMutation(recalculateUserAchievementsMutation());

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
		} catch (error) {
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
		<div className="container mx-auto py-6">
			<div className="flex items-center justify-between mb-6">
				<div>
					<h1 className="text-3xl font-bold tracking-tight">Manage Achievements</h1>
					<p className="text-muted-foreground">
						Recalculate achievements for users in this workspace.
					</p>
				</div>
				<Button
					onClick={handleRecalculateAll}
					disabled={isLoading || isRecalculatingAll || users.length === 0}
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
			</div>

			<AdminAchievementsTable
				users={users}
				isLoading={isLoading}
				workspaceSlug={workspaceSlug}
				onRecalculate={handleRecalculateSingle}
				recalculatingUsers={recalculatingUsers}
			/>
		</div>
	);
}
