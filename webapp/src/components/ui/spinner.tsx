import { Loader2Icon } from "lucide-react";
import { cn } from "@/lib/utils";

export interface SpinnerProps extends React.ComponentProps<"svg"> {
	/**
	 * Announce this spinner as a live region under the given name. Omit it — the usual case — and the
	 * spinner is decoration.
	 *
	 * A spinner inside a control must stay decoration: `role="status"` is not a presentational role,
	 * so it interrupts the accessible name computation of the button it sits in and a "Save" button
	 * announces as "Loading". The surrounding label already changes ("Saving…"), which is the
	 * announcement.
	 */
	label?: string;
}

/**
 * An indeterminate spinner, for a control the reader just activated.
 *
 * Not for a region: a list, a card grid or a page body has a known shape, and
 * [Polaris](https://polaris.shopify.com/components/feedback-indicators/spinner) restricts spinners to
 * "content that can't be represented with skeleton loading components". Use a shape-matched skeleton
 * there instead.
 */
function Spinner({ label, className, ...props }: SpinnerProps) {
	const icon = (
		<Loader2Icon
			aria-hidden={label === undefined ? true : undefined}
			className={cn("size-4 animate-spin motion-reduce:animate-none", className)}
			{...props}
		/>
	);
	if (label === undefined) {
		return icon;
	}
	return (
		<span role="status" className="contents">
			{icon}
			<span className="sr-only">{label}</span>
		</span>
	);
}

export { Spinner };
