import { ExternalLinkIcon, GraduationCapIcon } from "lucide-react";
import { useId, useState } from "react";

import type { ConsentStatus } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Dialog,
	DialogBody,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Spinner } from "@/components/ui/spinner";

export interface ConsentChoice {
	noticeVersion: string;
	termsAccepted: boolean;
	participateInResearch: boolean;
}

interface ConsentDialogProps {
	/** The notice to show. While it is loading the dialog is already open, so the app never flashes. */
	notice: ConsentStatus | undefined;
	/** The notice could not be loaded; the app stays blocked because the choice is still required. */
	failedToLoad?: boolean;
	onSubmit: (choice: ConsentChoice) => void;
	/** Declining is an answer too: without a way out, the only exit from a trapped surface is the tab. */
	onSignOut: () => void;
	/** Retry loading the notice, so a failed fetch is not a dead end. */
	onRetry: () => void;
	submitting?: boolean;
	/** The choice reached the server and was not stored. */
	failedToSubmit?: boolean;
}

/**
 * The transparency notice, asked where the reader already is.
 *
 * It is a dialog rather than a page because nothing about it is a destination: there is no url worth
 * sharing, no back button worth pressing, and the work behind it is the work the reader asked for.
 * It cannot be dismissed, because the server refuses every other call until the choice is recorded —
 * a closeable dialog would only produce an application that answers nothing.
 */
export function ConsentDialog({
	notice,
	failedToLoad = false,
	onSubmit,
	onSignOut,
	onRetry,
	submitting = false,
	failedToSubmit = false,
}: ConsentDialogProps) {
	const [termsAccepted, setTermsAccepted] = useState(false);
	const [research, setResearch] = useState(false);
	const termsId = useId();
	const researchId = useId();
	const researchHeadingId = useId();

	return (
		// Held open: a request to close is ignored rather than unsupported, because the server refuses
		// every other call until the choice is recorded and a closeable dialog would leave an
		// application that answers nothing.
		<Dialog open modal onOpenChange={() => undefined}>
			<DialogContent showCloseButton={false} className="sm:max-w-xl" aria-describedby={undefined}>
				<DialogHeader>
					<DialogTitle>How Hephaestus uses your data</DialogTitle>
					<DialogDescription>
						{notice
							? `A short summary, then two choices. Notice version ${notice.noticeVersion}.`
							: "A short summary, then two choices."}
					</DialogDescription>
				</DialogHeader>

				{failedToLoad ? (
					<>
						<DialogBody>
							<p role="alert" className="text-muted-foreground">
								We couldn't load the notice just now. Reload the page to try again — this choice is
								needed before Hephaestus can show you anything.
							</p>
						</DialogBody>
						<DialogFooter className="flex-col items-stretch gap-3 sm:flex-col sm:items-stretch">
							<Button type="button" onClick={onRetry}>
								Try again
							</Button>
							<Button type="button" variant="ghost" onClick={onSignOut}>
								Sign out instead
							</Button>
						</DialogFooter>
					</>
				) : notice ? (
					<>
						<DialogBody className="space-y-6 leading-6">
							<div className="space-y-4">
								{notice.noticeText.split("\n\n").map((paragraph) => (
									<p key={paragraph} className="text-muted-foreground">
										{paragraph}
									</p>
								))}
								<a
									href="/privacy"
									target="_blank"
									rel="noreferrer"
									className="text-foreground inline-flex items-center gap-1 font-medium underline underline-offset-4"
								>
									Read the full privacy notice
									<ExternalLinkIcon className="size-3.5" aria-hidden />
								</a>
							</div>

							{/* The research invitation stands on its own, as a thing worth doing, rather than as a
							    second checkbox under the legal one. It stays unticked: consent has to be an act
							    the reader takes, and a pre-ticked box is not one. */}
							<section
								aria-labelledby={researchHeadingId}
								className="bg-primary/5 ring-primary/20 space-y-3 rounded-lg p-4 ring-1"
							>
								<div className="flex items-start gap-3">
									<GraduationCapIcon className="text-primary mt-0.5 size-5 shrink-0" aria-hidden />
									<div className="space-y-1">
										<h3 id={researchHeadingId} className="text-foreground font-semibold">
											Help TUM understand how developers grow
										</h3>
										<p className="text-muted-foreground">
											Hephaestus is built by TUM's Applied Education Technologies group. If you take
											part, how you use Hephaestus and how you respond to its feedback informs that
											research — only for as long as you say so.
										</p>
									</div>
								</div>
								<Field orientation="horizontal" className="pl-8">
									<Checkbox id={researchId} checked={research} onCheckedChange={setResearch} />
									<FieldContent>
										<FieldLabel htmlFor={researchId} className="font-medium">
											Yes, I'll take part in the research
										</FieldLabel>
										<FieldDescription>
											Optional. It never changes the feedback you get, and you can withdraw any time
											in settings.
										</FieldDescription>
									</FieldContent>
								</Field>
							</section>
						</DialogBody>

						<form
							className="contents"
							onSubmit={(event) => {
								event.preventDefault();
								onSubmit({
									noticeVersion: notice.noticeVersion,
									termsAccepted,
									participateInResearch: research,
								});
							}}
						>
							<DialogFooter className="flex-col items-stretch gap-3 sm:flex-col sm:items-stretch">
								<Field orientation="horizontal">
									<Checkbox
										id={termsId}
										checked={termsAccepted}
										onCheckedChange={setTermsAccepted}
									/>
									<FieldContent>
										<FieldLabel htmlFor={termsId}>I accept the terms of use</FieldLabel>
										<FieldDescription>Required to use Hephaestus.</FieldDescription>
									</FieldContent>
								</Field>

								{failedToSubmit && (
									<p role="alert" className="text-destructive">
										Your choice wasn't saved. Try again.
									</p>
								)}

								<Button type="submit" disabled={!termsAccepted || submitting}>
									{submitting && <Spinner />}
									{submitting ? "Saving" : "Continue"}
								</Button>
								<Button type="button" variant="ghost" onClick={onSignOut} disabled={submitting}>
									Sign out instead
								</Button>
							</DialogFooter>
						</form>
					</>
				) : (
					<DialogBody className="flex justify-center py-8">
						<Spinner className="size-6" aria-label="Loading the transparency notice" />
					</DialogBody>
				)}
			</DialogContent>
		</Dialog>
	);
}
