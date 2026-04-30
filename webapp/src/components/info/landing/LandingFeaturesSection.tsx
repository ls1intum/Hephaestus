interface Stage {
	title: string;
	body: string;
}

const STAGES: Stage[] = [
	{
		title: "Your standards, written down.",
		body: "Each project decides what good contribution looks like — a clear pull-request description, a thoughtful review, a follow-through on commitments. Hephaestus reads from that list. Edit it any time.",
	},
	{
		title: "Comments where the work is.",
		body: "Findings appear on the pull request, beside the change. The contributor reads them with the rest of the review, applies what fits, and pushes back on what doesn't.",
	},
	{
		title: "A mentor when you want to think out loud.",
		body: "Open a conversation. Ask what to focus on this week, talk through what's stuck, plan the next push. The mentor reads the same activity you do.",
	},
	{
		title: "A quiet place to look back.",
		body: "Your profile shows what you've been working on and how your practices are trending. Private to you. There when you want it.",
	},
];

export function LandingFeaturesSection() {
	return (
		<section id="features" className="w-full py-20 md:py-32 bg-background">
			<div className="container max-w-4xl px-4 md:px-6 space-y-16">
				<div className="space-y-4 max-w-2xl">
					<h2 className="text-4xl font-semibold tracking-tight sm:text-5xl">
						How it fits into the day.
					</h2>
					<p className="text-lg text-muted-foreground">
						Hephaestus sits alongside your existing review. Same place, more useful.
					</p>
				</div>

				<div className="space-y-12">
					{STAGES.map((stage) => (
						<div key={stage.title} className="space-y-2">
							<h3 className="text-2xl font-semibold tracking-tight">{stage.title}</h3>
							<p className="text-lg text-muted-foreground leading-relaxed max-w-2xl">
								{stage.body}
							</p>
						</div>
					))}
				</div>
			</div>
		</section>
	);
}
