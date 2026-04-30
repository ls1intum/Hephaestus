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
						Hephaestus extends that conversation. It reads each contribution, holds it up against
						the practices the project cares about, and writes back — patient, specific, with
						examples when they help.
					</p>
				</div>
			</section>

			<Separator />

			<section className="space-y-5">
				<h2 className="text-3xl font-semibold tracking-tight">How it talks to people.</h2>
				<div className="space-y-4 text-lg text-muted-foreground leading-relaxed">
					<p>
						We write Hephaestus the way a thoughtful colleague would. The advice is about the work,
						with evidence from the work, and it ends with something concrete to try.
					</p>
					<p>
						The mentor is a thinking partner. It doesn't write code for you. It asks what you're
						trying to do, looks at what you've actually done, and helps you decide what to do next.
					</p>
					<p>
						Reflection is yours alone. Your profile is private. Aggregate views exist for the people
						coaching a team — to start better conversations, never to rank people.
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
					group at TUM. The code is open. The list of practices is yours to write. Run it on your
					own infrastructure, point it at your own model. Tell us what you change.
				</p>
			</section>
		</div>
	);
}
