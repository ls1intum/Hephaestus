import { Hammer } from "lucide-react";
import { MentorIcon } from "@/components/mentor/MentorIcon";
import { Badge } from "@/components/ui/badge";
import { FeatureCard, type FeatureData } from "./FeatureCard";

const FEATURES_DATA: FeatureData[] = [
	{
		icon: Hammer,
		badge: "Core feature",
		title: "Practice feedback",
		description: "Specific feedback on how the work was done",
		content:
			"Hephaestus reviews eligible project work against the practices a workspace has chosen. Today, that includes pull requests, merge requests, and issues. Each piece of feedback points to evidence and suggests what to try next.",
	},
	{
		icon: MentorIcon,
		badge: "Core feature",
		title: "Chat with Heph",
		description: "Talk through feedback and recent work",
		content:
			"Heph can use recent project activity, feedback the developer has received, and selected Slack messages or Outline documents as context. Developers can chat in the web app or, when connected, in Slack.",
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
						Developers learn engineering practices by doing the work and getting feedback. Expert
						attention is limited, so feedback is often uneven or missing. Hephaestus provides
						routine practice feedback without replacing human judgement or mentoring relationships.
					</p>

					<div className="border-l-4 border-primary pl-6 py-2">
						<p className="text-lg font-medium">
							Every developer should be able to see what worked, what could improve, and what to try
							next.
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
