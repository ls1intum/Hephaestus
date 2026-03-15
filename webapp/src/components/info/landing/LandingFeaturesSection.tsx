import { CheckCheck, MessageCircle, ScanSearch, TrendingUp } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export function LandingFeaturesSection() {
	return (
		<section id="features" className="w-full py-12 md:py-24 bg-background">
			<div className="container px-4 md:px-6">
				<div className="mb-12 text-center max-w-3xl mx-auto">
					<Badge className="mb-4" variant="outline">
						Key Features
					</Badge>
					<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl mb-4">
						Built for How Teams Actually Work
					</h2>
					<p className="text-muted-foreground text-lg">
						Detect practice patterns. Guide improvement through the right channel. Track growth over
						time.
					</p>
				</div>

				<div className="grid gap-8 md:grid-cols-2 lg:grid-cols-3">
					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<ScanSearch className="h-5 w-5 text-blue-500" />
							</div>
							<CardTitle>Practice Detection</CardTitle>
							<CardDescription>
								Identify what's working and what's not — before it becomes habit
							</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Catches patterns like rubber-stamp reviews and missing PR descriptions
									</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Adapts severity to PR lifecycle — early work is a coaching moment, not a
										violation
									</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Contributors close the loop by marking findings as fixed, adjusted, or incorrect
									</span>
								</li>
							</ul>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<MessageCircle className="h-5 w-5 text-primary" />
							</div>
							<CardTitle>Multi-Channel Guidance</CardTitle>
							<CardDescription>
								Coaching at the right time, through the right channel
							</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										AI mentor (Heph) leads structured reflection tied to real PRs, reviews, and
										issues
									</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Practice notifications reach contributors via email, Slack, and in-app alerts
									</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										60+ achievements with progression chains recognize sustained good practices
									</span>
								</li>
							</ul>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<TrendingUp className="h-5 w-5 text-green-500" />
							</div>
							<CardTitle>Growth Tracking</CardTitle>
							<CardDescription>See development trajectories, not just snapshots</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Achievement chains from common to mythic track practice milestones</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Elo-like league system provides persistent, transparent ranking</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Weekly Slack digests and leaderboards make good practices visible to the whole
										team
									</span>
								</li>
							</ul>
						</CardContent>
					</Card>
				</div>
			</div>
		</section>
	);
}
