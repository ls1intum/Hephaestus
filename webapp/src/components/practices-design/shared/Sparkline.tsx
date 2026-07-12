import { cn } from "@/lib/utils";

export interface SparklineProps {
	/** Observations per week, oldest first. */
	values: readonly number[];
	className?: string;
	/** Accessible description, e.g. "Observation activity over the last six weeks". */
	label: string;
}

/**
 * A tiny inline activity sparkline. It shows observation volume over time, deliberately
 * unlabeled and unscaled: it answers "is there recent signal here" at a glance without
 * inviting numeric comparison between people.
 */
export function Sparkline({ values, className, label }: SparklineProps) {
	if (values.length < 2) return null;
	const width = 56;
	const height = 18;
	const max = Math.max(...values, 1);
	const step = width / (values.length - 1);
	const points = values
		.map((value, index) => {
			const x = index * step;
			const y = height - 2 - (value / max) * (height - 4);
			return `${x.toFixed(1)},${y.toFixed(1)}`;
		})
		.join(" ");
	return (
		<svg
			role="img"
			aria-label={label}
			viewBox={`0 0 ${width} ${height}`}
			className={cn("h-[18px] w-14 shrink-0 text-muted-foreground/70", className)}
		>
			<polyline
				points={points}
				fill="none"
				stroke="currentColor"
				strokeWidth="1.5"
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
		</svg>
	);
}
