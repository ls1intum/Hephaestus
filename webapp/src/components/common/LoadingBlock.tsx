import { cva, type VariantProps } from "class-variance-authority";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";

const loadingBlockVariants = cva("flex items-center justify-center", {
	variants: {
		size: {
			/** Inside a panel or a card. */
			sm: "min-h-32 [&_svg]:size-5",
			/** A whole page's content area. */
			lg: "min-h-64 [&_svg]:size-8",
		},
	},
	defaultVariants: { size: "lg" },
});

export interface LoadingBlockProps extends VariantProps<typeof loadingBlockVariants> {
	/**
	 * What is loading, announced to assistive tech. Required, because "Loading" on its own tells a
	 * screen-reader user nothing about which of the three regions on the page is busy.
	 */
	label: string;
	className?: string;
}

/**
 * A centred spinner with a name.
 *
 * The repo grew twelve spellings of this — `h-64`/`h-96`/`min-h-64`/`min-h-32`/`h-40`/`py-6`, with
 * `size-8` and `h-8 w-8` both in use — and several announced only the primitive's generic "Loading",
 * which tells a screen-reader user nothing about which of three regions on the page is busy.
 *
 * The status role lives on the wrapper and the `Spinner` is muted with `aria-hidden`, rather than
 * the other way round: the primitive carries its own `role="status"` and a generic "Loading" label,
 * and leaving both in place announces the same thing twice under two different names.
 *
 * The label is carried twice on purpose, and neither one is redundant: `aria-label` names the region
 * (`status` does not take its name from its content, so hidden text alone leaves it unnamed), and the
 * `sr-only` text is what a polite live region actually announces when it appears (an `aria-label` on
 * the container is not).
 */
export function LoadingBlock({ label, size, className }: LoadingBlockProps) {
	return (
		<div role="status" aria-label={label} className={cn(loadingBlockVariants({ size }), className)}>
			<Spinner aria-hidden />
			<span className="sr-only">{label}</span>
		</div>
	);
}
