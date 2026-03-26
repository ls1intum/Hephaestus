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
			"An AI agent evaluates each PR against workspace-defined practices. Findings include a verdict, severity, evidence, and tailored guidance. Contributors can mark findings as applied, disputed, or not applicable.",
	},
	{
		icon: MessageCircle,
		badge: "Guide",
		title: "Adaptive Coaching",
		description: "Guidance adapts to each contributor's track record",
		content:
			"The system tracks each contributor's history per practice. New contributors get concrete examples. Repeat issues get direct coaching. As competence grows, guidance fades toward reflection.",
	},
	{
		icon: TrendingUp,
		badge: "Grow",
		title: "Engagement & Recognition",
		description: "Make good practices visible across the team",
		content:
			"Leaderboards, leagues, and achievements track engagement over time. Weekly Slack digests highlight standout contributors. The AI mentor (Heph) supports reflection and goal-setting.",
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

				<div className="space-y-6">
					<p className="text-lg leading-relaxed">
						You define a catalog of engineering practices per workspace — what good collaboration
						looks like for your project. Hephaestus evaluates every contribution against those
						practices and delivers feedback directly to the contributor, not their manager.
					</p>

					<div className="border-l-4 border-primary pl-6 py-2">
						<p className="text-lg font-medium">
							Guidance adapts to each person's track record: new contributors get concrete examples,
							repeat issues get direct coaching, and improving contributors get prompts for
							reflection.
						</p>
					</div>
				</div>
			</div>

			<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
				{FEATURES_DATA.map((feature) => (
					<FeatureCard key={feature.title} feature={feature} />
				))}
			</div>
		</section>
	);
}
