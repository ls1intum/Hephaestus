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
	/** What is loading. Required: "Loading" alone does not say which region of a page is busy. */
	label: string;
	className?: string;
}

/**
 * The `Spinner` is muted and the status role sits on the wrapper: the primitive carries its own
 * `role="status"` and a generic "Loading", and leaving both announces twice.
 *
 * The label is content, not an `aria-label`. A live region announces its content, and a name would
 * be read *before* it — so naming it as well says the same words twice.
 */
export function LoadingBlock({ label, size, className }: LoadingBlockProps) {
	return (
		<div role="status" className={cn(loadingBlockVariants({ size }), className)}>
			<Spinner aria-hidden />
			<span className="sr-only">{label}</span>
		</div>
	);
}
