import { MentorIcon } from "@/components/mentor/MentorIcon";

export function AboutHeroSection() {
	return (
		<section className="text-center space-y-6">
			<div className="inline-flex items-center justify-center p-4 rounded-full bg-secondary text-primary">
				<MentorIcon size={48} pad={4} />
			</div>
			<h1 className="text-4xl font-bold">About Hephaestus</h1>
			<p className="text-xl text-muted-foreground max-w-2xl mx-auto">
				Hephaestus helps teams give more developers useful feedback on the engineering practices
				they use in project work. Heph is the conversational AI mentor for talking it through.
			</p>
		</section>
	);
}
