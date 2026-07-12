import { Code, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { FeatureCard, type FeatureData } from "./FeatureCard";

const FEATURES_DATA: FeatureData[] = [
	{
		icon: Code,
		badge: "Core feature",
		title: "Practice feedback on your work",
		description: "What was done well, what could be better, and a way to get there",
		content:
			"Hephaestus reviews pull requests and issues in your GitHub and GitLab repositories against real engineering practices and posts its feedback right where the work happens. You can act on it, push back with a reason, or let it pass.",
	},
	{
		icon: Sparkles,
		badge: "Core feature",
		title: "Heph, your AI mentor",
		description: "A mentor chat grounded in your repository activity",
		content:
			"Heph knows your recent issues, commits, reviews, and pull requests, so its answers start from your actual work. Ask it about your changes, reflect on your week, or get a suggestion for what to do next, in the app or in Slack.",
	},
];

export function AboutMissionSection() {
	return (
		<section className="space-y-12">
			<div>
				<Badge className="mb-4" variant="outline">
					Our purpose
				</Badge>
				<h2 className="text-3xl font-bold mb-6">The mission</h2>

				<div className="space-y-6">
					<p className="text-lg leading-relaxed">
						Good mentoring feedback helps developers grow, but few people ever get enough of it.
						Hephaestus carries the routine feedback no one has time to give everyone, grounded in
						the work you actually do. It supports mentors, teachers, and maintainers rather than
						replacing them.
					</p>

					<div className="border-l-4 border-primary pl-6 py-2">
						<p className="text-lg font-medium">
							We believe every developer deserves thoughtful feedback on their work and a clear way
							to get better.
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
