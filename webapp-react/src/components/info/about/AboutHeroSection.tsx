import { Hammer } from "lucide-react";

export function AboutHeroSection() {
	return (
		<section className="text-center space-y-6">
			<div className="inline-flex items-center justify-center p-4 rounded-full bg-secondary">
				<Hammer className="size-12 text-primary" />
			</div>
			<h1 className="text-4xl font-bold">About Hephaestus</h1>
			<p className="text-xl text-muted-foreground max-w-2xl mx-auto">
				Named after the Greek craftsman of the gods, we forge powerful tools
				that transform how software teams collaborate, learn, and excel
				together.
			</p>
		</section>
	);
}
