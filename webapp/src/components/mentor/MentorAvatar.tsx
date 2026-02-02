import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { MentorIcon } from "./MentorIcon";

interface MentorAvatarProps {
	/** Size of the avatar */
	size?: "default" | "sm" | "lg";
	/** Optional CSS class name */
	className?: string;
	/** Whether the assistant is currently streaming */
	streaming?: boolean;
}

export function MentorAvatar({ className, streaming = false }: MentorAvatarProps) {
	return (
		<Avatar className={cn("size-16 -m-4 after:border-0", className)}>
			<AvatarFallback className="bg-transparent text-muted-foreground size-16">
				<MentorIcon size={64} pad={8} streaming={streaming} />
			</AvatarFallback>
		</Avatar>
	);
}
