import { Link } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import {
	ConsentBanner,
	type ConsentBannerProps,
	type ConsentCategory,
	type ConsentCategoryKey,
} from "@/components/consent/ConsentBanner";
import {
	analyticsConfigured,
	type ConsentChoice,
	closeConsentReopen,
	errorMonitoringConfigured,
	optionalIntegrationsAvailable,
	setStoredConsent,
	useConsentReopenRequested,
	useCookieConsent,
} from "@/integrations/consent";

/** The optional categories offered, in display order, limited to those configured in this deployment. */
const ALL_CATEGORIES: ConsentCategory[] = [
	{
		key: "analytics",
		name: "Usage analytics",
		description: "Helps us see how the app is used so we can improve it.",
	},
	{
		key: "errorMonitoring",
		name: "Error reports",
		description: "Tell us when something breaks so we can fix it.",
	},
];

function configuredCategories(): ConsentCategory[] {
	return ALL_CATEGORIES.filter((category) =>
		category.key === "analytics" ? analyticsConfigured : errorMonitoringConfigured,
	);
}

/**
 * Container for the cookie-consent banner. Owns the consent store, the configured-category set, the
 * edit/open state machine, and focus management; delegates all rendering to {@link ConsentBanner}.
 *
 * The banner appears only when an optional, consent-gated integration is configured — with essential
 * cookies alone, no consent is required (ePrivacy Art. 5(3) / German TDDDG §25), so the whole surface
 * stays hidden. It shows on first visit (no stored decision) and on an explicit re-open from the
 * footer/settings ("Cookie preferences"), pre-seeded and cancelable.
 */
export function CookieConsentBanner() {
	const consent = useCookieConsent();
	const reopen = useConsentReopenRequested();
	const open = optionalIntegrationsAvailable && (consent === null || reopen);

	if (!open) {
		return null;
	}

	// Mounted per appearance, so the toggles start from the decision being revisited (revisiting
	// preferences never silently drops a choice) without ever being re-seeded in place.
	return <ConsentForm decision={consent} reopened={reopen} />;
}

interface ConsentFormProps {
	/** The decision being revisited, or `null` on a first visit. Seeds the toggles. */
	decision: ConsentChoice | null;
	/** True when the banner was explicitly re-opened (vs appearing passively on a first visit). */
	reopened: boolean;
}

function ConsentForm({ decision, reopened }: ConsentFormProps) {
	const [values, setValues] = useState<Record<ConsentCategoryKey, boolean>>({
		analytics: decision?.analytics ?? false,
		errorMonitoring: decision?.errorMonitoring ?? false,
	});
	const cardRef = useRef<HTMLDivElement>(null);

	// An explicit reopen moves focus to the banner; a passive first visit must not steal it.
	useEffect(() => {
		if (reopened) {
			cardRef.current?.focus();
		}
	}, [reopened]);

	// Saving/closing unmounts the banner; move focus to the main landmark first so the next Tab lands
	// somewhere sensible instead of falling back to <body>.
	const restoreFocusToMain = () => {
		const main = document.querySelector<HTMLElement>("main");
		if (main) {
			if (main.tabIndex < 0 && !main.hasAttribute("tabindex")) {
				main.tabIndex = -1;
			}
			main.focus();
		}
	};

	const decide = (choice: Record<ConsentCategoryKey, boolean>) => {
		// Only ever grant a category that is actually configured; unconfigured stays false.
		setStoredConsent({
			analytics: analyticsConfigured && choice.analytics,
			errorMonitoring: errorMonitoringConfigured && choice.errorMonitoring,
		});
		restoreFocusToMain();
	};

	const handlers: Pick<
		ConsentBannerProps,
		"onToggle" | "onAcceptAll" | "onRejectAll" | "onSave" | "onCancel"
	> = {
		onToggle: (key, value) => setValues((prev) => ({ ...prev, [key]: value })),
		onAcceptAll: () => decide({ analytics: true, errorMonitoring: true }),
		onRejectAll: () => decide({ analytics: false, errorMonitoring: false }),
		onSave: () => decide(values),
		onCancel: () => {
			closeConsentReopen();
			restoreFocusToMain();
		},
	};

	return (
		<ConsentBanner
			ref={cardRef}
			categories={configuredCategories()}
			values={values}
			editing={decision !== null}
			privacyPolicy={
				<Link to="/privacy" className="underline underline-offset-4 hover:text-foreground">
					Read our Privacy Policy
				</Link>
			}
			{...handlers}
		/>
	);
}
