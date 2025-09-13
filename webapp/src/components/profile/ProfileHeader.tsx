import { ClockIcon } from "@primer/octicons-react";
import { format } from "date-fns";
import type { RepositoryInfo, UserInfo } from "@/api/types.gen";
import { LeagueIcon } from "@/components/leaderboard/LeagueIcon";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
	Tooltip,
	TooltipContent,
	TooltipTrigger,
} from "@/components/ui/tooltip";

// Repository images for known repositories
const REPO_IMAGES: Record<string, string> = {
	"ls1intum/Hephaestus":
		"https://github.com/ls1intum/Hephaestus/raw/refs/heads/develop/docs/images/hammer_bg.svg",
	"ls1intum/Artemis": "https://artemis.tum.de/public/images/logo.png",
	"ls1intum/Athena":
		"https://raw.githubusercontent.com/ls1intum/Athena/develop/playground/public/logo.png",
};

export interface ProfileHeaderProps {
	user?: UserInfo;
	firstContribution?: Date;
	contributedRepositories?: RepositoryInfo[];
	leaguePoints?: number;
	isLoading: boolean;
}

export function ProfileHeader({
	user,
	firstContribution,
	contributedRepositories = [],
	leaguePoints = 0,
	isLoading,
}: ProfileHeaderProps) {
	// Format the first contribution date if available
	const formattedFirstContribution = firstContribution
		? format(firstContribution, "MMMM do, yyyy")
		: undefined;

	// Function to get repository image based on nameWithOwner
	const getRepositoryImage = (nameWithOwner: string) => {
		return (
			REPO_IMAGES[nameWithOwner] ||
			`https://github.com/${nameWithOwner.split("/")[0]}.png`
		);
	};
	return (
		<div className="flex items-center justify-between mx-8">
			<div className="flex gap-8 items-center">
				{/* Avatar with loading skeleton */}
				{isLoading ? (
					<Avatar className="w-24 h-24 ring-2 ring-neutral-100 dark:ring-neutral-800">
						<Skeleton className="h-full w-full rounded-full" />
					</Avatar>
				) : (
					<Avatar className="w-24 h-24 ring-2 ring-neutral-100 dark:ring-neutral-800">
						<AvatarImage
							src={user?.avatarUrl}
							alt={`${user?.login}'s avatar`}
						/>
						<AvatarFallback>
							{user?.login?.slice(0, 2)?.toUpperCase()}
						</AvatarFallback>
					</Avatar>
				)}

				{/* User information with loading skeletons */}
				{isLoading ? (
					<div className="flex flex-col gap-2">
						<Skeleton className="h-8 w-48" />
						<Skeleton className="h-5 w-64" />
						<Skeleton className="h-5 w-80" />
						<div className="flex items-center gap-2">
							<Skeleton className="size-10" />
							<Skeleton className="size-10" />
						</div>
					</div>
				) : user ? (
					<div className="flex flex-col gap-1">
						{/* User name */}
						<h1 className="text-2xl md:text-3xl font-bold leading-6">
							{user.name}
						</h1>

						{/* GitHub profile link */}
						<a
							className="md:text-lg font-medium text-muted-foreground mb-1 hover:text-github-accent-foreground"
							href={user.htmlUrl}
							target="_blank"
							rel="noopener noreferrer"
						>
							github.com/{user.login}
						</a>

						{/* First contribution */}
						{formattedFirstContribution && (
							<div className="flex items-center gap-1 md:gap-2 text-muted-foreground font-medium text-sm md:text-base">
								<ClockIcon size={16} className="overflow-visible" />
								<span>Contributing since {formattedFirstContribution}</span>
							</div>
						)}

						{/* Contributed repositories */}
						{contributedRepositories.length > 0 && (
							<div className="flex items-center gap-2 mt-1">
								{contributedRepositories.map((repository) => (
									<Tooltip key={repository.id}>
										<TooltipTrigger asChild>
											<Button
												variant="outline"
												size="icon"
												className="size-10 p-1"
												asChild
											>
												<a
													href={repository.htmlUrl}
													target="_blank"
													rel="noopener noreferrer"
												>
													<img
														src={getRepositoryImage(repository.nameWithOwner)}
														alt={repository.name}
														className="size-full object-contain"
													/>
												</a>
											</Button>
										</TooltipTrigger>
										<TooltipContent>{repository.nameWithOwner}</TooltipContent>
									</Tooltip>
								))}
							</div>
						)}
					</div>
				) : null}
			</div>

			{/* League information */}
			<div className="flex flex-col justify-center items-center gap-2">
				{isLoading ? (
					<>
						<Skeleton className="size-28 rounded-full" />
						<Skeleton className="h-8 w-16" />
					</>
				) : (
					<>
						<LeagueIcon leaguePoints={leaguePoints} size="max" />
						<span className="text-muted-foreground text-xl md:text-2xl font-bold leading-6">
							{leaguePoints}
						</span>
					</>
				)}
			</div>
		</div>
	);
}
