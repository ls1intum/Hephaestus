import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { useNavigate } from "@tanstack/react-router";
import {
	ArrowRight,
	BotMessageSquare,
	CheckCheck,
	ChevronDown,
	Code,
	Github,
	Hammer,
	Trophy,
	Users,
} from "lucide-react";
import { useRef } from "react";

import type { LeaderboardEntry, PullRequestInfo } from "@/api/types.gen";
import { LeaderboardTable } from "@/components/leaderboard/LeaderboardTable";

import agileHephaestus from "@/assets/agile_hephaestus.png";
import aliceAvatar from "@/assets/alice_developer.jpg";
import bobAvatar from "@/assets/bob_builder.jpg";
import charlieAvatar from "@/assets/charlie_coder.jpg";

interface LandingPageProps {
	onSignIn: () => void;
	isSignedIn?: boolean;
}

function createMockReviewedPullRequest(amount: number) {
	return Array.from(
		{ length: amount },
		() =>
			({
				id: 1,
				title: "Fix bug in user authentication",
				number: 42,
				htmlUrl: "https://example.com/pull/42",
				state: "CLOSED",
				isDraft: false,
				isMerged: true,
				commentsCount: 5,
				createdAt: new Date("2023-01-01T00:00:00Z"),
				updatedAt: new Date("2023-01-02T00:00:00Z"),
				additions: 10,
				deletions: 2,
				repository: {
					id: 1,
					name: "example/repo",
					nameWithOwner: "example/repo",
					htmlUrl: "https://example.com/repo",
				},
			}) satisfies PullRequestInfo,
	);
}

// Sample data for the leaderboard preview
const sampleLeaderboardEntries: LeaderboardEntry[] = [
	{
		rank: 1,
		score: 520,
		user: {
			id: 0,
			leaguePoints: 2000,
			login: "codeMaster",
			avatarUrl: aliceAvatar,
			name: "Alice Developer",
			htmlUrl: "https://example.com/alice",
		},
		numberOfReviewedPRs: 15,
		numberOfApprovals: 8,
		numberOfChangeRequests: 3,
		numberOfComments: 4,
		numberOfCodeComments: 6,
		numberOfUnknowns: 0,
		reviewedPullRequests: createMockReviewedPullRequest(12),
	},
	{
		rank: 2,
		score: 431,
		user: {
			id: 1,
			leaguePoints: 1000,
			login: "devWizard",
			avatarUrl: bobAvatar,
			name: "Bob Builder",
			htmlUrl: "https://example.com/bob",
		},
		numberOfReviewedPRs: 12,
		numberOfApprovals: 5,
		numberOfChangeRequests: 2,
		numberOfComments: 5,
		numberOfCodeComments: 3,
		numberOfUnknowns: 0,
		reviewedPullRequests: createMockReviewedPullRequest(5),
	},
	{
		rank: 3,
		score: 302,
		user: {
			id: 2,
			leaguePoints: 1500,
			login: "codeNinja",
			avatarUrl: charlieAvatar,
			name: "Charlie Coder",
			htmlUrl: "https://example.com/charlie",
		},
		numberOfReviewedPRs: 9,
		numberOfApprovals: 4,
		numberOfChangeRequests: 1,
		numberOfComments: 4,
		numberOfCodeComments: 2,
		numberOfUnknowns: 0,
		reviewedPullRequests: createMockReviewedPullRequest(2),
	},
];

// FAQ items with more honest and straightforward answers
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

export function LandingPage({
	onSignIn,
	isSignedIn = false,
}: LandingPageProps) {
	const learnMoreRef = useRef<HTMLDivElement>(null);
	const navigate = useNavigate();

	const handleCTAClick = () => {
		if (isSignedIn) {
			navigate({ to: "/" });
		} else {
			onSignIn();
		}
	};

	return (
		<div className="flex flex-col">
			{/* Override the container class in main to make sections full width */}
			<style>{`
        main.container {
          max-width: 100% !important;
          padding-left: 0 !important;
          padding-right: 0 !important;
          padding-top: 0 !important;
          margin-top: 0 !important;
        }
      `}</style>

			{/* Hero Section - Full Width */}
			<section className="w-full bg-gradient-to-b from-background to-muted/30 pt-8 md:pt-16 lg:pt-24 text-foreground">
				<div className="container mx-auto px-4 md:px-6 mb-12">
					<div className="flex flex-col items-center space-y-8 text-center">
						<div className="space-y-4 max-w-3xl">
							<h1 className="text-4xl font-bold tracking-tighter sm:text-5xl md:text-6xl">
								Forge Healthy Software Teams
							</h1>
							<p className="mx-auto max-w-[700px] text-xl text-muted-foreground">
								Empower engineers, foster collaboration, and build sustainable
								development practices with our open-source platform.
							</p>
						</div>
						<div className="flex gap-4 sm:gap-6">
							<Button onClick={handleCTAClick} size="lg" className="gap-2">
								{isSignedIn ? "Go to Dashboard" : "Get Started"}{" "}
								<ArrowRight className="h-4 w-4" />
							</Button>
							<Button
								variant="outline"
								size="lg"
								onClick={() =>
									learnMoreRef.current?.scrollIntoView({
										behavior: "smooth",
										block: "start",
									})
								}
								className="gap-2"
							>
								Learn More <ChevronDown className="h-4 w-4" />
							</Button>
						</div>
					</div>
				</div>

				{/* Leaderboard Preview */}
				<div className="mx-auto max-w-4xl px-4 md:px-6">
					<div className="shadow-xl border border-muted rounded-md overflow-hidden -mb-3">
						<div
							className="overflow-auto pointer-events-none"
							style={{
								maskImage:
									"linear-gradient(to bottom, rgba(0, 0, 0, 1) 50%, rgba(0, 0, 0, 0))",
							}}
						>
							<LeaderboardTable
								leaderboard={sampleLeaderboardEntries}
								isLoading={false}
							/>
						</div>
					</div>
				</div>
			</section>

			{/* Features Section */}
			<section
				ref={learnMoreRef}
				id="features"
				className="w-full py-12 md:py-24 bg-background"
			>
				<div className="container px-4 md:px-6">
					<div className="mb-12 text-center max-w-3xl mx-auto">
						<Badge className="mb-4" variant="outline">
							Key Features
						</Badge>
						<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl mb-4">
							Tools for Team Growth
						</h2>
						<p className="text-muted-foreground text-lg">
							Features designed to elevate your engineering team's collaboration
							and learning
						</p>
					</div>

					<div className="grid gap-8 md:grid-cols-2">
						<Card>
							<CardHeader>
								<div className="flex items-center gap-2 mb-2">
									<Trophy className="h-5 w-5 text-yellow-500" />
								</div>
								<CardTitle>Code Review Gamification</CardTitle>
								<CardDescription>
									Transform code reviews into learning opportunities
								</CardDescription>
							</CardHeader>
							<CardContent>
								<ul className="space-y-2">
									<li className="flex gap-2 items-start">
										<CheckCheck className="size-5 mt-1 text-github-success-foreground" />
										<span>Weekly leaderboards with GitHub integration</span>
									</li>
									<li className="flex gap-2 items-start">
										<CheckCheck className="size-5 mt-1 text-github-success-foreground" />
										<span>Team competitions across multiple repositories</span>
									</li>
									<li className="flex gap-2 items-start">
										<CheckCheck className="size-5 mt-1 text-github-success-foreground" />
										<span>Structured league system for ongoing engagement</span>
									</li>
								</ul>
							</CardContent>
						</Card>

						<Card>
							<CardHeader>
								<div className="flex items-center gap-2 mb-2">
									<BotMessageSquare className="h-5 w-5 text-cyan-500" />
								</div>
								<CardTitle>AI-Powered Mentorship</CardTitle>
								<CardDescription>
									Personalized guidance for improvement
								</CardDescription>
							</CardHeader>
							<CardContent>
								<ul className="space-y-2">
									<li className="flex gap-2 items-start">
										<CheckCheck className="size-5 mt-1 text-github-success-foreground" />
										<span>Weekly reflective sessions for improvement</span>
									</li>
									<li className="flex gap-2 items-start">
										<CheckCheck className="size-5 mt-1 text-github-success-foreground" />
										<span>
											GitHub activity analysis for context-aware feedback
										</span>
									</li>
									<li className="flex gap-2 items-start">
										<CheckCheck className="size-5 mt-1 text-github-success-foreground" />
										<span>Goal-setting framework with progress tracking</span>
									</li>
								</ul>
							</CardContent>
						</Card>
					</div>
				</div>
			</section>

			{/* Why Choose Section */}
			<section className="w-full py-8 md:py-16 bg-gradient-to-b from-background to-muted/30">
				<div className="container px-4 md:px-6">
					<div className="grid gap-10 lg:grid-cols-[1fr_500px] lg:gap-12">
						<img
							src={agileHephaestus}
							alt="Agile Hephaestus"
							width="500"
							height="350"
							className="mx-auto rounded-lg aspect-auto object-cover lg:order-last"
						/>
						<div className="flex flex-col justify-center space-y-5">
							<Badge className="w-fit" variant="outline">
								Our Approach
							</Badge>
							<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl">
								Why Choose Hephaestus?
							</h2>
							<p className="text-lg text-muted-foreground">
								Named after the Greek god of craftsmen, Hephaestus combines
								creativity with technical expertise to build better development
								teams.
							</p>

							<ul className="grid gap-4 mt-4">
								<li className="flex items-start gap-3">
									<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
										<Hammer className="h-4 w-4 text-primary" />
									</div>
									<div>
										<p className="font-medium">Empower engineers</p>
										<p className="text-sm text-muted-foreground">
											Tools that accelerate learning through real-world feedback
										</p>
									</div>
								</li>
								<li className="flex items-start gap-3">
									<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
										<Users className="h-4 w-4 text-primary" />
									</div>
									<div>
										<p className="font-medium">Foster collaboration</p>
										<p className="text-sm text-muted-foreground">
											Build team habits that strengthen your engineering culture
										</p>
									</div>
								</li>
								<li className="flex items-start gap-3">
									<div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
										<Code className="h-4 w-4 text-primary" />
									</div>
									<div>
										<p className="font-medium">Improve code quality</p>
										<p className="text-sm text-muted-foreground">
											Motivate better code reviews through friendly competition
										</p>
									</div>
								</li>
							</ul>

							<div className="pt-4">
								<Button onClick={handleCTAClick} className="gap-2">
									{isSignedIn ? "Go to Dashboard" : "Get Started"}{" "}
									<ArrowRight className="h-4 w-4" />
								</Button>
							</div>
						</div>
					</div>
				</div>
			</section>

			{/* Testimonial - Only keeping the real one */}
			<section id="testimonials" className="w-full py-8 md:py-16 bg-background">
				<div className="container px-4 md:px-6">
					<div className="mb-8 text-center max-w-3xl mx-auto">
						<Badge className="mb-4" variant="outline">
							Testimonial
						</Badge>
						<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl mb-4">
							From Our Community
						</h2>
					</div>

					<div className="max-w-3xl mx-auto">
						<Card>
							<CardContent className="pt-6">
								<div className="border-l-4 border-primary pl-4 py-2 italic mb-4">
									"I heavily use Hephaestus to see how our team is doing. It
									helps me write all the things I've accomplished when it's time
									to fill a weekly report."
								</div>
								<div className="flex items-center gap-3">
									<div className="font-medium">Ege Kocabas</div>
									<div className="text-sm text-muted-foreground">
										<Button
											variant="link"
											size="none"
											className="text-muted-foreground"
											asChild
										>
											<a href="https://github.com/ls1intum/helios">
												Helios Project, TU Munich
											</a>
										</Button>
									</div>
								</div>
							</CardContent>
						</Card>
					</div>
				</div>
			</section>

			{/* FAQ */}
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

			{/* CTA */}
			<section className="w-full py-8 md:py-16 bg-foreground mb-[-2rem]">
				<div className="container px-4 md:px-6">
					<div className="flex flex-col items-center space-y-6 text-center max-w-3xl mx-auto">
						<h2 className="text-3xl md:text-4xl font-bold text-primary-foreground">
							Ready to Get Started?
						</h2>
						<p className="text-lg text-secondary">
							Join our community and build more collaborative and effective
							software development practices.
						</p>
						<div className="flex flex-col sm:flex-row gap-4 w-full sm:w-auto invert">
							<Button size="lg" onClick={handleCTAClick}>
								{isSignedIn ? "Go to Dashboard" : "Get Started Now"}
								<ArrowRight className="h-4 w-4" />
							</Button>
							<Button size="lg" asChild>
								<a
									href="https://ls1intum.github.io/Hephaestus/"
									target="_blank"
									rel="noopener noreferrer"
								>
									<span>Read Documentation</span>
								</a>
							</Button>
						</div>
						{!isSignedIn && (
							<p className="text-sm text-secondary pt-2">
								Open-source and free to use.
							</p>
						)}
					</div>
				</div>
			</section>
		</div>
	);
}
