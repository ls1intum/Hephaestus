import { ArrowRight, MessageCircle, Radar, ScrollText } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

interface PipelineStage {
	verb: string;
	icon: React.ComponentType<{ className?: string }>;
	title: string;
	description: string;
	bullets: string[];
}

const STAGES: PipelineStage[] = [
	{
		verb: "Detect",
		icon: Radar,
		title: "Practice catalog + lifecycle detection",
		description: "Define the practices that matter. Detect them across the full PR lifecycle.",
		bullets: [
			"Workspace-defined practice catalog — versioned and inspectable, not a vendor prompt",
			"Detection reads descriptions, commits, review threads, related issues, and contributor history",
			"Findings include verdict, severity, evidence, and a recommended action",
		],
	},
	{
		verb: "Coach",
		icon: MessageCircle,
		title: "Adaptive guidance, in context and in conversation",
		description: "Findings adapt to each contributor's history per practice.",
		bullets: [
			"In-context channel: PR/MR comments and inline diff notes for the author",
			"Conversational mentor: a reflection partner — not a coding agent — for goal-setting and check-ins",
			"Tone shifts with the contributor's track record: examples for newcomers, direct coaching for repeats",
		],
	},
	{
		verb: "Reflect",
		icon: ScrollText,
		title: "Private dashboards, never public ranking",
		description:
			"Reflection surfaces show patterns over time — for the contributor and the facilitator.",
		bullets: [
			"Reflection dashboard: a contributor's own findings and practice history, scoped privately",
			"Facilitator dashboard: aggregate practice signals to support coaching, not grading",
			"Optional weekly activity recognition for workspaces that want it — opt-in, never the headline",
		],
	},
];

export function LandingFeaturesSection() {
	return (
		<section id="features" className="w-full py-12 md:py-24 bg-background">
			<div className="container px-4 md:px-6">
				<div className="mb-12 text-center max-w-3xl mx-auto">
					<Badge className="mb-4" variant="outline">
						The Practice-Aware Loop
					</Badge>
					<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl mb-4">
						Detect → Coach → Reflect
					</h2>
					<p className="text-muted-foreground text-lg">
						Three stages, four channels, one closed loop. Activity becomes findings; findings reach
						people through the channel that fits the moment; reactions feed back into detection.
					</p>
				</div>

				<div className="grid gap-6 md:grid-cols-3">
					{STAGES.map((stage, index) => (
						<PipelineCard key={stage.verb} stage={stage} isLast={index === STAGES.length - 1} />
					))}
				</div>
			</div>
		</section>
	);
}

function PipelineCard({ stage, isLast }: { stage: PipelineStage; isLast: boolean }) {
	const Icon = stage.icon;
	return (
		<div className="relative">
			<Card className="h-full">
				<CardHeader>
					<div className="flex items-center gap-2 mb-2">
						<Icon className="h-5 w-5 text-primary" />
						<Badge variant="secondary" className="font-mono text-[10px]">
							{stage.verb}
						</Badge>
					</div>
					<CardTitle className="text-lg">{stage.title}</CardTitle>
					<CardDescription>{stage.description}</CardDescription>
				</CardHeader>
				<CardContent>
					<ul className="space-y-2 text-sm">
						{stage.bullets.map((bullet) => (
							<li key={bullet} className="flex gap-2">
								<span className="text-muted-foreground mt-0.5">—</span>
								<span>{bullet}</span>
							</li>
						))}
					</ul>
				</CardContent>
			</Card>
			{!isLast && (
				<ArrowRight
					aria-hidden
					className="hidden md:block absolute -right-4 top-12 h-6 w-6 text-muted-foreground/40"
				/>
			)}
		</div>
	);
}
