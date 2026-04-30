import { MentorIcon } from "@/components/mentor/MentorIcon";
import { Badge } from "@/components/ui/badge";

export function AboutHeroSection() {
	return (
		<section className="text-center space-y-6">
			<div className="inline-flex items-center justify-center p-4 rounded-full bg-secondary text-primary">
				<MentorIcon size={48} pad={4} />
			</div>
			<Badge variant="outline">Open-source research project</Badge>
			<h1 className="text-4xl font-bold">
				About <span className="text-provider-done-foreground">Heph</span>aestus
			</h1>
			<p className="text-xl text-muted-foreground max-w-2xl mx-auto">
				Hephaestus turns review activity into practice-aware feedback — coaching contributors on the
				practices each project defines.
			</p>
		</section>
	);
}
