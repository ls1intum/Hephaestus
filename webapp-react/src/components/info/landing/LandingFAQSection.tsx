import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Github } from "lucide-react";

const faqItems = [
	{
		key: "faq-item-1",
		q: "How does Hephaestus integrate with our existing workflow?",
		a: "Hephaestus integrates with GitHub, providing insights without disrupting your current processes. Setup is simple with our guided configuration.",
	},
	{
		key: "faq-item-2",
		q: "Is Hephaestus suitable for small teams?",
		a: "Yes! Hephaestus is built with flexibility in mind and works well for teams of any size, from small student projects to larger development teams.",
	},
	{
		key: "faq-item-3",
		q: "How does the AI Mentor work?",
		a: "The AI Mentor analyzes your GitHub activity and reflection inputs to provide personalized guidance, helping team members set goals and track their progress.",
	},
	{
		key: "faq-item-4",
		q: "Do we need to change how we use GitHub?",
		a: "No, Hephaestus works alongside your existing GitHub workflow without requiring any changes to how your team uses pull requests, reviews, or issues.",
	},
];

export function LandingFAQSection() {
	return (
		<section
			id="faq"
			className="w-full py-8 md:py-16 bg-gradient-to-b from-background to-muted/30"
		>
			<div className="container px-4 md:px-6">
				<div className="mb-10 text-center max-w-3xl mx-auto">
					<Badge className="mb-4" variant="outline">
						FAQ
					</Badge>
					<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl mb-4">
						Frequently Asked Questions
					</h2>
				</div>

				<div className="max-w-3xl mx-auto">
					<Accordion type="single" collapsible className="w-full">
						{faqItems.map((item, index) => (
							<AccordionItem
								key={item.key}
								value={`item-${index}`}
								className="border-b border-muted"
							>
								<AccordionTrigger className="text-left font-medium">
									{item.q}
								</AccordionTrigger>
								<AccordionContent className="text-muted-foreground">
									{item.a}
								</AccordionContent>
							</AccordionItem>
						))}
					</Accordion>

					<div className="mt-8 p-6 bg-muted/50 border border-muted rounded-lg text-center">
						<p className="mb-4">Have more questions?</p>
						<Button variant="outline" asChild>
							<a
								href="https://github.com/ls1intum/Hephaestus/discussions"
								target="_blank"
								rel="noopener noreferrer"
								className="gap-2"
							>
								<Github className="h-4 w-4" />
								<span>Ask the Community</span>
							</a>
						</Button>
					</div>
				</div>
			</div>
		</section>
	);
}
