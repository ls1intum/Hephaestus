import { ExternalLinkIcon } from "lucide-react";
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
						<DialogBody className="space-y-4 leading-6">
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

								<Field orientation="horizontal" className="bg-muted/40 rounded-lg border p-3">
									<Checkbox id={researchId} checked={research} onCheckedChange={setResearch} />
									<FieldContent>
										<FieldLabel htmlFor={researchId}>Help improve Hephaestus research</FieldLabel>
										<FieldDescription>
											Optional. Declining changes nothing about your access, and you can change this
											later in settings.
										</FieldDescription>
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
