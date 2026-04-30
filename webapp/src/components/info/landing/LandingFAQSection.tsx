import { Github } from "lucide-react";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Button } from "@/components/ui/button";

const faqItems = [
	{
		key: "faq-setup",
		q: "What does it take to set up?",
		a: "An admin connects Hephaestus to GitHub or GitLab. Contributors sign in. Once webhooks are wired up, comments appear on the next pull request alongside the existing review.",
	},
	{
		key: "faq-practices",
		q: "What does Hephaestus actually look at?",
		a: "Each project keeps a short list of the practices that matter — a clear pull-request description, an actionable review, a coherent commit history. The list is plain prose. The contribution and the list are read together.",
	},
	{
		key: "faq-different",
		q: "How does the advice differ for each contributor?",
		a: "The shape of the comment draws on the recent history visible to it. A first-time issue might get a worked example. A repeat issue gets a sharper note. Steady improvement gets a reflection question instead of another reminder.",
	},
	{
		key: "faq-mentor",
		q: "What can I do with the mentor?",
		a: "Open a thread when you want to think out loud — about what to do this week, what's blocking you, how a recent review went. The mentor has access to your recent activity.",
	},
	{
		key: "faq-data",
		q: "What does Hephaestus send to AI models?",
		a: "Each review runs in an isolated environment with the diff, surrounding code, and your project's practices. Admins choose the AI model provider and the repositories in scope. You can host everything yourself.",
	},
	{
		key: "faq-team-size",
		q: "Is it worth it for a small team?",
		a: "A three-person project and a forty-person team use the same setup. Pick a few practices, add more when you're ready.",
	},
];

export function LandingFAQSection() {
	return (
		<section
			id="faq"
			className="w-full py-20 md:py-32 bg-gradient-to-b from-background to-muted/30"
		>
			<div className="container max-w-3xl px-4 md:px-6 space-y-12">
				<h2 className="text-4xl font-semibold tracking-tight sm:text-5xl">Questions.</h2>

				<Accordion className="w-full">
					{faqItems.map((item, index) => (
						<AccordionItem key={item.key} value={`item-${index}`} className="border-b border-muted">
							<AccordionTrigger className="text-left text-lg font-medium py-5">
								{item.q}
							</AccordionTrigger>
							<AccordionContent className="text-muted-foreground text-base leading-relaxed pb-5">
								{item.a}
							</AccordionContent>
						</AccordionItem>
					))}
				</Accordion>

				<div className="text-base text-muted-foreground">
					Still curious?{" "}
					<Button
						variant="link"
						className="px-0 text-base"
						render={
							<a
								href="https://github.com/ls1intum/Hephaestus/discussions"
								target="_blank"
								rel="noopener noreferrer"
							>
								<Github className="h-4 w-4 mr-1" />
								Ask on GitHub Discussions
							</a>
						}
					/>
				</div>
			</div>
		</section>
	);
}
