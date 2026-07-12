import { Code, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { FeatureCard, type FeatureData } from "./FeatureCard";

const FEATURES_DATA: FeatureData[] = [
	{
		icon: Code,
		badge: "Core Feature",
		title: "Practice Feedback",
		description: "Turning technical work into personal growth",
		content:
			"Transform code reviews into personal, practice-by-practice feedback on each developer's own work, with a mentoring overview across the workspace. It highlights strengths and what to work on, so everyone can see where to grow next.",
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
						We help developers onboard and grow by combining practice-focused feedback with
						process-aware AI mentoring. Our guidance is grounded in your actual repository activity
						and supports self-regulated learning.
					</p>

					<div className="border-l-4 border-primary pl-6 py-2">
						<p className="text-lg font-medium">
							We believe the best software isn't just about code — it's about building teams that
							practice healthy habits and continuous reflection.
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
