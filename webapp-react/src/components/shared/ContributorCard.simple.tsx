import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";

export interface Contributor {
	id: number;
	login: string;
	name: string;
	avatarUrl: string;
	htmlUrl: string;
}

interface ContributorCardProps {
	contributor: Contributor;
	size?: "sm" | "md";
	className?: string;
}

/**
 * ContributorCard component for displaying individual contributor information
 * with avatar, name, and GitHub profile link.
 */
export function ContributorCard({
	contributor,
	size = "md",
	className,
}: ContributorCardProps) {
	const isSmall = size === "sm";

	return (
		<a
			href={contributor.htmlUrl}
			target="_blank"
			rel="noopener noreferrer"
			className={cn(
				"flex flex-col items-center rounded-lg transition-colors hover:bg-accent hover:text-accent-foreground",
				isSmall ? "gap-1 p-1" : "gap-2 p-2",
				className,
			)}
			title={`${contributor.name} (@${contributor.login})`}
		>
			<Avatar className={isSmall ? "size-12" : "size-16"}>
				<AvatarImage
					src={contributor.avatarUrl}
					alt={`${contributor.login}'s avatar`}
				/>
				<AvatarFallback>
					{contributor.login.slice(0, 2).toUpperCase()}
				</AvatarFallback>
			</Avatar>
			<div className="flex flex-col items-center min-w-0 w-full space-y-0.5">
				<div
					className={cn(
						"font-medium text-center w-full px-1 leading-tight",
						isSmall ? "text-xs" : "text-sm",
					)}
					style={{ wordBreak: "break-word", lineHeight: "1.2" }}
				>
					{contributor.name}
				</div>
				<div
					className="text-muted-foreground text-center w-full px-1 text-xs leading-tight"
					style={{ wordBreak: "break-word", lineHeight: "1.2" }}
				>
					@{contributor.login}
				</div>
			</div>
		</a>
	);
}
