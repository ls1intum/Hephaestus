import { cn } from "@/lib/utils";

interface SpinnerProps extends React.HTMLAttributes<HTMLSpanElement> {
	size?: "sm" | "default" | "lg";
}

export function Spinner({
	size = "default",
	className,
	...props
}: SpinnerProps) {
	return (
		<span
			className={cn(
				"inline-block animate-spin rounded-full border-2 border-solid border-current border-r-transparent motion-reduce:animate-[spin_1.5s_linear_infinite]",
				{
					"h-4 w-4": size === "sm",
					"h-6 w-6": size === "default",
					"h-8 w-8": size === "lg",
				},
				className,
			)}
			aria-busy
			{...props}
		>
			<span className="sr-only">Loading...</span>
		</span>
	);
}
