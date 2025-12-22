import { Github } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

export function AboutCallToActionSection() {
	return (
		<section className="mt-20 mb-8 bg-gradient-to-br from-background to-muted/30 rounded-lg p-8 text-center border border-muted/50">
			<Badge className="mb-4" variant="outline">
				Get Involved
			</Badge>
			<h2 className="text-3xl font-bold mb-4">Ready to Forge Something Great?</h2>
			<p className="text-muted-foreground max-w-2xl mx-auto mb-8">
				Hephaestus thrives on community contributions from developers of all skill levels. Whether
				you're fixing bugs, adding features, or improving documentation, your expertise helps shape
				our platform.
			</p>
			<div className="flex flex-col sm:flex-row gap-4 justify-center">
				<Button asChild variant="default" size="lg">
					<a
						href="https://github.com/ls1intum/Hephaestus"
						target="_blank"
						rel="noopener noreferrer"
						className="inline-flex items-center gap-2"
					>
						<Github className="h-4 w-4" />
						<span>GitHub Repository</span>
					</a>
				</Button>
				<Button asChild variant="outline" size="lg">
					<a
						href="https://ls1intum.github.io/Hephaestus/"
						target="_blank"
						rel="noopener noreferrer"
					>
						<span>View Documentation</span>
					</a>
				</Button>
			</div>
		</section>
	);
}
