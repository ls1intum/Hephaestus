import type { FindingFeedbackEngagement } from "@/api/types.gen";
import { Card } from "@/components/ui/card";

const RING_SIZE = 80;
const STROKE_WIDTH = 8;
const RADIUS = (RING_SIZE - STROKE_WIDTH) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

export interface EngagementOverviewProps {
	engagement: FindingFeedbackEngagement;
	totalFindings: number;
}

export function EngagementOverview({ engagement, totalFindings }: EngagementOverviewProps) {
	const totalResponded = engagement.applied + engagement.disputed + engagement.notApplicable;
	const engagementRate = totalFindings > 0 ? Math.round((totalResponded / totalFindings) * 100) : 0;
	const offset = CIRCUMFERENCE - (engagementRate / 100) * CIRCUMFERENCE;

	return (
		<Card className="p-4 flex flex-col sm:flex-row items-center gap-4 sm:gap-6">
			{/* Engagement ring */}
			<div className="relative shrink-0" style={{ width: RING_SIZE, height: RING_SIZE }}>
				<svg
					width={RING_SIZE}
					height={RING_SIZE}
					viewBox={`0 0 ${RING_SIZE} ${RING_SIZE}`}
					aria-label={`${engagementRate}% findings reviewed`}
					role="img"
				>
					<title>{engagementRate}% engagement</title>
					{/* Background track */}
					<circle
						cx={RING_SIZE / 2}
						cy={RING_SIZE / 2}
						r={RADIUS}
						fill="none"
						stroke="currentColor"
						className="text-muted/30"
						strokeWidth={STROKE_WIDTH}
					/>
					{/* Progress arc */}
					<circle
						cx={RING_SIZE / 2}
						cy={RING_SIZE / 2}
						r={RADIUS}
						fill="none"
						stroke="currentColor"
						className="text-primary transition-all duration-500"
						strokeWidth={STROKE_WIDTH}
						strokeDasharray={CIRCUMFERENCE}
						strokeDashoffset={offset}
						strokeLinecap="round"
						transform={`rotate(-90 ${RING_SIZE / 2} ${RING_SIZE / 2})`}
					/>
				</svg>
				<span className="absolute inset-0 flex items-center justify-center text-sm font-semibold">
					{engagementRate}%
				</span>
			</div>

			{/* Stats */}
			<div className="flex flex-col gap-2 text-center sm:text-left">
				<span className="text-sm font-medium text-muted-foreground">
					{totalResponded} of {totalFindings} findings reviewed
				</span>
				<div className="flex flex-wrap justify-center sm:justify-start gap-3 text-sm">
					<StatPill
						label="Applied"
						count={engagement.applied}
						className="text-provider-success-foreground"
					/>
					<StatPill
						label="Disputed"
						count={engagement.disputed}
						className="text-provider-attention-foreground"
					/>
					<StatPill
						label="N/A"
						count={engagement.notApplicable}
						className="text-provider-muted-foreground"
					/>
				</div>
			</div>
		</Card>
	);
}

function StatPill({
	label,
	count,
	className,
}: {
	label: string;
	count: number;
	className?: string;
}) {
	return (
		<span className="flex items-center gap-1">
			<span className={className}>{count}</span>
			<span className="text-muted-foreground">{label}</span>
		</span>
	);
}
