import { Github } from "@/components/icons/brand";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";

export function AboutCallToActionSection() {
	return (
		<section className="mt-20 mb-8 rounded-lg border border-muted/50 bg-gradient-to-br from-background to-muted/30 p-6 text-center sm:p-8">
			<Badge className="mb-4" variant="outline">
				Get involved
			</Badge>
			<h2 className="text-3xl font-bold mb-4">Contribute to Hephaestus</h2>
			<p className="text-muted-foreground max-w-2xl mx-auto mb-8">
				Fix a bug, improve the documentation, or propose a feature. Start with the repository or
				read the contributor guide.
			</p>
			<div className="flex flex-col sm:flex-row gap-4 justify-center">
				<a
					href="https://github.com/ls1intum/Hephaestus"
					target="_blank"
					rel="noopener noreferrer"
					className={buttonVariants({ size: "lg" })}
				>
					<Github className="h-4 w-4" />
					<span>View on GitHub</span>
				</a>
				<a
					href="https://ls1intum.github.io/Hephaestus/"
					target="_blank"
					rel="noopener noreferrer"
					className={buttonVariants({ variant: "outline", size: "lg" })}
				>
					<span>Read the contributor guide</span>
				</a>
			</div>
		</section>
	);
}
