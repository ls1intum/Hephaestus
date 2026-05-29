import type { ReactNode } from "react";
import { SignInButtons } from "@/components/auth/SignInButtons";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

/** Friendly, PII-free copy for the OAuth error codes the server appends to `?error=`. */
const ERROR_COPY: Record<string, { title: string; description: string }> = {
	access_denied: {
		title: "Sign-in was cancelled",
		description: "You declined the authorization request. Try again when you're ready.",
	},
	idp_unavailable: {
		title: "Provider unavailable",
		description: "The identity provider couldn't be reached. Please try again in a moment.",
	},
};

function describeError(code: string): { title: string; description: string } {
	return (
		ERROR_COPY[code] ?? {
			title: "Sign-in failed",
			description: "Something went wrong while signing you in. Please try again.",
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
}

/**
 * Centered sign-in card shared by the global and workspace-scoped login routes.
 * Renders full, branded provider buttons and an accessible `aria-live` error alert.
 */
export function LoginCard({ title, description, error, onSignIn }: LoginCardProps) {
	const errorCopy = error ? describeError(error) : undefined;

	return (
		<div className="flex min-h-[100dvh] items-center justify-center p-4">
			<Card className="w-full max-w-md">
				<CardHeader className="text-center">
					<CardTitle className="text-2xl">{title}</CardTitle>
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
					<div className="flex flex-col gap-3 [&>*]:w-full [&_button]:w-full [&_button]:justify-center">
						<SignInButtons onSignIn={onSignIn} />
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
