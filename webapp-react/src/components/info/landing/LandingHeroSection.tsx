import type { LeaderboardEntry, PullRequestInfo } from "@/api/types.gen";
import aliceAvatar from "@/assets/alice_developer.jpg";
import bobAvatar from "@/assets/bob_builder.jpg";
import charlieAvatar from "@/assets/charlie_coder.jpg";
import { LeaderboardTable } from "@/components/leaderboard/LeaderboardTable";
import { MentorIcon } from "@/components/mentor/MentorIcon";
import { Button } from "@/components/ui/button";
import { ArrowRight, ChevronDown } from "lucide-react";

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
const SAMPLE_LEADERBOARD_ENTRIES: LeaderboardEntry[] = [
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

interface LandingHeroSectionProps {
	onSignIn: () => void;
	isSignedIn: boolean;
	onLearnMoreClick: () => void;
}

export function LandingHeroSection({
	onSignIn,
	isSignedIn,
	onLearnMoreClick,
}: LandingHeroSectionProps) {
	return (
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
						<Button onClick={onSignIn} size="lg" className="gap-2">
							{isSignedIn ? "Go to Dashboard" : "Get Started"}{" "}
							<ArrowRight className="h-4 w-4" />
						</Button>
						<Button
							variant="outline"
							size="lg"
							onClick={onLearnMoreClick}
							className="gap-2"
						>
							Learn More <ChevronDown className="h-4 w-4" />
						</Button>
					</div>
					<div className="flex items-center gap-2 text-muted-foreground">
						<MentorIcon size={36} className="text-primary" />
						<span className="text-sm">Powered by <span className="text-github-done-foreground">Heph</span>, your AI mentor</span>
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
							leaderboard={SAMPLE_LEADERBOARD_ENTRIES}
							isLoading={false}
						/>
					</div>
				</div>
			</div>
		</section>
	);
}
