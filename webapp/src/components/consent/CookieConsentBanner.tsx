import { useId, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { setStoredConsent, useCookieConsent } from "@/integrations/consent";

/**
 * Cookie consent banner (English only, no external dependency). Shown at the bottom of the
 * viewport until a decision is stored in localStorage.
 *
 * Categories:
 *  - Essential (always on, informational): session, CSRF, OAuth-state cookies.
 *  - Analytics (opt-in): PostHog.
 *  - Error monitoring (opt-in): Sentry.
 *
 * Storing a decision gates initialization of PostHog and Sentry (see `main.tsx`). Offers
 * "Accept all", "Reject non-essential", and per-category toggles before saving.
 */
export function CookieConsentBanner() {
	const consent = useCookieConsent();
	const [analytics, setAnalytics] = useState(false);
	const [errorMonitoring, setErrorMonitoring] = useState(false);
	const titleId = useId();
	const descriptionId = useId();
	const analyticsId = useId();
	const errorMonitoringId = useId();

	// Saving a decision unmounts the banner; without intervention focus falls back to <body>,
	// stranding keyboard/AT users. Move focus to the main landmark (or its first focusable child)
	// before the unmount so the next Tab lands somewhere sensible.
	const decideAndRestoreFocus = (choice: { analytics: boolean; errorMonitoring: boolean }) => {
		const main = document.querySelector<HTMLElement>("main");
		setStoredConsent(choice);
		if (main) {
			if (main.tabIndex < 0 && !main.hasAttribute("tabindex")) {
				main.tabIndex = -1;
			}
			main.focus();
		}
	};

	// A decision has been made — banner stays hidden until consent is cleared.
	if (consent !== null) {
		return null;
	}

	return (
		<div className="fixed inset-x-0 bottom-0 z-50 flex justify-center p-4">
			<Card
				role="dialog"
				aria-modal="false"
				aria-labelledby={titleId}
				aria-describedby={descriptionId}
				className="w-full max-w-2xl shadow-lg"
			>
				<CardHeader>
					<CardTitle id={titleId}>Cookies &amp; privacy</CardTitle>
					<CardDescription id={descriptionId}>
						We use essential cookies to keep you signed in and secure. With your consent we also use
						optional cookies for product analytics and error monitoring. You can change your choice
						at any time.
					</CardDescription>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="space-y-3">
						<div className="flex items-start justify-between gap-4">
							<div className="space-y-0.5">
								<p className="font-medium">Essential</p>
								<p className="text-muted-foreground text-sm">
									Required for sign-in, CSRF protection, and the OAuth flow. Always on.
								</p>
							</div>
							<Switch checked disabled aria-label="Essential cookies (always on)" />
						</div>

						<div className="flex items-start justify-between gap-4">
							<label htmlFor={analyticsId} className="space-y-0.5 cursor-pointer">
								<p className="font-medium">Analytics</p>
								<p className="text-muted-foreground text-sm">
									Product analytics via PostHog to help us improve Hephaestus.
								</p>
							</label>
							<Switch
								id={analyticsId}
								checked={analytics}
								onCheckedChange={setAnalytics}
								aria-label="Allow analytics cookies (PostHog)"
							/>
						</div>

						<div className="flex items-start justify-between gap-4">
							<label htmlFor={errorMonitoringId} className="space-y-0.5 cursor-pointer">
								<p className="font-medium">Error monitoring</p>
								<p className="text-muted-foreground text-sm">
									Error monitoring via Sentry so we can detect and fix problems.
								</p>
							</label>
							<Switch
								id={errorMonitoringId}
								checked={errorMonitoring}
								onCheckedChange={setErrorMonitoring}
								aria-label="Allow error monitoring cookies (Sentry)"
							/>
						</div>
					</div>

					<div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
						<Button
							variant="outline"
							onClick={() => decideAndRestoreFocus({ analytics: false, errorMonitoring: false })}
						>
							Reject non-essential
						</Button>
						<Button
							variant="outline"
							onClick={() => decideAndRestoreFocus({ analytics, errorMonitoring })}
						>
							Save choices
						</Button>
						<Button
							onClick={() => decideAndRestoreFocus({ analytics: true, errorMonitoring: true })}
						>
							Accept all
						</Button>
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
