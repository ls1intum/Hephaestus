import type { ReviewSubject } from "@/api/types.gen";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { getInitials } from "@/lib/avatar";
import { cn } from "@/lib/utils";
import { subjectLabel } from "./review-format";

export interface ReviewPersonProps {
	person: ReviewSubject | undefined;
	prefix?: string;
	className?: string;
	display?: "compact" | "full";
}

export function ReviewPerson({
	person,
	prefix,
	className,
	display = "compact",
}: ReviewPersonProps) {
	return (
		<span className={cn("flex min-w-0 items-center gap-2 text-sm", className)}>
			<Avatar size="sm">
				<AvatarImage src={person?.avatarUrl} alt="" />
				<AvatarFallback>{getInitials(person?.name, person?.login)}</AvatarFallback>
			</Avatar>
			<span className={cn(display === "compact" ? "truncate" : "break-words")}>
				{prefix && <span className="text-muted-foreground">{prefix} </span>}
				{subjectLabel(person)}
			</span>
		</span>
	);
}
