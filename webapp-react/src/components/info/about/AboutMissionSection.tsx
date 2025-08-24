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
		title: "AI-Powered Mentorship",
		description: "Your team's personalized guide",
		content:
			"Receive contextual guidance through Heph—our AI mentor—that provides personalized feedback, identifies growth opportunities, and helps team members develop their skills with practical insights that lead to measurable improvement.",
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
						We empower novice software engineers and foster sustainable,
						collaborative development practices through smart gamification and
						AI guidance that adapts to your team's unique challenges.
					</p>

					<div className="border-l-4 border-primary pl-6 py-2">
						<p className="text-lg font-medium">
							We believe the best software isn't just about writing code — it's
							about building teams that bring out the best in each other.
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
