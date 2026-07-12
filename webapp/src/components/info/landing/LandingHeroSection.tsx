import { ChevronDown } from "lucide-react";
import { LandingSignInCTA } from "@/components/auth/LandingSignInCTA";
import { MentorIcon } from "@/components/mentor/MentorIcon";
import { Button } from "@/components/ui/button";

interface LandingHeroSectionProps {
	onSignIn: (idpHint: string) => void;
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
							Mentoring feedback, grounded in your work
						</h1>
						<p className="mx-auto max-w-[700px] text-xl text-muted-foreground">
							Hephaestus reads your pull requests, issues, and reviews and tells you what was done
							well, what could be better, and a way to get there.
						</p>
					</div>
					<div className="flex flex-col items-center gap-4 sm:flex-row sm:gap-6">
						<LandingSignInCTA
							isSignedIn={isSignedIn}
							onSignIn={onSignIn}
							onGoToDashboard={onGoToDashboard}
							size="lg"
							className="w-full sm:w-auto"
						/>
						<Button variant="outline" size="lg" onClick={onLearnMoreClick} className="gap-2">
							Learn more <ChevronDown className="h-4 w-4" />
						</Button>
					</div>
					<div className="flex items-center gap-2 text-muted-foreground">
						<MentorIcon size={36} className="text-primary" />
						<span className="text-sm">
							Powered by <span className="text-provider-done-foreground">Heph</span>, your AI mentor
						</span>
					</div>
				</div>
			</div>

			{/* Mentor feedback preview (decorative mock conversation, hidden from assistive tech) */}
			<div className="mx-auto max-w-2xl px-4 md:px-6">
				<div
					aria-hidden="true"
					className="shadow-xl border border-muted rounded-md overflow-hidden -mb-3 bg-background text-left"
				>
					<div className="flex items-center gap-2 border-b border-muted px-4 py-3">
						<MentorIcon size={24} className="text-primary" />
						<span className="text-sm font-medium">Heph</span>
						<span className="text-xs text-muted-foreground">on your pull request</span>
					</div>
					<div
						className="space-y-3 px-4 py-4 text-sm pointer-events-none"
						style={{
							maskImage: "linear-gradient(to bottom, rgba(0, 0, 0, 1) 60%, rgba(0, 0, 0, 0))",
						}}
					>
						<p className="rounded-md bg-muted/50 px-3 py-2">
							Nice work keeping this change small and splitting the schema migration into its own
							commit. That made the review quick to get through.
						</p>
						<p className="rounded-md bg-muted/50 px-3 py-2">
							One thing to tighten up: the pull request description says what changed but not why. A
							sentence linking it to the issue would help the next reader. Want a suggestion?
						</p>
						<p className="rounded-md bg-primary/10 px-3 py-2 text-right">Yes, draft one for me.</p>
					</div>
				</div>
				<p className="mt-3 text-center text-sm text-muted-foreground">
					Act on the feedback, push back with a reason, or let it pass. You stay in charge.
				</p>
			</div>
		</section>
	);
}
