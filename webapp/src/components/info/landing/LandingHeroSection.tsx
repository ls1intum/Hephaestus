import { ChevronDown } from "lucide-react";
import type { PracticeReportCard } from "@/api/types.gen";
import { LandingSignInCTA } from "@/components/auth/LandingSignInCTA";
import { MentorIcon } from "@/components/mentor/MentorIcon";
import { PracticeReflectionCard } from "@/components/practices/reflection/PracticeReflectionCard";
import { Button } from "@/components/ui/button";

// Sample practice reflection for the marketing preview.
const SAMPLE_PRACTICE: PracticeReportCard = {
	name: "Write a clear pull request description",
	areaName: "Pull requests",
	slug: "clear-pr-description",
	status: "DEVELOPING",
	trend: "STEADY",
	whyItMatters:
		"A clear description helps reviewers understand the change quickly and gives future readers the context behind it.",
	strengths: [
		{
			artifactId: 42,
			artifactType: "PULL_REQUEST",
			observationId: "sample-strength-1",
			title: "Explained the user-facing impact up front",
			guidance: "You opened with what changes for the user — reviewers get oriented fast.",
			locator: "PR #42",
			severity: "INFO",
		},
	],
	toWorkOn: [
		{
			artifactId: 42,
			artifactType: "PULL_REQUEST",
			observationId: "sample-work-1",
			title: "Link the issue this pull request closes",
			guidance: "Add a closing keyword (e.g. “Closes #17”) so the issue and PR stay connected.",
			locator: "PR #42",
			severity: "MINOR",
		},
	],
};

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
							Process-Aware Mentoring for Agile Software Teams
						</h1>
						<p className="mx-auto max-w-[700px] text-xl text-muted-foreground">
							Onboard faster and learn better habits with an AI mentor grounded in your repo
							workflow — from issues to pull requests and team rituals.
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
							Learn More <ChevronDown className="h-4 w-4" />
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

			{/* Practice reflection preview */}
			<div className="mx-auto max-w-2xl px-4 md:px-6">
				<div
					aria-hidden="true"
					className="pointer-events-none -mb-3 overflow-hidden rounded-md border border-muted shadow-xl"
					style={{
						maskImage: "linear-gradient(to bottom, rgba(0, 0, 0, 1) 60%, rgba(0, 0, 0, 0))",
					}}
				>
					<PracticeReflectionCard practice={SAMPLE_PRACTICE} />
				</div>
				<p className="mt-3 text-center text-sm text-muted-foreground">
					Personal, practice-by-practice feedback on your own work — for your growth, never a score.
				</p>
			</div>
		</section>
	);
}
