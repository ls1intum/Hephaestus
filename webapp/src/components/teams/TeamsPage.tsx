import type { TeamInfo } from "@/api/types.gen";
import {
	type Contributor,
	ContributorGrid,
} from "@/components/shared/ContributorGrid";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * TeamsPage component for displaying contributors grouped by teams
 * This is a purely presentational component that receives data via props
 */
export interface TeamsPageProps {
	teams: TeamInfo[];
	isLoading: boolean;
}

export function TeamsPage({ teams, isLoading }: TeamsPageProps) {
	// Sort teams by name and filter out hidden teams
	// Using immutable operations to avoid mutating the original array
	const sortedTeams = [...teams]
		.filter((team: TeamInfo) => !team.hidden)
		.sort((a, b) => a.name.localeCompare(b.name));

	// Helper function to sort team members alphabetically by name
	const sortMembers = (team: TeamInfo) => {
		return [...team.members].sort((a, b) => a.name.localeCompare(b.name));
	};

	// Helper function to convert team members to Contributors
	const convertMembersToContributors = (team: TeamInfo): Contributor[] => {
		return sortMembers(team).map((member) => ({
			id: member.id,
			login: member.login,
			name: member.name,
			avatarUrl: member.avatarUrl,
			htmlUrl: `https://github.com/${member.login}`,
		}));
	};

	return (
		<>
			<h2 className="text-2xl font-bold mb-2">Team Contributors</h2>
			<p className="text-muted-foreground text-sm mb-4">
				Overview of contributors across different teams
			</p>

			{!isLoading && (
				<>
					{sortedTeams.map((team: TeamInfo) => (
						<Card key={team.id} className="flex flex-col mb-8 gap-3">
							<CardHeader>
								<CardTitle>{team.name}</CardTitle>
							</CardHeader>
							<CardContent>
								<ContributorGrid
									contributors={convertMembersToContributors(team)}
									size="sm"
									layout="compact"
									emptyState={
										<div className="py-8 text-center">
											<p className="text-muted-foreground">
												No members assigned to this team
											</p>
										</div>
									}
								/>
							</CardContent>
						</Card>
					))}

					{sortedTeams.length === 0 && (
						<div className="py-8 text-center">
							<p className="text-muted-foreground">No teams found</p>
						</div>
					)}
				</>
			)}

			{isLoading &&
				Array(3)
					.fill(null)
					.map((_, teamIndex) => (
						<Card
							// biome-ignore lint/suspicious/noArrayIndexKey: Static array
							key={`loading-team-${teamIndex}`}
							className="flex flex-col mb-8 gap-3"
						>
							<CardHeader>
								<Skeleton className="h-6 w-1/4" />
							</CardHeader>
							<CardContent>
								<ContributorGrid
									contributors={[]}
									isLoading={true}
									size="sm"
									layout="compact"
									loadingSkeletonCount={4}
								/>
							</CardContent>
						</Card>
					))}
		</>
	);
}
