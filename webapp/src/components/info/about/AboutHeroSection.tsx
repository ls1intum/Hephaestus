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
				Hephaestus gives developers the mentoring feedback they would otherwise only get from a
				mentor, so that every developer gets some. Heph, your AI mentor, grounds its guidance in
				your actual work across issues, commits, reviews, and pull requests.
			</p>
		</section>
	);
}
