import { MentorIcon } from "@/components/mentor/MentorIcon";

export function AboutHeroSection() {
	return (
		<section className="text-center space-y-6">
			<div className="inline-flex items-center justify-center p-4 rounded-full bg-secondary text-primary">
				<MentorIcon size={48} pad={4} />
			</div>
			<h1 className="text-4xl font-bold">
				About <span className="text-provider-done-foreground">Heph</span>aestus
			</h1>
			<p className="text-xl text-muted-foreground max-w-2xl mx-auto">
				Formative practice analytics for project teams. Hephaestus observes how participants
				collaborate — from issues to pull requests to code reviews — detects behavioral patterns,
				and guides improvement through AI mentoring and gamification.
			</p>
		</section>
	);
}
