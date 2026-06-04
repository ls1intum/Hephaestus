import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { requestConsentReopen, useCookieConsent } from "@/integrations/consent";

/**
 * Lets a signed-in user revisit the cookie decision they made in the consent banner. Clearing the
 * stored decision re-opens the banner (and disables PostHog/Sentry until a fresh choice is made),
 * which is the withdrawal path GDPR Art. 7(3) requires to be as easy as granting consent.
 */
export function CookiePreferencesSection() {
	const consent = useCookieConsent();

	const summary = consent
		? [
				`Analytics ${consent.analytics ? "on" : "off"}`,
				`Error monitoring ${consent.errorMonitoring ? "on" : "off"}`,
			].join(" · ")
		: "No optional cookies — using essential cookies only.";

	return (
		<section className="space-y-4" aria-labelledby="cookie-preferences-heading">
			<div className="space-y-1">
				<h2 id="cookie-preferences-heading" className="text-xl font-semibold">
					Privacy &amp; cookies
				</h2>
				<p className="text-sm text-muted-foreground">
					Essential cookies keep you signed in and secure. You can change your choice about optional
					analytics and error-monitoring cookies at any time.
				</p>
			</div>
			<div className="flex items-center justify-between gap-4">
				<p className="text-sm text-muted-foreground">{summary}</p>
				<Button
					variant="outline"
					className="shrink-0"
					onClick={() => {
						requestConsentReopen();
						toast.success("Adjust your cookie choices in the banner that just appeared.");
					}}
				>
					Change cookie choices
				</Button>
			</div>
		</section>
	);
}
