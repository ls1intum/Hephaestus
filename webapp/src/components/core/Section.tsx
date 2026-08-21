import { cva, type VariantProps } from "class-variance-authority";
import { type ComponentProps, type ReactNode, useId } from "react";
import { cn } from "@/lib/utils";

const sectionTitleVariants = cva("text-foreground", {
	variants: {
		size: {
			/** A subsection inside a panel or a page region. */
			sm: "text-sm font-medium",
			/** A section of an admin page, one step below the page title. */
			md: "text-lg font-semibold",
		},
	},
	defaultVariants: { size: "md" },
});

export interface SectionProps
	extends Omit<ComponentProps<"section">, "title">,
		VariantProps<typeof sectionTitleVariants> {
	title: ReactNode;
	description?: ReactNode;
	/** Controls that belong to the section rather than to any one row in it. */
	actions?: ReactNode;
	/** `h3` when the section sits inside another one, so the outline stays truthful. */
	level?: 2 | 3;
}

/**
 * A titled region of a page or a panel.
 *
 * It exists because this repo had five spellings of the same thing — `text-lg font-semibold` and
 * `font-semibold text-lg` were separate clusters — and because roughly a third of the hand-rolled
 * ones had no `aria-labelledby`, which a component can simply not get wrong: the id is generated
 * here and wired to the region without the caller thinking about it.
 *
 * Configured rather than composed on purpose. The three holes are a title, a description and an
 * actions slot, every call site fills them with the same kinds of thing, and a compound API would
 * publish three ReactNode rows no Storybook control can drive — for a frame with no variants worth
 * exploring.
 */
export function Section({
	title,
	description,
	actions,
	level = 2,
	size,
	className,
	children,
	...props
}: SectionProps) {
	const headingId = useId();
	const Heading = level === 2 ? "h2" : "h3";

	return (
		<section aria-labelledby={headingId} className={cn("space-y-3", className)} {...props}>
			<div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
				<div className="min-w-0 space-y-1">
					<Heading id={headingId} className={cn(sectionTitleVariants({ size }), "break-words")}>
						{title}
					</Heading>
					{description && <p className="max-w-2xl text-sm text-muted-foreground">{description}</p>}
				</div>
				{actions}
			</div>
			{children}
		</section>
	);
}
