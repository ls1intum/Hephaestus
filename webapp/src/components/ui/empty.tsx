import { CircleSlash } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { Card, CardContent } from "./card";

interface EmptyProps {
	title: string;
	description?: string;
	icon?: ReactNode;
	action?: ReactNode;
	className?: string;
}

export function Empty({
	title,
	description,
	icon = <CircleSlash className="size-5 text-muted-foreground" />,
	action,
	className,
}: EmptyProps) {
	return (
		<Card className={cn("border-dashed", className)}>
			<CardContent className="flex flex-col items-center justify-center gap-3 py-10 text-center">
				<span className="flex h-10 w-10 items-center justify-center rounded-full border text-muted-foreground">
					{icon}
				</span>
				<div className="space-y-1">
					<p className="text-base font-semibold">{title}</p>
					{description ? (
						<p className="text-sm text-muted-foreground">{description}</p>
					) : null}
				</div>
				{action ? <div className="mt-1">{action}</div> : null}
			</CardContent>
		</Card>
	);
}
