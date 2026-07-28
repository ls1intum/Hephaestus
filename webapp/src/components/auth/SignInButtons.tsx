import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { LogInIcon } from "lucide-react";
import type { ComponentPropsWithoutRef, SVGAttributes } from "react";
import { listIdentityProvidersOptions } from "@/api/@tanstack/react-query.gen";
import type { IdentityProviderView } from "@/api/types.gen";
import { DevSignInForm } from "@/components/auth/DevSignInForm";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

type ButtonSize = ComponentPropsWithoutRef<typeof Button>["size"];

/** Synthetic provider type the server emits for the optional passwordless dev sign-in. */
const DEV_PROVIDER_TYPE = "DEV";
const LINK_ONLY_PROVIDER_TYPES = new Set(["SLACK", "OUTLINE"]);

interface SignInButtonsProps {
	onSignIn: (idpHint: string) => void;
	disabled?: boolean;
	size?: ButtonSize;
	className?: string;
	/** Header mode: compact buttons (icon + short name on desktop, icon-only on mobile). */
	header?: boolean;
	/** Destination after a successful dev sign-in (full mode only). Defaults to home. */
	devReturnTo?: string;
	/**
	 * Prefer providers of this type (e.g. `"GITHUB"`) — used by the step-up challenge, where signing in
	 * with a different provider switches the admin's account instead of confirming it. Best-effort: if
	 * it matches no sign-in provider (a link-only or unknown primary identity), the full list is shown
	 * rather than an unusable empty dialog.
	 */
	onlyProviderType?: string;
}

/** GitHub mark SVG. Sized by the Button's `[&_svg]` rule; kept monochrome (currentColor). */
function GitHubMark(props: SVGAttributes<SVGSVGElement>) {
	return (
		<svg viewBox="0 0 98 96" fill="currentColor" focusable="false" aria-hidden="true" {...props}>
			<path
				fillRule="evenodd"
				clipRule="evenodd"
				d="M49 0C21.914 0 0 22 0 49.2c0 21.754 14.026 40.187 33.492 46.73 2.45.455 3.356-1.067 3.356-2.374 0-1.175-.045-5.037-.073-9.127-13.632 2.965-16.516-5.718-16.516-5.718-2.23-5.734-5.45-7.26-5.45-7.26-4.457-3.047.334-2.99.334-2.99 4.934.35 7.526 5.156 7.526 5.156 4.377 7.496 11.486 5.327 14.29 4.073.443-3.206 1.712-5.327 3.11-6.553-10.885-1.25-22.323-5.485-22.323-24.409 0-5.393 1.918-9.8 5.063-13.25-.51-1.25-2.2-6.282.48-13.091 0 0 4.145-1.326 13.59 5.06a47.355 47.355 0 0 1 12.363-1.662c4.2.024 8.432.57 12.363 1.662 9.446-6.385 13.58-5.06 13.58-5.06 2.69 6.809.998 11.84.488 13.09 3.155 3.45 5.064 7.858 5.064 13.25 0 18.967-11.454 23.144-22.35 24.36 1.759 1.527 3.321 4.5 3.321 9.08 0 6.558-.058 11.85-.058 13.46 0 1.32.892 2.858 3.37 2.37C83.98 89.42 98 71 98 49.24 98 22 76.085 0 49 0Z"
			/>
		</svg>
	);
}

/** GitLab tanuki mark SVG (monochrome, currentColor — recognisable on a neutral button). */
function GitLabMark(props: SVGAttributes<SVGSVGElement>) {
	return (
		<svg viewBox="0 0 24 24" fill="currentColor" focusable="false" aria-hidden="true" {...props}>
			<path d="m23.6004 9.5927-.0337-.0862L20.3.9814a.851.851 0 0 0-.3362-.405.8748.8748 0 0 0-.9997.0539.8748.8748 0 0 0-.29.4399l-2.2055 6.748H7.5375l-2.2057-6.748a.8573.8573 0 0 0-.29-.4412.8748.8748 0 0 0-.9997-.0537.8585.8585 0 0 0-.3362.4049L.4332 9.5015l-.0325.0862a6.0657 6.0657 0 0 0 2.0119 7.0105l.0113.0087.03.0213 4.976 3.7264 2.462 1.8633 1.4995 1.1321a1.0085 1.0085 0 0 0 1.2197 0l1.4995-1.1321 2.4619-1.8633 5.006-3.7489.0125-.01a6.0682 6.0682 0 0 0 2.0094-7.003z" />
		</svg>
	);
}

/** Recognisable provider mark (icon only — the button itself stays the stock shadcn style). */
function ProviderIcon({ provider }: { provider: IdentityProviderView }) {
	if (provider.providerType?.toUpperCase() === "GITHUB") {
		return <GitHubMark className="shrink-0" />;
	}
	if ((provider.registrationId ?? "").startsWith("gitlab")) {
		return <GitLabMark className="shrink-0" />;
	}
	return null;
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
	return (
		<Button
			variant="outline"
			size={size}
			disabled={disabled}
			onClick={() => onSignIn(registrationId)}
			className={cn("w-full", className)}
		>
			<ProviderIcon provider={provider} />
			Continue with {label}
		</Button>
	);
}

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
	return (
		<Tooltip>
			<TooltipTrigger
				render={
					// Below `sm` the label is hidden and the mark is aria-hidden, so without this the button
					// has NO accessible name (a tooltip does not supply one). Name it explicitly.
					<Button
						variant="outline"
						disabled={disabled}
						aria-label={`Continue with ${label}`}
						onClick={() => onSignIn(registrationId)}
					/>
				}
			>
				<ProviderIcon provider={provider} />
				<span className="hidden sm:inline">{label}</span>
			</TooltipTrigger>
			<TooltipContent className="sm:hidden">Continue with {label}</TooltipContent>
		</Tooltip>
	);
}

/**
 * Renders a stock shadcn `outline` sign-in button per enabled identity provider (brand icon for
 * recognition + "Continue with …" label). The provider list is fetched from `/identity-providers`.
 *
 * - Default: full-width buttons stacked vertically (the login card).
 * - `header`: compact buttons (icon + short name on desktop, icon-only on mobile).
 */
export function SignInButtons({
	onSignIn,
	disabled,
	size,
	className,
	header,
	devReturnTo,
	onlyProviderType,
}: SignInButtonsProps) {
	const {
		data: providers,
		isLoading,
		isError,
	} = useQuery({
		...listIdentityProvidersOptions(),
		staleTime: 5 * 60 * 1000,
	});

	// On a genuine discovery failure we must NOT imply a specific provider works (this instance may be
	// GitLab-only, where a GitHub button leads to a dead OAuth path). Show a neutral, non-misleading state.
	if (isError || (!isLoading && !providers)) {
		if (header) {
			return <span className="text-sm text-muted-foreground">Sign-in unavailable</span>;
		}
		return (
			<p className={cn("text-center text-sm text-muted-foreground", className)}>
				Couldn't load sign-in options. Please refresh and try again.
			</p>
		);
	}

	// While the list loads, show a NON-clickable placeholder — never an optimistic provider button.
	if (isLoading || !providers) {
		if (header) {
			return (
				<Button variant="outline" disabled aria-label="Loading sign-in options">
					<Spinner aria-hidden="true" />
				</Button>
			);
		}
		return (
			<Button
				variant="outline"
				size={size}
				disabled
				aria-label="Loading sign-in options"
				className={cn("w-full", className)}
			>
				<Spinner aria-hidden="true" />
				Loading sign-in options…
			</Button>
		);
	}

	// The dev sign-in needs a username field, so it renders as a small form (full mode only), never as
	// an OAuth-style button — and it is excluded from the compact header entirely.
	const signInProviders = providers.filter((provider) => {
		const type = provider.providerType?.toUpperCase();
		return type !== DEV_PROVIDER_TYPE && !LINK_ONLY_PROVIDER_TYPES.has(type ?? "");
	});
	const preferred = onlyProviderType
		? signInProviders.filter(
				(p) => p.providerType?.toUpperCase() === onlyProviderType.toUpperCase(),
			)
		: signInProviders;
	// Never render an empty list: a primary identity that is link-only (Slack/Outline) or UNKNOWN
	// matches nothing here, and a challenge with no button is a lockout.
	const oauthProviders = preferred.length > 0 ? preferred : signInProviders;
	const hasDevSignIn = providers.some(
		(provider) => provider.providerType?.toUpperCase() === DEV_PROVIDER_TYPE,
	);

	if (header) {
		// The dev sign-in needs a username, and a link-only provider is not a way in — so with neither
		// GitHub nor GitLab configured there is nothing the header can render inline. It must still
		// offer a way in: send the user to the full sign-in page rather than showing an empty header.
		if (oauthProviders.length === 0) {
			return (
				<Button
					variant="outline"
					size="sm"
					disabled={disabled}
					render={<Link to="/login" />}
					aria-label="Sign in"
				>
					<LogInIcon aria-hidden />
					Sign in
				</Button>
			);
		}
		return (
			<div className="flex items-center gap-2">
				{oauthProviders.map((provider) => (
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
		<div className="flex flex-col gap-2">
			{oauthProviders.map((provider) => (
				<ProviderButton
					key={provider.registrationId ?? provider.displayName}
					provider={provider}
					onSignIn={onSignIn}
					disabled={disabled}
					size={size}
					className={className}
				/>
			))}
			{hasDevSignIn ? <DevSignInForm returnTo={devReturnTo} /> : null}
		</div>
	);
}
