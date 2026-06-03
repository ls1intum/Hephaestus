import { useQuery } from "@tanstack/react-query";
import type { ComponentPropsWithoutRef } from "react";
import { listIdentityProvidersOptions } from "@/api/@tanstack/react-query.gen";
import type { IdentityProviderView } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { GitHubSignInButton } from "./GitHubSignInButton";
import { GitLabMarkIcon, GitLabSignInButton } from "./GitLabSignInButton";

type ButtonSize = ComponentPropsWithoutRef<typeof Button>["size"];

interface SignInButtonsProps {
	onSignIn: (idpHint: string) => void;
	disabled?: boolean;
	size?: ButtonSize;
	className?: string;
	/**
	 * Header mode: branded icon + short name on desktop, icon-only on mobile.
	 */
	header?: boolean;
}

/** GitHub mark SVG — shared between full and header buttons */
function GitHubMark({ className }: { className?: string }) {
	return (
		<svg
			viewBox="0 0 98 96"
			fill="currentColor"
			className={className ?? "h-5 w-5 shrink-0"}
			focusable="false"
			aria-hidden="true"
		>
			<path
				fillRule="evenodd"
				clipRule="evenodd"
				d="M49 0C21.914 0 0 22 0 49.2c0 21.754 14.026 40.187 33.492 46.73 2.45.455 3.356-1.067 3.356-2.374 0-1.175-.045-5.037-.073-9.127-13.632 2.965-16.516-5.718-16.516-5.718-2.23-5.734-5.45-7.26-5.45-7.26-4.457-3.047.334-2.99.334-2.99 4.934.35 7.526 5.156 7.526 5.156 4.377 7.496 11.486 5.327 14.29 4.073.443-3.206 1.712-5.327 3.11-6.553-10.885-1.25-22.323-5.485-22.323-24.409 0-5.393 1.918-9.8 5.063-13.25-.51-1.25-2.2-6.282.48-13.091 0 0 4.145-1.326 13.59 5.06a47.355 47.355 0 0 1 12.363-1.662c4.2.024 8.432.57 12.363 1.662 9.446-6.385 13.58-5.06 13.58-5.06 2.69 6.809.998 11.84.488 13.09 3.155 3.45 5.064 7.858 5.064 13.25 0 18.967-11.454 23.144-22.35 24.36 1.759 1.527 3.321 4.5 3.321 9.08 0 6.558-.058 11.85-.058 13.46 0 1.32.892 2.858 3.37 2.37C83.98 89.42 98 71 98 49.24 98 22 76.085 0 49 0Z"
			/>
		</svg>
	);
}

/** Shared hover style for branded header buttons */
const headerButtonBase =
	"gap-2 border-0 transition-all motion-safe:hover:scale-[1.02] motion-safe:active:scale-[0.98]";

function HeaderProviderButton({
	provider,
	onSignIn,
	disabled,
}: {
	provider: IdentityProviderView;
	onSignIn: (idpHint: string) => void;
	disabled?: boolean;
}) {
	const registrationId = provider.registrationId ?? "";
	const label = provider.displayName ?? registrationId;
	const handleClick = () => onSignIn(registrationId);
	const isGitHub = provider.providerType?.toUpperCase() === "GITHUB";
	const isGitLab = registrationId.startsWith("gitlab");

	const brandClass = isGitHub
		? "bg-github-black text-github-white dark:bg-github-white dark:text-github-black hover:shadow-lg hover:shadow-github-black/20 dark:hover:shadow-github-white/20"
		: isGitLab
			? "bg-gitlab-orange text-white hover:shadow-lg hover:shadow-gitlab-orange/30"
			: "";

	const icon = isGitHub ? (
		<GitHubMark
			className={`h-5 w-5 shrink-0 ${isGitHub ? "!text-github-white dark:!text-github-black" : ""}`}
		/>
	) : isGitLab ? (
		<GitLabMarkIcon />
	) : null;

	return (
		<Tooltip>
			<TooltipTrigger
				render={
					<Button
						onClick={handleClick}
						disabled={disabled}
						className={`${headerButtonBase} ${brandClass}`}
					/>
				}
			>
				{icon}
				<span className="hidden sm:inline text-sm font-semibold tracking-tight">{label}</span>
			</TooltipTrigger>
			<TooltipContent className="sm:hidden">Continue with {label}</TooltipContent>
		</Tooltip>
	);
}

function ProviderButton({
	provider,
	onSignIn,
	disabled,
	size,
	className,
}: {
	provider: IdentityProviderView;
	onSignIn: (idpHint: string) => void;
	disabled?: boolean;
	size?: ButtonSize;
	className?: string;
}) {
	const registrationId = provider.registrationId ?? "";
	const label = provider.displayName ?? registrationId;
	const handleClick = () => onSignIn(registrationId);

	if (provider.providerType?.toUpperCase() === "GITHUB") {
		return (
			<GitHubSignInButton
				onClick={handleClick}
				disabled={disabled}
				size={size}
				className={className}
			>
				Continue with {label}
			</GitHubSignInButton>
		);
	}

	if (registrationId.startsWith("gitlab")) {
		return (
			<GitLabSignInButton
				onClick={handleClick}
				disabled={disabled}
				size={size}
				className={className}
			>
				Continue with {label}
			</GitLabSignInButton>
		);
	}

	return (
		<Button onClick={handleClick} disabled={disabled} size={size} className={className}>
			Continue with {label}
		</Button>
	);
}

/**
 * Renders sign-in buttons for all enabled identity providers.
 * Fetches the list from the backend at /auth/identity-providers.
 *
 * - Default: full branded buttons with "Sign in with …" labels (landing pages).
 * - `header`: branded icon + short name on desktop, icon-only on mobile.
 *   Uses consistent `brightness` hover across all providers.
 */
export function SignInButtons({ onSignIn, disabled, size, className, header }: SignInButtonsProps) {
	const { data: providers, isLoading } = useQuery({
		...listIdentityProvidersOptions(),
		staleTime: 5 * 60 * 1000,
	});

	// While loading or on error, show a GitHub button as fallback
	if (isLoading || !providers) {
		if (header) {
			return (
				<Button
					disabled={disabled}
					onClick={() => onSignIn("github")}
					className={`${headerButtonBase} bg-github-black text-github-white dark:bg-github-white dark:text-github-black hover:shadow-lg hover:shadow-github-black/20 dark:hover:shadow-github-white/20`}
				>
					<GitHubMark className="h-5 w-5 shrink-0 !text-github-white dark:!text-github-black" />
					<span className="hidden sm:inline text-sm font-semibold tracking-tight">GitHub</span>
				</Button>
			);
		}
		return (
			<GitHubSignInButton
				onClick={() => onSignIn("github")}
				disabled={disabled}
				size={size}
				className={className}
			/>
		);
	}

	if (header) {
		return (
			<div className="flex items-center gap-2">
				{providers.map((provider) => (
					<HeaderProviderButton
						key={provider.registrationId ?? provider.displayName}
						provider={provider}
						onSignIn={onSignIn}
						disabled={disabled}
					/>
				))}
			</div>
		);
	}

	return (
		<div className="flex flex-wrap items-center gap-2">
			{providers.map((provider) => (
				<ProviderButton
					key={provider.registrationId ?? provider.displayName}
					provider={provider}
					onSignIn={onSignIn}
					disabled={disabled}
					size={size}
					className={className}
				/>
			))}
		</div>
	);
}
