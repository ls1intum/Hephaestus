import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { BotIcon } from "lucide-react";

interface AssistantAvatarProps {
	/** Optional CSS class name */
	className?: string;
}

export function MentorAvatar({ className }: AssistantAvatarProps) {
	return (
		<Avatar className={cn("size-8", className)}>
			<AvatarFallback className="bg-background">
				<BotIcon size={24} />
			</AvatarFallback>
		</Avatar>
	);
}
