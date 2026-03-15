import { Code, ScanSearch, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { FeatureCard, type FeatureData } from "./FeatureCard";

const FEATURES_DATA: FeatureData[] = [
	{
		icon: Code,
		badge: "Core Feature",
		title: "Code Review Gamification",
		description: "Making good practices visible through recognition",
		content:
			"Leaderboards, team competitions, and a progression system that recognize meaningful review contributions — not just volume. Designed to sustain engagement while modeling the collaborative practices that matter most.",
	},
	{
		icon: Sparkles,
		badge: "Core Feature",
		title: "AI Mentor",
		description: "Personalized coaching grounded in your project activity",
		content:
			"Heph — our AI mentor — delivers formative feedback drawn from issues, commits, reviews, and pull requests. Each session guides you through goal-setting, progress review, and reflection — building self-regulation skills alongside technical ones.",
	},
	{
		icon: ScanSearch,
		badge: "Core Feature",
		title: "Practice Detection",
		description: "Identifying what's working and what needs attention",
		content:
			"AI-powered analysis of pull requests identifies anti-patterns like missing descriptions and oversized changes. Feedback adapts to context — draft PRs receive supportive coaching while ready-to-merge work gets rigorous review.",
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
						We help teams develop professional practices by observing how they collaborate,
						detecting what's working and what needs attention, and coaching improvement through AI
						mentoring and gamification — all grounded in real project activity.
					</p>

					<div className="border-l-4 border-primary pl-6 py-2">
						<p className="text-lg font-medium">
							We believe the best work isn't just about the output — it's about how people learn to
							collaborate, reflect, and grow through the process of building together.
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
