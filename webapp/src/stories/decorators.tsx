/**
 * Wraps a story in a GitLab provider color scope so that
 * `--color-provider-*` CSS custom properties resolve to Pajamas values.
 */
export const gitlabDecorator = (Story: React.ComponentType) => (
	<div data-provider="gitlab">
		<Story />
	</div>
);
