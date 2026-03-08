import type { Decorator } from "@storybook/react";
import { getProviderSlug, type ProviderType } from "@/lib/provider";

/**
 * Wraps a story in a provider color scope so that
 * `--color-provider-*` CSS custom properties resolve to provider-specific values.
 */
export function withProvider(provider: ProviderType): Decorator {
	return function ProviderDecorator(Story) {
		return (
			<div data-provider={getProviderSlug(provider)}>
				<Story />
			</div>
		);
	};
}
