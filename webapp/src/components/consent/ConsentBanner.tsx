import { type ReactNode, type Ref, useId } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";

export type ConsentCategoryKey = "analytics" | "errorMonitoring";

export interface ConsentCategory {
	key: ConsentCategoryKey;
	/** Human label, e.g. "Usage analytics". */
	name: string;
	/** One-line plain-language purpose. */
	description: string;
}

export interface ConsentBannerProps {
	/** Configured optional categories (1 or 2). Drives the body copy and the button set. */
	categories: ConsentCategory[];
	/** Current toggle values per category (only shown when two categories are configured). */
	values: Record<ConsentCategoryKey, boolean>;
	/** True when re-opened to change an existing decision — adds a Cancel action. */
	editing: boolean;
	onToggle: (key: ConsentCategoryKey, value: boolean) => void;
	/** Grant every configured category. */
	onAcceptAll: () => void;
	/** Decline every optional category. */
	onRejectAll: () => void;
	/** Save the current per-category toggle values (two-category mode only). */
	onSave: () => void;
	/** Leave an existing decision untouched (editing mode only). */
	onCancel: () => void;
	/** Privacy-policy link, passed as a slot so this component stays router-agnostic (storyable). */
	privacyPolicy?: ReactNode;
	ref?: Ref<HTMLDivElement>;
}

/** Body sentence describing the optional cookies on offer, tailored to what's configured. */
function describeOptional(categories: ConsentBannerProps["categories"]): string {
	const hasAnalytics = categories.some((c) => c.key === "analytics");
	const hasErrors = categories.some((c) => c.key === "errorMonitoring");
	if (hasAnalytics && hasErrors) {
		return "With your permission, we'd also like to use optional cookies to understand how the app is used and to catch errors.";
	}
	if (hasAnalytics) {
		return "With your permission, we'd also like to learn how the app is used so we can improve it.";
	}
	return "With your permission, we'd also like to send error reports so we can fix problems faster.";
}

/**
 * Presentational cookie-consent banner — pure and prop-driven (no store, no DOM side effects), so
 * every state is reachable in Storybook. The container ({@link CookieConsentBanner}) owns the store,
 * focus management, and which categories are configured.
 *
 * Accessibility: a non-modal `role="region"` (announced via `aria-live`, reachable by keyboard) — it
 * never traps focus or blocks the page, which a consent prompt must not do. "Accept" and "Reject"
 * carry equal visual weight (EDPB/CNIL equal-prominence). Optional toggles are off unless granted.
 */
export function ConsentBanner({
	categories,
	values,
	editing,
	onToggle,
	onAcceptAll,
	onRejectAll,
	onSave,
	onCancel,
	privacyPolicy,
	ref,
}: ConsentBannerProps) {
	const titleId = useId();
	const descriptionId = useId();
	const granular = categories.length > 1;

	return (
		<div className="fixed inset-x-0 bottom-0 z-50 flex justify-center p-4">
			<Card
				ref={ref}
				tabIndex={-1}
				role="region"
				aria-live="polite"
				aria-labelledby={titleId}
				aria-describedby={descriptionId}
				className="w-full max-w-2xl shadow-lg outline-none"
			>
				<CardHeader>
					<CardTitle id={titleId}>Your privacy</CardTitle>
					<CardDescription id={descriptionId}>
						Hephaestus uses essential cookies to keep you signed in and secure.{" "}
						{describeOptional(categories)} You can change this anytime.
						{privacyPolicy ? <> {privacyPolicy}</> : null}
					</CardDescription>
				</CardHeader>
				<CardContent className="space-y-4">
					{granular && (
						<div className="space-y-3">
							<div className="flex items-start justify-between gap-4">
								<div className="space-y-0.5">
									<p className="font-medium">Essential</p>
									<p className="text-muted-foreground text-sm">
										Keep you signed in and protect your account. Always on.
									</p>
								</div>
								<Switch checked disabled aria-label="Essential cookies (always on)" />
							</div>

							{categories.map((category) => {
								const switchId = `consent-${category.key}`;
								return (
									<div key={category.key} className="flex items-start justify-between gap-4">
										<label htmlFor={switchId} className="space-y-0.5 cursor-pointer">
											<p className="font-medium">{category.name}</p>
											<p className="text-muted-foreground text-sm">{category.description}</p>
										</label>
										<Switch
											id={switchId}
											checked={values[category.key]}
											onCheckedChange={(checked) => onToggle(category.key, checked)}
											aria-label={category.name}
										/>
									</div>
								);
							})}
						</div>
					)}

					<div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
						{editing && (
							<Button variant="ghost" className="sm:mr-auto" onClick={onCancel}>
								Cancel
							</Button>
						)}
						{/* Equal prominence (EDPB/CNIL): decline and accept share the same weight. */}
						{granular ? (
							<>
								<Button onClick={onRejectAll}>Reject all</Button>
								<Button variant="outline" onClick={onSave}>
									Save choices
								</Button>
								<Button onClick={onAcceptAll}>Accept all</Button>
							</>
						) : (
							<>
								<Button onClick={onRejectAll}>Decline</Button>
								<Button onClick={onAcceptAll}>Allow</Button>
							</>
						)}
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
