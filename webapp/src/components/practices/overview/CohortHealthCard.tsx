import type { CohortPracticeStatus } from "@/api/types.gen";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

const SEGMENTS: ReadonlyArray<{
	key: "strengthCount" | "developingCount" | "mixedCount" | "noActivityCount";
	label: string;
	barClassName: string;
}> = [
	{ key: "strengthCount", label: "Strength", barClassName: "bg-provider-done" },
	{ key: "developingCount", label: "Developing", barClassName: "bg-provider-attention" },
	{ key: "mixedCount", label: "Mixed", barClassName: "bg-muted-foreground/40" },
	{ key: "noActivityCount", label: "No clear signal", barClassName: "bg-muted" },
];

export interface CohortHealthCardProps {
	health: CohortPracticeStatus;
}

export function CohortHealthCard({ health }: CohortHealthCardProps) {
	if (health.suppressed) {
		return (
			<Card>
				<CardHeader>
					<CardTitle className="text-base">{health.name}</CardTitle>
				</CardHeader>
				<CardContent>
					<p className="text-sm text-muted-foreground">
						Not enough recent activity to show this safely.
					</p>
				</CardContent>
			</Card>
		);
	}

	const counts = SEGMENTS.map((segment) => ({
		...segment,
		value: health[segment.key] ?? 0,
	}));
	const total = counts.reduce((sum, segment) => sum + segment.value, 0);

	return (
		<Card>
			<CardHeader>
				<CardTitle className="text-base">{health.name}</CardTitle>
			</CardHeader>
			<CardContent className="flex flex-col gap-3">
				<div
					className="flex h-2.5 w-full overflow-hidden rounded-full bg-muted"
					role="img"
					aria-label={`Cohort standings for ${health.name}: ${counts
						.map((c) => `${c.value} ${c.label}`)
						.join(", ")}`}
				>
					{total > 0 &&
						counts.map((segment) =>
							segment.value > 0 ? (
								<div
									key={segment.key}
									className={cn("h-full", segment.barClassName)}
									style={{ width: `${(segment.value / total) * 100}%` }}
								/>
							) : null,
						)}
				</div>
				<ul className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
					{counts.map((segment) => (
						<li key={segment.key} className="flex items-center gap-2">
							<span className={cn("size-2.5 shrink-0 rounded-full", segment.barClassName)} />
							<span className="text-muted-foreground">{segment.label}</span>
							<span className="ml-auto font-medium tabular-nums">{segment.value}</span>
						</li>
					))}
				</ul>
			</CardContent>
		</Card>
	);
}
