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
									<span>Catches bad practices across pull requests, reviews, and commits</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Adapts to context — early work gets coaching, finished work gets rigor
									</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>You stay in control — accept, dismiss, or challenge any finding</span>
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
									<span>AI mentor (Heph) helps you reflect on your work and plan next steps</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Timely notifications reach you where you work — Slack, email, or in-app
									</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Achievements and progression chains celebrate sustained good practices
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
									<span>Achievement milestones track your growth from first steps to mastery</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										A league system gives you a fair, evolving rank based on your contributions
									</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Weekly digests and leaderboards make great work visible to the whole team
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
