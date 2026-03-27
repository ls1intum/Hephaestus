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
				A research project from TUM's Applied Education Technologies group, building practice-aware
				guidance for software projects. Grounded in Cognitive Apprenticeship theory.
			</p>
		</section>
	);
}
