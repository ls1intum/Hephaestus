import { Link } from "@tanstack/react-router";
import { Hammer } from "lucide-react";
import type { ReactNode } from "react";
import { SignInButtons } from "@/components/auth/SignInButtons";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

/** Friendly, PII-free copy for the OAuth error codes the server appends to `?error=`. */
const ERROR_COPY: Record<string, { title: string; description: string }> = {
	access_denied: {
		title: "Sign-in was cancelled",
		description: "No problem — you can try again whenever you're ready.",
	},
	idp_unavailable: {
		title: "That provider isn't responding",
		description: "We couldn't reach it just now. Give it a moment and try again.",
	},
};

function describeError(code: string): { title: string; description: string } {
	return (
		ERROR_COPY[code] ?? {
			title: "Something went wrong",
			description: "We couldn't sign you in. Please try again.",
		}
	);
}

interface LoginCardProps {
	/** Card heading. */
	title: string;
	/** Optional sub-heading rendered under the title. */
	description?: ReactNode;
	/** Optional error code from the `?error=` search param. */
	error?: string;
	/** Called with the chosen provider's registration id. */
	onSignIn: (registrationId: string) => void;
	/** Destination after a successful dev sign-in (only used when the server advertises the dev provider). */
	devReturnTo?: string;
}

/**
 * Focused, full-viewport sign-in screen shared by the global and workspace-scoped login routes. Rendered
 * on its own canvas (no app header/footer — see the auth-route branch in the root layout), so it centers
 * cleanly and never duplicates the header's sign-in buttons. Shows branded provider buttons and an
 * accessible `aria-live` error alert.
 */
export function LoginCard({ title, description, error, onSignIn, devReturnTo }: LoginCardProps) {
	const errorCopy = error ? describeError(error) : undefined;

	return (
		<div className="flex min-h-svh flex-col items-center justify-center gap-6 bg-background p-6 md:p-10">
			<Link
				to="/"
				aria-label="Hephaestus home"
				className="flex items-center gap-2 font-medium hover:opacity-80"
			>
				<Hammer className="size-5" />
				<span className="text-lg font-semibold tracking-tight">Hephaestus</span>
			</Link>

			<Card className="w-full max-w-sm">
				<CardHeader className="text-center">
					<CardTitle className="text-xl">{title}</CardTitle>
					{description ? <CardDescription>{description}</CardDescription> : null}
				</CardHeader>
				<CardContent className="flex flex-col gap-4">
					{/* aria-live region: announces sign-in errors to assistive tech without a focus jump. */}
					<div aria-live="assertive" aria-atomic="true">
						{errorCopy ? (
							<Alert variant="destructive">
								<AlertTitle>{errorCopy.title}</AlertTitle>
								<AlertDescription>{errorCopy.description}</AlertDescription>
							</Alert>
						) : null}
					</div>
					<SignInButtons onSignIn={onSignIn} devReturnTo={devReturnTo} />
				</CardContent>
			</Card>

			<Link to="/" className="text-sm text-muted-foreground hover:text-foreground">
				← Back to home
			</Link>
		</div>
	);
}
