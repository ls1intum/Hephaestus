import { MessageCircle, ScanSearch, TrendingUp } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { FeatureCard, type FeatureData } from "./FeatureCard";

const FEATURES_DATA: FeatureData[] = [
	{
		icon: ScanSearch,
		badge: "Detect",
		title: "Practice Detection",
		description: "Evaluate contributions against your project's practice catalog",
		content:
			"An AI agent evaluates each contribution against workspace-defined practices. Findings include a verdict, severity, evidence, and tailored guidance. Contributors can mark findings as applied, disputed, or not applicable.",
	},
	{
		icon: MessageCircle,
		badge: "Guide",
		title: "Adaptive Coaching",
		description: "Guidance adapts to each contributor's track record",
		content:
			"The system tracks each contributor's history per practice and instructs the agent to adapt accordingly. New contributors are guided toward concrete examples; repeat issues prompt direct coaching; improving contributors get prompts for reflection. Heph, the AI mentor, complements in-context findings with goal-setting and reflection.",
	},
	{
		icon: TrendingUp,
		badge: "Grow",
		title: "Engagement & Recognition",
		description: "Surface contribution activity over time",
		content:
			"A weekly leaderboard, leagues, and achievements surface contribution activity over time. Weekly Slack digests highlight standout contributors. Practice-aware recognition is on the roadmap.",
	},
];

export function AboutMissionSection() {
	return (
		<section className="space-y-12">
			<div>
				<Badge className="mb-4" variant="outline">
					What Hephaestus Does
				</Badge>
				<h2 className="text-3xl font-bold mb-6">Practice-Aware Guidance</h2>

				<p className="text-lg leading-relaxed">
					You define a practice catalog per workspace. Hephaestus evaluates every contribution
					against those practices and delivers guidance directly to the contributor — adapted based
					on their track record with each practice.
				</p>
			</div>

			<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
				{FEATURES_DATA.map((feature) => (
					<FeatureCard key={feature.title} feature={feature} />
				))}
			</div>
		</section>
	);
}
