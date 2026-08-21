import { Github } from "@/components/icons/brand";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";

const faqItems = [
	{
		key: "faq-item-1",
		q: "What project context can Hephaestus use?",
		a: "It can use activity from connected GitHub or GitLab repositories. Workspace admins can also add selected Slack channels and Outline documents. You keep working in the same tools.",
	},
	{
		key: "faq-item-2",
		q: "What is practice feedback?",
		a: "It is feedback on an observable way of working, such as keeping a change reviewable or explaining why it is needed. Practice feedback is tied to evidence in the work and can suggest a next step.",
	},
	{
		key: "faq-item-3",
		q: "What can I discuss with Heph?",
		a: "Ask about feedback or recent work, or use the conversation to reflect on what to try next. Heph can use recent project activity, feedback you have received, and selected Slack messages or Outline documents as context.",
	},
	{
		key: "faq-item-4",
		q: "Do I have to follow the feedback?",
		a: "No. Practice feedback is advisory. You decide what to use, question, or skip.",
	},
];

export function LandingFaqSection() {
	return (
		<section id="faq" className="w-full py-8 md:py-16 bg-gradient-to-b from-background to-muted/30">
			<div className="mx-auto w-full max-w-7xl px-4 md:px-6">
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
						<a
							href="https://github.com/ls1intum/Hephaestus/discussions"
							target="_blank"
							rel="noopener noreferrer"
							className={buttonVariants({ variant: "outline" })}
						>
							<Github className="h-4 w-4" />
							<span>Ask the community</span>
						</a>
					</div>
				</div>
			</div>
		</section>
	);
}
