import { ArrowRight, ChevronDown, FileText, MessageSquareText, Quote } from "lucide-react";
import { GitHubSignInButton } from "@/components/auth/GitHubSignInButton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface LandingHeroSectionProps {
	onSignIn: () => void;
	onGoToDashboard?: () => void;
	isSignedIn: boolean;
	onLearnMoreClick: () => void;
}

export function LandingHeroSection({
	onSignIn,
	onGoToDashboard,
	isSignedIn,
	onLearnMoreClick,
}: LandingHeroSectionProps) {
	return (
		<section className="w-full bg-gradient-to-b from-background to-muted/30 pt-8 md:pt-16 lg:pt-24 text-foreground">
			<div className="container mx-auto px-4 md:px-6 mb-12">
				<div className="flex flex-col items-center space-y-8 text-center">
					<div className="space-y-4 max-w-3xl">
						<h1 className="text-4xl font-bold tracking-tighter sm:text-5xl md:text-6xl">
							Practice-aware feedback for every contribution.
						</h1>
						<p className="mx-auto max-w-[720px] text-xl text-muted-foreground">
							Define the practices that matter for your project. Hephaestus reads the full
							pull-request lifecycle, evaluates each contribution against your catalog, and delivers
							findings — with evidence and an action — to the contributor.
						</p>
					</div>
					<div className="flex flex-col items-center gap-4 sm:flex-row sm:gap-6">
						{isSignedIn ? (
							<Button onClick={onGoToDashboard} size="lg" className="gap-2">
								Go to Dashboard <ArrowRight className="h-4 w-4" />
							</Button>
						) : (
							<GitHubSignInButton
								onClick={onSignIn}
								size="lg"
								className="w-full justify-center sm:w-auto"
							/>
						)}
						<Button variant="outline" size="lg" onClick={onLearnMoreClick} className="gap-2">
							Learn more <ChevronDown className="h-4 w-4" />
						</Button>
					</div>
				</div>
			</div>

			{/* Sample finding — the unit of value */}
			<div className="mx-auto max-w-3xl px-4 md:px-6">
				<SampleFindingCard />
			</div>
		</section>
	);
}

function SampleFindingCard() {
	return (
		<Card className="shadow-xl border-muted -mb-6 overflow-hidden">
			<CardHeader className="pb-3">
				<div className="flex items-center justify-between gap-3">
					<div className="flex items-center gap-2 min-w-0">
						<FileText className="h-4 w-4 shrink-0 text-muted-foreground" />
						<span className="truncate text-sm font-medium text-muted-foreground">
							feat/add-rate-limiter — pull request #142
						</span>
					</div>
					<div className="flex items-center gap-2 shrink-0">
						<Badge variant="secondary" className="font-mono text-[10px]">
							in-context
						</Badge>
						<SeverityPill level="needs-improvement" />
					</div>
				</div>
				<CardTitle className="text-lg">
					Practice: <span className="text-primary">Justifies changes with evidence</span>
				</CardTitle>
			</CardHeader>
			<CardContent className="space-y-4 text-sm">
				<FindingRow icon={Quote} label="Evidence">
					Pull-request description reads <em>"fixes the bug"</em> with no link to an issue, no
					reproduction, and no rationale for the chosen approach. Two reviewers asked for context in
					the thread.
				</FindingRow>
				<FindingRow icon={MessageSquareText} label="Recommended action">
					Add a one-paragraph <em>"Why this change?"</em> section: what failure mode you observed,
					what reproduces it, and why the chosen fix addresses the cause rather than its symptom.
				</FindingRow>
				<div className="flex flex-wrap items-center gap-2 pt-2 border-t">
					<span className="text-xs uppercase tracking-wide text-muted-foreground">React</span>
					<Badge variant="outline" className="font-mono text-[10px]">
						confirm
					</Badge>
					<Badge variant="outline" className="font-mono text-[10px]">
						dispute
					</Badge>
					<Badge variant="outline" className="font-mono text-[10px]">
						not applicable
					</Badge>
				</div>
			</CardContent>
		</Card>
	);
}

function SeverityPill({ level }: { level: "looks-good" | "minor" | "needs-improvement" }) {
	const styles = {
		"looks-good": "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
		minor: "bg-amber-500/10 text-amber-700 dark:text-amber-300",
		"needs-improvement": "bg-orange-500/10 text-orange-700 dark:text-orange-400",
	};
	const labels = {
		"looks-good": "Looks good",
		minor: "Minor",
		"needs-improvement": "Needs improvement",
	};
	return (
		<span className={cn("rounded-full px-2 py-0.5 text-[11px] font-medium", styles[level])}>
			{labels[level]}
		</span>
	);
}

interface FindingRowProps {
	icon: React.ComponentType<{ className?: string }>;
	label: string;
	children: React.ReactNode;
}

function FindingRow({ icon: Icon, label, children }: FindingRowProps) {
	return (
		<div className="flex gap-3">
			<Icon className="h-4 w-4 mt-0.5 shrink-0 text-muted-foreground" />
			<div className="space-y-1 text-left">
				<div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
					{label}
				</div>
				<p className="leading-relaxed">{children}</p>
			</div>
		</div>
	);
}
