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
							Feedback on how you work
						</h1>
						<p className="mx-auto max-w-[700px] text-xl text-muted-foreground">
							Hephaestus draws on project activity already recorded in your team's tools to give
							feedback on engineering practices. See what worked, what could improve, and what to
							try next.
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
							See how it works <ChevronDown className="h-4 w-4" />
						</Button>
					</div>
					<div className="flex items-center gap-2 text-muted-foreground">
						<span className="text-sm">Works with GitHub and GitLab</span>
					</div>
				</div>
			</div>

			{/* Practice feedback preview (decorative mock comment, hidden from assistive tech) */}
			<div className="mx-auto max-w-2xl px-4 md:px-6">
				<div
					aria-hidden="true"
					className="shadow-xl border border-muted rounded-md overflow-hidden -mb-3 bg-background text-left"
				>
					<div className="flex items-center gap-2 border-b border-muted px-4 py-3">
						<MentorIcon size={24} className="text-primary" />
						<span className="text-sm font-medium">Hephaestus</span>
						<span className="text-xs text-muted-foreground">Practice feedback</span>
					</div>
					<div
						className="space-y-3 px-4 py-4 text-sm pointer-events-none"
						style={{
							maskImage: "linear-gradient(to bottom, rgba(0, 0, 0, 1) 60%, rgba(0, 0, 0, 0))",
						}}
					>
						<p className="rounded-md bg-muted/50 px-3 py-2">
							<span className="font-medium">What worked:</span> You kept this change small and put
							the schema migration in its own commit. That made the change easier to review.
						</p>
						<p className="rounded-md bg-muted/50 px-3 py-2">
							<span className="font-medium">Try next:</span> The description says what changed but
							not why. Link the issue and add one sentence explaining the reason for the change.
						</p>
					</div>
				</div>
				<p className="mt-3 text-center text-sm text-muted-foreground">
					The feedback is advisory. Act on it, push back with a reason, or let it pass.
				</p>
			</div>
		</section>
	);
}
