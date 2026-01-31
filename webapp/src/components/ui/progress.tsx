import { Progress as ProgressPrimitive } from "@base-ui/react/progress";
import type { ClassValue } from "clsx";
import type * as React from "react";

import { cn } from "@/lib/utils";

interface ProgressProps extends React.ComponentProps<typeof ProgressPrimitive.Root> {
	indicatorClassName?: ClassValue;
}

function Progress({ className, indicatorClassName, value, ...props }: ProgressProps) {
	return (
		<ProgressPrimitive.Root
			data-slot="progress"
			value={value}
			className={cn("bg-primary/20 relative h-2 w-full overflow-hidden rounded-full", className)}
			{...props}
		>
			<ProgressPrimitive.Track className="h-full w-full">
				<ProgressPrimitive.Indicator
					data-slot="progress-indicator"
					className={cn("bg-primary h-full w-full flex-1 transition-all", indicatorClassName)}
					style={{ transform: `translateX(-${100 - (value || 0)}%)` }}
				/>
			</ProgressPrimitive.Track>
		</ProgressPrimitive.Root>
	);
}

export { Progress };
