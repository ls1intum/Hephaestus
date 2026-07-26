import {
	CircleCheckIcon,
	InfoIcon,
	Loader2Icon,
	OctagonXIcon,
	TriangleAlertIcon,
} from "lucide-react";
import { Toaster as Sonner, type ToasterProps } from "sonner";
import { useTheme } from "@/integrations/theme";

/**
 * Every toast in this app is announced politely, including `toast.error(...)`, and that cannot
 * currently be changed. sonner hard-codes `aria-live="polite"` as a string literal on the container
 * `<section>` and spreads nothing else onto it; `ToasterProps` exposes no politeness prop (only
 * `containerAriaLabel`, which is the label, not the urgency), and each toast's `<li>` receives only
 * `data-*` attributes — no `role`, no `aria-live` — with no arbitrary-prop passthrough on
 * `ExternalToast`. 2.0.7 is both the installed and the latest published version (2025-08-02), and
 * `main` is unchanged on this point, so there is no version to upgrade to. Two `<Toaster>`s *are*
 * possible (`<Toaster id>` plus `toast.error(msg, { toasterId })`, added in 2.0.7), but both
 * instances hard-code the same politeness, so routing errors to a second one buys a position and a
 * label, never a `role="alert"`. An app-owned assertive region fed in parallel would announce every
 * error twice, which is worse than late. The upstream gap is tracked by open PR
 * emilkowalski/sonner#765, "add custom ARIA announcement options to Toaster component" (opened
 * 2026-06-07, unmerged), which makes `aria-live` configurable and would lift this the moment it
 * ships. Polite already satisfies WCAG 2.2 SC 4.1.3 Status Messages; assertive errors are the
 * improvement we are waiting on, not a conformance failure. `toast-politeness.test.tsx` pins the
 * current behaviour and fails when it changes, so this comment cannot quietly go stale.
 */
const Toaster = ({ ...props }: ToasterProps) => {
	const { theme = "system" } = useTheme();

	return (
		<Sonner
			theme={theme as ToasterProps["theme"]}
			className="toaster group"
			icons={{
				success: <CircleCheckIcon className="size-4" />,
				info: <InfoIcon className="size-4" />,
				warning: <TriangleAlertIcon className="size-4" />,
				error: <OctagonXIcon className="size-4" />,
				loading: <Loader2Icon className="size-4 animate-spin" />,
			}}
			style={
				{
					"--normal-bg": "var(--popover)",
					"--normal-text": "var(--popover-foreground)",
					"--normal-border": "var(--border)",
					"--border-radius": "var(--radius)",
				} as React.CSSProperties
			}
			{...props}
		/>
	);
};

export { Toaster };
