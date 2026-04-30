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
						Practice-Aware Guidance
					</h2>
					<p className="text-muted-foreground text-lg">
						You define what good looks like. Hephaestus evaluates every contribution against your
						practices and gives each contributor personalized feedback.
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
								Evaluate contributions against your project's practice catalog
							</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>AI agent evaluates each PR against workspace-defined practices</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Structured findings with verdict, severity, evidence, and guidance</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>You stay in control — mark findings as applied, disputed, or N/A</span>
								</li>
							</ul>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<MessageCircle className="h-5 w-5 text-primary" />
							</div>
							<CardTitle>Adaptive Coaching</CardTitle>
							<CardDescription>Guidance adapts to each contributor's track record</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										New to a practice? Get concrete examples. Repeat issue? Direct coaching.
									</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Findings appear as PR comments and inline code annotations</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Heph, the AI mentor, supports reflection, goal-setting, and summaries</span>
								</li>
							</ul>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<TrendingUp className="h-5 w-5 text-green-500" />
							</div>
							<CardTitle>Engagement & Recognition</CardTitle>
							<CardDescription>Surface contribution activity over time</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Weekly leaderboard with leagues and achievements</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Profile timeline grouped by repository and contribution type</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Weekly Slack digests highlight standout contributors</span>
								</li>
							</ul>
						</CardContent>
					</Card>
				</div>
			</div>
		</section>
	);
}
