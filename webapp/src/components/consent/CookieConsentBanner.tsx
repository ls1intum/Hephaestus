import { useEffect, useId, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import {
	closeConsentReopen,
	consumeReopenSeed,
	setStoredConsent,
	useConsentReopenRequested,
	useCookieConsent,
} from "@/integrations/consent";

/**
 * Cookie consent banner (English only, no external dependency). Shown at the bottom of the
 * viewport until a decision is stored, and re-opened in edit mode via "Cookie preferences".
 *
 * Categories:
 *  - Essential (always on, informational): session, CSRF, OAuth-state cookies.
 *  - Analytics (opt-in): PostHog.
 *  - Error monitoring (opt-in): Sentry.
 *
 * Storing a decision gates initialization of PostHog and Sentry (see `main.tsx`). "Accept all" and
 * "Reject all" carry equal visual weight (EDPB/CNIL equal-prominence); granular toggles + "Save
 * choices" sit alongside. It is a non-modal `role="region"` (not a focus-trapping dialog) so it never
 * blocks the page, but it is announced and reachable by keyboard/AT.
 */
export function CookieConsentBanner() {
	const consent = useCookieConsent();
	const reopen = useConsentReopenRequested();
	const editing = consent !== null; // reopened to change an existing decision (vs first visit)
	const open = consent === null || reopen;

	const [analytics, setAnalytics] = useState(false);
	const [errorMonitoring, setErrorMonitoring] = useState(false);
	const titleId = useId();
	const descriptionId = useId();
	const analyticsId = useId();
	const errorMonitoringId = useId();
	const cardRef = useRef<HTMLDivElement>(null);

	// On an explicit reopen, pre-seed the toggles from the prior decision (so revisiting never
	// silently drops a choice) and move focus to the banner. A passive first visit does neither.
	useEffect(() => {
		if (reopen) {
			const seed = consumeReopenSeed();
			setAnalytics(seed?.analytics ?? false);
			setErrorMonitoring(seed?.errorMonitoring ?? false);
			cardRef.current?.focus();
		}
	}, [reopen]);

	// Reserve space at the bottom of the page while the fixed banner is shown so it never occludes
	// the footer Privacy/Imprint links a first-visit user must read before consenting.
	useEffect(() => {
		if (!open) return;
		const apply = () => {
			const height = cardRef.current?.offsetHeight ?? 0;
			document.body.style.paddingBottom = height > 0 ? `${height + 32}px` : "";
		};
		apply();
		window.addEventListener("resize", apply);
		return () => {
			window.removeEventListener("resize", apply);
			document.body.style.paddingBottom = "";
		};
	}, [open]);

	// Saving/closing unmounts the banner; move focus to the main landmark first so the next Tab
	// lands somewhere sensible instead of falling back to <body>.
	const restoreFocusToMain = () => {
		const main = document.querySelector<HTMLElement>("main");
		if (main) {
			if (main.tabIndex < 0 && !main.hasAttribute("tabindex")) {
				main.tabIndex = -1;
			}
			main.focus();
		}
	};

	const decide = (choice: { analytics: boolean; errorMonitoring: boolean }) => {
		setStoredConsent(choice); // also closes the reopen
		restoreFocusToMain();
	};

	const cancelEdit = () => {
		closeConsentReopen();
		restoreFocusToMain();
	};

	if (!open) {
		return null;
	}

	return (
		<div className="fixed inset-x-0 bottom-0 z-50 flex justify-center p-4">
			<Card
				ref={cardRef}
				tabIndex={-1}
				role="region"
				aria-live="polite"
				aria-labelledby={titleId}
				aria-describedby={descriptionId}
				className="w-full max-w-2xl shadow-lg outline-none"
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
						{editing && (
							<Button variant="ghost" className="sm:mr-auto" onClick={cancelEdit}>
								Cancel
							</Button>
						)}
						{/* Equal prominence (EDPB/CNIL): Reject all and Accept all share the same weight. */}
						<Button onClick={() => decide({ analytics: false, errorMonitoring: false })}>
							Reject all
						</Button>
						<Button variant="outline" onClick={() => decide({ analytics, errorMonitoring })}>
							Save choices
						</Button>
						<Button onClick={() => decide({ analytics: true, errorMonitoring: true })}>
							Accept all
						</Button>
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
