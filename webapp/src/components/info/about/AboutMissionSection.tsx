import { Code, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { FeatureCard, type FeatureData } from "./FeatureCard";

const FEATURES_DATA: FeatureData[] = [
	{
		icon: Code,
		badge: "Core Feature",
		title: "Code Review Gamification",
		description: "Turning technical work into team growth",
		content:
			"Transform code reviews into engaging experiences with dynamic leaderboards, team competitions, and a structured league system that recognizes excellence and encourages participation from developers at all skill levels.",
	},
	{
		icon: Sparkles,
		badge: "Core Feature",
		title: "Process-Aware AI Mentoring",
		description: "Guidance grounded in your repo activity",
		content:
			"Heph — our AI mentor — delivers personalized, data-informed feedback based on issues, commits, reviews, and pull requests. It supports self-regulated learning with goal setting, reflection, and practical next steps.",
	},
];

export function AboutMissionSection() {
	return (
		<section className="space-y-12">
			<div>
				<Badge className="mb-4" variant="outline">
					Our Purpose
				</Badge>
				<h2 className="text-3xl font-bold mb-6">The Mission</h2>

				<div className="space-y-6">
					<p className="text-lg leading-relaxed">
						We help novice developers onboard and grow in agile software teams
						by combining smart gamification with process-aware AI mentoring. Our
						guidance is grounded in your actual repository activity and supports
						self-regulated learning.
					</p>

					<div className="border-l-4 border-primary pl-6 py-2">
						<p className="text-lg font-medium">
							We believe the best software isn't just about code — it's about
							building teams that practice healthy habits and continuous
							reflection.
						</p>
					</div>
				</div>
			</div>

			<div className="grid grid-cols-1 md:grid-cols-2 gap-8">
				{FEATURES_DATA.map((feature) => (
					<FeatureCard key={feature.title} feature={feature} />
				))}
			</div>
		</section>
	);
}
