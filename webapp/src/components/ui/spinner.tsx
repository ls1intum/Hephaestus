import { Loader2Icon } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * An indeterminate spinner, for a control the reader just activated. A region gets a skeleton.
 *
 * Decoration by default: `role="status"` is not a presentational role, so a spinner that carries one
 * inside a button interrupts that button's accessible name computation and "Save" announces as
 * "Loading". A caller that genuinely needs the announcement opts in with the standard attributes —
 * pass `role` or `aria-label` and the spinner stops hiding itself.
 */
function Spinner({ className, ...props }: React.ComponentProps<"svg">) {
	const announces = props.role !== undefined || props["aria-label"] !== undefined;
	return (
		<Loader2Icon
			aria-hidden={announces ? undefined : true}
			className={cn("size-4 animate-spin motion-reduce:animate-none", className)}
			{...props}
		/>
	);
}

export { Spinner };
