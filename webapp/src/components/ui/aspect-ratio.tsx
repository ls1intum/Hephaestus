import type * as React from "react";
import { cn } from "@/lib/utils";

interface AspectRatioProps extends React.ComponentPropsWithoutRef<"div"> {
	ratio?: number;
}

function AspectRatio({ ratio = 1, className, style, ...props }: AspectRatioProps) {
	return (
		<div
			data-slot="aspect-ratio"
			className={cn("relative w-full", className)}
			style={{
				...style,
				aspectRatio: String(ratio),
			}}
			{...props}
		/>
	);
}

export { AspectRatio };
