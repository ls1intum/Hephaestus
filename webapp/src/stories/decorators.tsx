import type { Decorator } from "@storybook/react";
import { StandardPageSurface } from "@/components/core/StandardPageSurface";
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

export const withStandardPage: Decorator = (Story) => (
	<StandardPageSurface>
		<Story />
	</StandardPageSurface>
);

export const withWidePage: Decorator = (Story) => (
	<div className="mx-auto w-full max-w-6xl">
		<Story />
	</div>
);
