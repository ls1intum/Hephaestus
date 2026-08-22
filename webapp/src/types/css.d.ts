// React's `CSSProperties` comes from csstype, which lists only standard properties, so a style
// object carrying a CSS custom property does not fit it. The template-literal index signature is
// narrow enough to keep every standard property checked — an index signature of plain `string`
// would turn the whole style object into `any` and lose that.
import "react";

declare module "react" {
	interface CSSProperties {
		[custom: `--${string}`]: string | number | undefined;
	}
}
