import { Github } from "lucide-react";
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
		key: "faq-integration",
		q: "How does Hephaestus integrate with our existing workflow?",
		a: "An admin installs the GitHub App (or configures the GitLab integration), contributors sign in, and findings appear on existing pull requests. No workflow changes required, and no new place contributors have to remember to check.",
	},
	{
		key: "faq-good-practice",
		q: "What makes a good practice?",
		a: "A practice is a named behavioural pattern with a detection prompt — for example, 'pull request descriptions explain motivation and tradeoffs' or 'reviewers leave actionable change requests, not preferences'. Practices are versioned per workspace, so each project encodes the standards that matter to it. The clearer the prompt, the more reliable the agent's findings.",
	},
	{
		key: "faq-adaptive",
		q: "How does adaptive coaching work?",
		a: "Each contributor has a track record per practice. The detection agent receives that history alongside the contribution and adapts: concrete examples for newcomers, direct coaching for repeat issues, reflection prompts as people improve. The conversational mentor complements in-context findings with goal-setting and reflection grounded in actual activity.",
	},
	{
		key: "faq-channels",
		q: "Where do findings actually appear?",
		a: "In-context: as PR/MR comments and inline diff notes for the author. On the reflection dashboard: a contributor's own findings and practice history, scoped privately. In conversation: with the mentor, where the contributor can articulate and reflect. For educators: on the facilitator dashboard, alongside aggregate practice signals.",
	},
	{
		key: "faq-data",
		q: "What data does Hephaestus send to LLM providers?",
		a: "Practice detection runs in a sandboxed Docker container with access to the contribution diff, surrounding repository context, and the active practice catalog. Workspace admins control the LLM provider, resource limits, and which repositories are in scope. Self-hosting is supported.",
	},
	{
		key: "faq-team-size",
		q: "Is Hephaestus suitable for small teams?",
		a: "Yes — a three-person student project and a forty-person engineering team use the same workspace model. Define as many or as few practices as you need.",
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
						Frequently Asked Questions
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
							<span>Ask the Community</span>
						</Button>
					</div>
				</div>
			</div>
		</section>
	);
}
