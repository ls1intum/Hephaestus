import { Link, type LinkProps } from "@tanstack/react-router";
import type { LucideIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

interface OverviewStatCardProps {
	label: string;
	value: string | number;
	/** Secondary line under the value, e.g. "3 active". */
	hint?: string;
	icon: LucideIcon;
	/** The admin page this stat drills into. */
	to: LinkProps["to"];
	isLoading?: boolean;
}

/** One glanceable metric on the instance overview, linking to the page that manages it. */
export function OverviewStatCard({
	label,
	value,
	hint,
	icon: Icon,
	to,
	isLoading = false,
}: OverviewStatCardProps) {
	return (
		<Link
			to={to}
			className="group block rounded-xl outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
		>
			<Card className="h-full transition-colors group-hover:border-primary/40">
				<CardContent className="flex items-start justify-between gap-3">
					<div className="space-y-1">
						<p className="text-sm text-muted-foreground">{label}</p>
						{isLoading ? (
							<Skeleton className="h-8 w-16" />
						) : (
							<p className="text-2xl font-semibold tabular-nums">{value}</p>
						)}
						{hint && !isLoading ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
					</div>
					<div className="rounded-full bg-muted p-2">
						<Icon className="size-4 text-muted-foreground" aria-hidden />
					</div>
				</CardContent>
			</Card>
		</Link>
	);
}
