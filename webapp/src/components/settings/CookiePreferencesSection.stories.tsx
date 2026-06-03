import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, screen, userEvent, within } from "storybook/test";
import { CookieConsentBanner } from "@/components/consent/CookieConsentBanner";
import { clearStoredConsent, consumeConsentReopen, setStoredConsent } from "@/integrations/consent";
import { CookiePreferencesSection } from "./CookiePreferencesSection";

/**
 * Privacy & cookies settings row. Rendered alongside the consent banner so the withdrawal path
 * (GDPR Art. 7(3)) is reviewable end-to-end: clicking "Manage cookie preferences" forgets the
 * stored decision and the banner re-appears.
 */
const meta = {
	component: CookiePreferencesSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	// Seed a prior decision so the section shows a summary and the banner starts hidden. The teardown
	// drains the reopen flag clearStoredConsent sets, so it can't leak focus into the next story.
	beforeEach: () => {
		setStoredConsent({ analytics: true, errorMonitoring: false });
		return () => {
			clearStoredConsent();
			consumeConsentReopen();
		};
	},
	render: () => (
		<>
			<CookiePreferencesSection />
			<CookieConsentBanner />
		</>
	),
} satisfies Meta<typeof CookiePreferencesSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Shows the current choice and re-opens the consent banner on demand. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/analytics on · error monitoring off/i)).toBeInTheDocument();
		// The banner is hidden while a decision is stored.
		await expect(screen.queryByText("Cookies & privacy")).not.toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /manage cookie preferences/i }));
		await expect(await screen.findByText("Cookies & privacy")).toBeInTheDocument();
		// A user-initiated reopen moves focus to the banner dialog (keyboard/AT parity).
		await expect(screen.getByRole("dialog")).toHaveFocus();
	},
};
