import { CheckCheck, ScanSearch, Trophy } from "lucide-react";
import { MentorIcon } from "@/components/mentor/MentorIcon";
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
						Gamification drives engagement. AI coaching drives depth. Practice detection closes the
						loop.
					</p>
				</div>

				<div className="grid gap-8 md:grid-cols-2 lg:grid-cols-3">
					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<Trophy className="h-5 w-5 text-yellow-500" />
							</div>
							<CardTitle>Code Review Gamification</CardTitle>
							<CardDescription>Transform code reviews into learning opportunities</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Weekly leaderboards with GitHub integration</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Team competitions across multiple repositories</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Structured league system for ongoing engagement</span>
								</li>
							</ul>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2 text-muted-foreground">
								<MentorIcon className="-m-2" size={32} pad={4} />
							</div>
							<CardTitle>AI Mentor</CardTitle>
							<CardDescription>
								Heph coaches you based on your actual project activity
							</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Structured weekly reflection tied to real PRs, reviews, and issues</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Contextual feedback drawn from your repository activity</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Goal setting with progress tracking across behavioral patterns</span>
								</li>
							</ul>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<ScanSearch className="h-5 w-5 text-blue-500" />
							</div>
							<CardTitle>Practice Detection</CardTitle>
							<CardDescription>Catches anti-patterns before they become habits</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Detects patterns like rubber-stamp reviews and missing PR descriptions
									</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Lifecycle-aware severity — drafts get coaching, ready PRs get rigor</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Contributors can mark findings as fixed, won't fix, or incorrect</span>
								</li>
							</ul>
						</CardContent>
					</Card>
				</div>
			</div>
		</section>
	);
}
