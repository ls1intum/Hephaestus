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
		description: "Coaching at the right time, through the right channel",
		content:
			"When a practice is detected, guidance reaches the right person through the right channel — AI mentoring, notifications, or achievement unlocks. All grounded in real project activity, not assumptions.",
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
					Our Purpose
				</Badge>
				<h2 className="text-3xl font-bold mb-6">The Mission</h2>

				<div className="space-y-6">
					<p className="text-lg leading-relaxed">
						We help teams develop professional practices by observing how they collaborate,
						detecting what's working and what needs attention, and delivering targeted guidance
						through multiple channels — all grounded in real project activity, not assumptions.
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
