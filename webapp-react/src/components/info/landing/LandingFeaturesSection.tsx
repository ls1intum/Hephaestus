import { CheckCheck, Trophy } from "lucide-react";
import { MentorIcon } from "@/components/mentor/MentorIcon";
import { Badge } from "@/components/ui/badge";
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";

export function LandingFeaturesSection() {
	return (
		<section id="features" className="w-full py-12 md:py-24 bg-background">
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
							<div className="flex items-center gap-2 mb-2 text-muted-foreground">
								<MentorIcon className="-m-2" size={32} pad={4} />
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
	);
}
