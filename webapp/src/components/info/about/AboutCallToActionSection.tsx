import { Github } from "@/components/icons/brand";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

export function AboutCallToActionSection() {
	return (
		<section className="mt-20 mb-8 bg-gradient-to-br from-background to-muted/30 rounded-lg p-8 text-center border border-muted/50">
			<Badge className="mb-4" variant="outline">
				Get involved
			</Badge>
			<h2 className="text-3xl font-bold mb-4">Contribute to Hephaestus</h2>
			<p className="text-muted-foreground max-w-2xl mx-auto mb-8">
				Fix a bug, improve the documentation, or propose a feature. Start with the repository or
				read the contributor guide.
			</p>
			<div className="flex flex-col sm:flex-row gap-4 justify-center">
				<Button
					variant="default"
					size="lg"
					render={
						<a
							href="https://github.com/ls1intum/Hephaestus"
							target="_blank"
							rel="noopener noreferrer"
							className="inline-flex items-center gap-2"
						/>
					}
				>
					<Github className="h-4 w-4" />
					<span>View on GitHub</span>
				</Button>
				<Button
					variant="outline"
					size="lg"
					render={
						<a
							href="https://ls1intum.github.io/Hephaestus/"
							target="_blank"
							rel="noopener noreferrer"
						/>
					}
				>
					<span>Read the contributor guide</span>
				</Button>
			</div>
		</section>
	);
}
