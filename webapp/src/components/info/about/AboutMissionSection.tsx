import { Code, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { FeatureCard, type FeatureData } from "./FeatureCard";

const FEATURES_DATA: FeatureData[] = [
	{
		icon: Code,
		badge: "Core feature",
		title: "Practice feedback",
		description: "Specific feedback on how the work was done",
		content:
			"Hephaestus reviews pull requests, merge requests, and issues against the practices a workspace has chosen. Each piece of feedback points to evidence in the work and suggests what to try next.",
	},
	{
		icon: Sparkles,
		badge: "Core feature",
		title: "Chat with Heph",
		description: "Talk through feedback and recent work",
		content:
			"Heph can use recent project activity, delivered feedback, and selected Slack messages or Outline documents as context. Developers can chat in the web app or, when connected, in Slack.",
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
						Developers learn engineering practices by doing the work and getting feedback on it.
						Expert attention is limited, so many contributors get little or none. Hephaestus makes
						routine practice feedback available more consistently while leaving human judgement and
						mentoring relationships to people.
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
