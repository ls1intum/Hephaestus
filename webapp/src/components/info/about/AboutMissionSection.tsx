import { Separator } from "@/components/ui/separator";

export function AboutMissionSection() {
	return (
		<div className="max-w-2xl mx-auto space-y-16">
			<section className="space-y-5">
				<h2 className="text-3xl font-semibold tracking-tight">Why we built it.</h2>
				<div className="space-y-4 text-lg text-muted-foreground leading-relaxed">
					<p>
						People learn to write good software twice. First, the language and the libraries.
						Second, the harder part — describing your change so a reviewer can engage with it,
						scoping work so it can be reviewed at all, leaving a comment that helps the next person.
					</p>
					<p>
						The second part is learned in conversation, around real work. With a mentor, around a
						pull request. The conversation is rare, even when the work is plentiful.
					</p>
					<p>
						Hephaestus extends that conversation. Each contribution is read alongside the practices
						the project has written down, and a comment appears beside it — with the evidence behind
						it and a suggested next move.
					</p>
				</div>
			</section>

			<Separator />

			<section className="space-y-5">
				<h2 className="text-3xl font-semibold tracking-tight">How it talks to people.</h2>
				<div className="space-y-4 text-lg text-muted-foreground leading-relaxed">
					<p>
						The advice is about the work, with evidence from the work, and ends with something
						concrete to try. Take what fits. Push back on what doesn't.
					</p>
					<p>
						The mentor is a thinking partner. It doesn't write code. It's a place to articulate what
						you're trying to do and look back at what you've done.
					</p>
					<p>
						Reflection stays close to you. A reflection dashboard is on the way; today, findings
						land in the pull request and on your profile.
					</p>
				</div>
			</section>

			<Separator />

			<section className="space-y-5">
				<h2 className="text-3xl font-semibold tracking-tight">Open, and made for tinkering.</h2>
				<p className="text-lg text-muted-foreground leading-relaxed">
					Hephaestus is built at the{" "}
					<a
						className="underline underline-offset-4 hover:text-foreground"
						href="https://aet.cit.tum.de/"
						target="_blank"
						rel="noopener noreferrer"
					>
						Applied Education Technologies
					</a>{" "}
					group at TUM. The code is open. The list of practices is yours to define — today by
					editing seed data, soon through an in-app editor. Run it on your own infrastructure, point
					it at your own AI model.
				</p>
			</section>
		</div>
	);
}
