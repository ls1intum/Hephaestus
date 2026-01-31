import * as React from "react";
import { cn } from "./utils";

/**
 * Composes a component that can render as its child element when asChild is true.
 */
export function composeRenderProps<
	E extends keyof React.JSX.IntrinsicElements,
	P extends Record<string, unknown>,
>(
	asChild: boolean | undefined,
	children: React.ReactNode,
	defaultElement: E,
	props: P & { className?: string },
): React.ReactElement {
	if (asChild && React.isValidElement(children)) {
		const { className, ...rest } = props;
		return React.cloneElement(children, {
			...rest,
			className: cn(className, (children.props as { className?: string }).className),
		} as React.HTMLAttributes<HTMLElement>);
	}

	const { className, ...rest } = props;
	return React.createElement(defaultElement, { className, ...rest }, children);
}
