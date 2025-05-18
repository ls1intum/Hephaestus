import type { TeamInfo } from "@/api/types.gen";
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

	return (
		<section className="max-w-4xl mx-auto p-5">
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
								{sortMembers(team).length > 0 ? (
									<div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 self-center">
										{sortMembers(team).map((member) => (
											<a
												key={member.id}
												href={`https://github.com/${member.login}`}
												target="_blank"
												rel="noopener noreferrer"
												className="flex flex-col items-center text-center hover:bg-accent hover:text-accent-foreground rounded-md p-2 transition-colors"
											>
												<div className="w-20 h-20 rounded-full flex items-center justify-center overflow-hidden">
													<img
														src={member.avatarUrl}
														alt={member.name}
														className="w-full h-full object-cover"
													/>
												</div>
												<p className="font-semibold mt-2">{member.name}</p>
												<p className="text-muted-foreground text-sm">
													@{member.login}
												</p>
											</a>
										))}
									</div>
								) : (
									<div className="py-8 text-center">
										<p className="text-muted-foreground">
											No members assigned to this team
										</p>
									</div>
								)}
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
								<div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-4 self-center">
									{Array(4)
										.fill(null)
										.map((_, memberIndex) => (
											<div
												// biome-ignore lint/suspicious/noArrayIndexKey: Static arrays
												key={`loading-team-${teamIndex}-member-${memberIndex}`}
												className="flex flex-col items-center text-center"
											>
												<div className="w-20 h-20 rounded-full flex items-center justify-center overflow-hidden">
													<Skeleton className="w-full h-full object-cover" />
												</div>
												<Skeleton className="h-4 w-3/4 mt-2 mx-auto" />
												<Skeleton className="h-3 w-1/2 mt-1 mx-auto" />
											</div>
										))}
								</div>
							</CardContent>
						</Card>
					))}
		</section>
	);
}
