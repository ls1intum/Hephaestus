import { CircleAlert } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

export interface FormError {
	/** The `id` of the field this is about. Activating the entry moves focus there. */
	fieldId: string;
	message: string;
	/**
	 * Called before focusing, for a field that is not in the document yet. A collapsed section
	 * unmounts its contents, so without this the entry links to an id that does not exist and
	 * focus stays on the link — which is worse than no link at all.
	 */
	reveal?: () => void;
}

export interface FormErrorSummaryProps {
	errors: FormError[];
	className?: string;
}

/**
 * Every reason a submit was refused, at the top of the form, each one linking to the field it is
 * about.
 *
 * A long form with only inline errors makes the reader hunt: they press the action, nothing visible
 * happens, and the message is somewhere in the two thousand pixels above or below them. GOV.UK's
 * form guidance is the source here — an error summary is what lets someone "recover easily from form
 * errors" on a page they cannot see all of.
 *
 * It does **not** take focus. `Alert` is a live region, so it is announced when it appears, and the
 * form already sends focus to the first invalid field — which is where the reader wants to be. An
 * earlier version focused the summary whenever the error count changed, and since the count changes
 * as you type, it pulled the caret out of the field after the third character.
 */
export function FormErrorSummary({ errors, className }: FormErrorSummaryProps) {
	const count = errors.length;
	if (count === 0) return null;

	return (
		<Alert variant="destructive" className={className}>
			<CircleAlert />
			<AlertTitle>
				<h2>{count === 1 ? "There is a problem" : `There are ${count} problems`}</h2>
			</AlertTitle>
			<AlertDescription>
				<ul className="list-inside list-disc space-y-1">
					{errors.map((error) => (
						<li key={error.fieldId}>
							<a
								href={`#${error.fieldId}`}
								className="underline underline-offset-4"
								onClick={(event) => {
									// The browser's own fragment jump cannot reveal a collapsed section, and
									// cannot wait a frame for it to mount.
									event.preventDefault();
									error.reveal?.();
									requestAnimationFrame(() => document.getElementById(error.fieldId)?.focus());
								}}
							>
								{error.message}
							</a>
						</li>
					))}
				</ul>
			</AlertDescription>
		</Alert>
	);
}
