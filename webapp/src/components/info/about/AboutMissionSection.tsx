import { MessageCircle, ScanSearch, TrendingUp } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { FeatureCard, type FeatureData } from "./FeatureCard";

const FEATURES_DATA: FeatureData[] = [
	{
		icon: ScanSearch,
		badge: "Detect",
		title: "Practice Detection",
		description: "Identify what's working and what's not — before it becomes habit",
		content:
			"Analyzes how your team works — across pull requests, reviews, and commits — to catch bad practices before they become habits. Early work gets coaching. Finished work gets rigor.",
	},
	{
		icon: MessageCircle,
		badge: "Guide",
		title: "Multi-Channel Guidance",
		description: "Feedback via AI mentor, Slack, and in-app notifications",
		content:
			"When a practice is detected, feedback reaches the contributor via AI mentor conversations, Slack notifications, or achievement unlocks — driven by actual project activity.",
	},
	{
		icon: TrendingUp,
		badge: "Grow",
		title: "Growth Tracking",
		description: "See development trajectories, not just snapshots",
		content:
			"Achievements and a league system track skill development over time. As practices improve, coaching fades — the goal is independence, not dependence on the tool.",
	},
];

export function AboutMissionSection() {
	return (
		<section className="space-y-12">
			<div>
				<Badge className="mb-4" variant="outline">
					What Hephaestus Does
				</Badge>
				<h2 className="text-3xl font-bold mb-6">Purpose</h2>

				<div className="space-y-6">
					<p className="text-lg leading-relaxed">
						Most developer analytics tools report to managers. Hephaestus sends feedback directly to
						individual contributors — analyzing pull requests, reviews, and collaboration patterns
						to flag what needs attention and coach improvement.
					</p>

					<div className="border-l-4 border-primary pl-6 py-2">
						<p className="text-lg font-medium">
							Feedback should reach the person who can act on it, at the moment they can act on it —
							not aggregated into a quarterly report for their manager.
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
