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
		a: "Hephaestus connects to GitHub or GitLab via webhooks. An admin installs the GitHub App (or configures the GitLab integration), contributors sign in, and findings appear on existing pull requests. No workflow changes required, and no new place contributors have to remember to check.",
	},
	{
		key: "faq-good-practice",
		q: "What makes a good practice?",
		a: "A practice is a named behavioural pattern with a detection prompt — for example, 'pull request descriptions explain motivation and tradeoffs' or 'reviewers leave actionable change requests, not preferences'. Practices are versioned per workspace, so each project encodes the standards that matter to it. The clearer the prompt, the more reliable the agent's findings.",
	},
	{
		key: "faq-no-leaderboard",
		q: "Why isn't there a contributor ranking on the front page?",
		a: "Self-level feedback (rating the worker) and aggregate scoring consistently underperform task- and process-level feedback in the formative-feedback literature. Hephaestus is designed against that model: findings target the practice and the action, never the contributor's worth. Workspaces that want a weekly activity recognition surface can opt into one — but it is never the headline.",
	},
	{
		key: "faq-not-a-coding-agent",
		q: "Is Hephaestus a coding agent or a Cursor competitor?",
		a: "No. The conversational mentor is a reflection partner — it asks what's blocking you, surfaces your own activity, and helps you plan. It does not write code, merge branches, or take actions on your behalf. Code review and synthesis tools serve a different need.",
	},
	{
		key: "faq-not-a-review-bot",
		q: "Is this a code review bot like CodeRabbit or Greptile?",
		a: "Different goal. Diff-level review bots catch defects in the patch. Hephaestus reads the full pull-request lifecycle and produces practice findings — code-level signals are evidence, not the goal. The two stack cleanly: keep your defect bot, and add practice-aware coaching on top.",
	},
	{
		key: "faq-surveillance",
		q: "How do you avoid creating a surveillance environment?",
		a: "Channels are scoped asymmetrically. The in-context channel addresses authors only. Reviewer-side practices appear only on private reflection surfaces. Findings are about the work, not the worker. Public ranking is not a default product feature.",
	},
	{
		key: "faq-data",
		q: "What data does Hephaestus send to LLM providers?",
		a: "Practice detection runs in a sandboxed Docker container with access to the contribution diff, surrounding repository context, and the active practice catalog. Workspace admins control the LLM provider, resource limits, and which repositories are in scope. Self-hosting is supported.",
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
