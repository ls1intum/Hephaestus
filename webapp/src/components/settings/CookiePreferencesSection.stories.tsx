import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, screen, userEvent, within } from "storybook/test";
import { CookieConsentBanner } from "@/components/consent/CookieConsentBanner";
import {
	CONSENT_STORAGE_KEY,
	closeConsentReopen,
	consumeReopenSeed,
	setStoredConsent,
} from "@/integrations/consent";
import { CookiePreferencesSection } from "./CookiePreferencesSection";

/**
 * Privacy settings row. Rendered alongside the consent banner so the edit path
 * (GDPR Art. 7(3)) is reviewable end-to-end: clicking "Change cookie choices" re-opens the banner
 * (pre-seeded, cancelable) so a prior choice can be adjusted or withdrawn.
 */
const meta = {
	component: CookiePreferencesSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	// Seed a prior decision so the section shows a summary and the banner starts hidden. Teardown
	// resets the stored decision + reopen flag so nothing leaks into the next story.
	beforeEach: () => {
		setStoredConsent({ analytics: true, errorMonitoring: false });
		return () => {
			localStorage.removeItem(CONSENT_STORAGE_KEY);
			closeConsentReopen();
			consumeReopenSeed();
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
		await expect(canvas.getByRole("heading", { name: /^privacy$/i })).toBeInTheDocument();
		// The banner is hidden while a decision is stored.
		await expect(screen.queryByRole("region", { name: /your privacy/i })).not.toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /change cookie choices/i }));
		// A user-initiated reopen surfaces the banner and moves focus to it (keyboard/AT parity).
		await expect(await screen.findByRole("region", { name: /your privacy/i })).toBeInTheDocument();
		await expect(screen.getByRole("region", { name: /your privacy/i })).toHaveFocus();
	},
};
