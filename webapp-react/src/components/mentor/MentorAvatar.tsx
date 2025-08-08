import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { MentorIcon } from "./MentorIcon";

interface MentorAvatarProps {
	/** Size of the avatar */
	size?: "default" | "sm" | "lg";
	/** Optional CSS class name */
	className?: string;
}

export function MentorAvatar({ className }: MentorAvatarProps) {
	return (
		<Avatar className={cn("size-16 -m-4", className)}>
			<AvatarFallback className="bg-transparent text-muted-foreground size-16">
				<MentorIcon className="size-16" pad={8} />
			</AvatarFallback>
		</Avatar>
	);
}
