import { Github } from "@/components/icons/brand";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

const faqItems = [
	{
		key: "faq-item-1",
		q: "How does Hephaestus integrate with our existing workflow?",
		a: "Hephaestus connects to your GitHub or GitLab repositories and reads the activity that is already there. Setup is a guided configuration in your workspace settings.",
	},
	{
		key: "faq-item-2",
		q: "Is Hephaestus suitable for small teams?",
		a: "Yes. Hephaestus works for teams of any size, from small student projects to larger development teams and open-source projects.",
	},
	{
		key: "faq-item-3",
		q: "How does Heph work?",
		a: "Heph reads your pull requests, issues, and reviews, then names your strongest habit and the one change that would help most. You can also chat with it about your work, in the app or in Slack.",
	},
	{
		key: "faq-item-4",
		q: "Do we need to change how we work?",
		a: "No. Hephaestus works alongside your existing GitHub or GitLab workflow without requiring any changes to how your team uses pull requests, reviews, or issues.",
	},
];

export function LandingFAQSection() {
	return (
		<section id="faq" className="w-full py-8 md:py-16 bg-gradient-to-b from-background to-muted/30">
			<div className="container px-4 md:px-6">
				<div className="mb-10 text-center max-w-3xl mx-auto">
					<Badge className="mb-4" variant="outline">
						FAQ
					</Badge>
					<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl mb-4">
						Frequently asked questions
					</h2>
				</div>

				<div className="max-w-3xl mx-auto">
					<Accordion className="w-full">
						{faqItems.map((item, index) => (
							<AccordionItem
								key={item.key}
								value={`item-${index}`}
								className="border-b border-muted"
							>
								<AccordionTrigger className="text-left font-medium">{item.q}</AccordionTrigger>
								<AccordionContent className="text-muted-foreground">{item.a}</AccordionContent>
							</AccordionItem>
						))}
					</Accordion>

					<div className="mt-8 p-6 bg-muted/50 border border-muted rounded-lg text-center">
						<p className="mb-4">Have more questions?</p>
						<Button
							variant="outline"
							render={
								<a
									href="https://github.com/ls1intum/Hephaestus/discussions"
									target="_blank"
									rel="noopener noreferrer"
									className="gap-2"
								/>
							}
						>
							<Github className="h-4 w-4" />
							<span>Ask the community</span>
						</Button>
					</div>
				</div>
			</div>
		</section>
	);
}
