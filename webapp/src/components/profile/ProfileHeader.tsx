import { Link } from "@tanstack/react-router";
import { Sparkles } from "lucide-react";
import type { UserInfo } from "@/api/types.gen";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getInitials } from "@/lib/avatar";

export interface ProfileHeaderProps {
	user?: UserInfo;
	isLoading: boolean;
	/** Workspace slug for routing to achievement page. */
	workspaceSlug: string;
	achievementsEnabled?: boolean;
}

export function ProfileHeader({
	user,
	isLoading,
	workspaceSlug,
	achievementsEnabled = true,
}: ProfileHeaderProps) {
	return (
		<div className="flex items-start justify-between gap-6 mx-8">
			{/* Left section: Avatar + User Info */}
			<div className="flex flex-col gap-4 w-full max-w-xl">
				{/* Avatar + Name + GitHub Link */}
				<div className="flex items-center gap-4">
					{/* Avatar */}
					<div className="relative shrink-0">
						{isLoading ? (
							<Avatar className="size-16">
								<Skeleton className="h-full w-full rounded-full" />
							</Avatar>
						) : (
							<Avatar className="size-16 border-2 border-background shadow-sm">
								<AvatarImage src={user?.avatarUrl} alt={`${user?.login}'s avatar`} />
								<AvatarFallback>{getInitials(user?.name, user?.login)}</AvatarFallback>
							</Avatar>
						)}
					</div>

					{/* Name + GitHub Link */}
					{isLoading ? (
						<div className="flex flex-col gap-1.5">
							<Skeleton className="h-7 w-40" />
							<Skeleton className="h-5 w-48" />
						</div>
					) : user ? (
						<div className="flex flex-col gap-0.5">
							<h1 className="text-xl md:text-2xl font-bold leading-tight">{user.name}</h1>
							<div className="flex items-center gap-3">
								<a
									className="text-sm md:text-base text-muted-foreground hover:text-primary transition-colors"
									href={user.htmlUrl}
									target="_blank"
									rel="noopener noreferrer"
								>
									{user.htmlUrl ? new URL(user.htmlUrl).host : ""}/{user.login}
								</a>
								{achievementsEnabled && (
									<Button
										variant="ghost"
										size="sm"
										className="h-7 gap-1.5 text-muted-foreground hover:text-foreground"
										render={
											<Link
												to="/w/$workspaceSlug/user/$username/achievements"
												params={{ workspaceSlug, username: user.login ?? "" }}
											/>
										}
									>
										<Sparkles className="w-3.5 h-3.5" />
										<span className="text-xs">Achievements</span>
									</Button>
								)}
							</div>
						</div>
					) : null}
				</div>
			</div>
		</div>
	);
}
