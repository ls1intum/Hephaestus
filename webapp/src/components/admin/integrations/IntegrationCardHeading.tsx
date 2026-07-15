import type * as React from "react";

import { cn } from "@/lib/utils";

/**
 * Semantic `<h2>` card title for the integrations admin surface.
 *
 * `CardTitle` renders a `<div>` and can't take a `render` prop, but these settings pages are
 * navigated by heading, so each section title has to be a real `<h2>` under the page `<h1>`.
 * Mirrors `CardTitle`'s styling so the two are visually identical — use inside `CardHeader`.
 */
export function IntegrationCardHeading({ className, ...props }: React.ComponentProps<"h2">) {
	return (
		<h2
			data-slot="card-title"
			className={cn("text-base leading-snug font-medium", className)}
			{...props}
		/>
	);
}
