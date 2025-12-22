import { Loader2Icon } from "lucide-react";

import { cn } from "@/lib/utils";

type SpinnerSize = "sm" | "default" | "lg";

interface SpinnerProps extends React.HTMLAttributes<HTMLDivElement> {
	size?: SpinnerSize;
}

function Spinner({ size = "default", className, ...props }: SpinnerProps) {
	const sizeClass = size === "sm" ? "size-4" : size === "lg" ? "size-8" : "size-6";

	return (
		<div
			data-slot="spinner"
			role="status"
			aria-live="polite"
			className={cn("inline-flex items-center justify-center", className)}
			{...props}
		>
			<Loader2Icon aria-hidden="true" className={cn("animate-spin", sizeClass)} />
			<span className="sr-only">Loading...</span>
		</div>
	);
}

export { Spinner };
