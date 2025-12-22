import { type ComponentPropsWithoutRef, forwardRef, type SVGAttributes } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export type GitHubSignInButtonProps = ComponentPropsWithoutRef<typeof Button>;

/**
 * GitHub-branded sign-in button built from the official mark and label guidance.
 * See https://github.com/logos for usage rules.
 */
export const GitHubSignInButton = forwardRef<HTMLButtonElement, GitHubSignInButtonProps>(
	({ className, children, size = "default", ...props }, ref) => {
		return (
			<Button
				ref={ref}
				size={size}
				className="bg-github-black text-github-white dark:bg-github-white dark:text-github-black"
				{...props}
			>
				<GitHubMarkIcon className="!text-github-white dark:!text-github-black" />
				<span className="text-base font-semibold tracking-tight">
					{children ?? "Sign in with GitHub"}
				</span>
			</Button>
		);
	},
);

GitHubSignInButton.displayName = "GitHubSignInButton";

function GitHubMarkIcon({ className, ...props }: SVGAttributes<SVGSVGElement>) {
	return (
		<svg
			viewBox="0 0 98 96"
			fill="currentColor"
			className={cn("h-5 w-5 shrink-0", className)}
			focusable="false"
			aria-hidden="true"
			{...props}
		>
			<path
				fillRule="evenodd"
				clipRule="evenodd"
				d="M49 0C21.914 0 0 22 0 49.2c0 21.754 14.026 40.187 33.492 46.73 2.45.455 3.356-1.067 3.356-2.374 0-1.175-.045-5.037-.073-9.127-13.632 2.965-16.516-5.718-16.516-5.718-2.23-5.734-5.45-7.26-5.45-7.26-4.457-3.047.334-2.99.334-2.99 4.934.35 7.526 5.156 7.526 5.156 4.377 7.496 11.486 5.327 14.29 4.073.443-3.206 1.712-5.327 3.11-6.553-10.885-1.25-22.323-5.485-22.323-24.409 0-5.393 1.918-9.8 5.063-13.25-.51-1.25-2.2-6.282.48-13.091 0 0 4.145-1.326 13.59 5.06a47.355 47.355 0 0 1 12.363-1.662c4.2.024 8.432.57 12.363 1.662 9.446-6.385 13.58-5.06 13.58-5.06 2.69 6.809.998 11.84.488 13.09 3.155 3.45 5.064 7.858 5.064 13.25 0 18.967-11.454 23.144-22.35 24.36 1.759 1.527 3.321 4.5 3.321 9.08 0 6.558-.058 11.85-.058 13.46 0 1.32.892 2.858 3.37 2.37C83.98 89.42 98 71 98 49.24 98 22 76.085 0 49 0Z"
			/>
		</svg>
	);
}
