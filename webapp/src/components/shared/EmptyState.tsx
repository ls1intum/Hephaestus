import React, { type ComponentType, type ReactNode } from "react";
import { Card, CardContent } from "@/components/ui/card";

/** The props the empty state passes to an icon component — Lucide and Octicons both accept them. */
interface IconProps {
	className?: string;
	size?: number;
}

/**
 * Lucide icons are `forwardRef` objects rather than plain functions, so a component icon is
 * recognised by either shape.
 */
function isIconComponent(
	icon: ReactNode | ComponentType<IconProps>,
): icon is ComponentType<IconProps> {
	if (typeof icon === "function") return true;
	if (typeof icon !== "object" || icon === null || !("render" in icon)) return false;
	return typeof icon.render === "function";
}

export interface EmptyStateProps {
	/** The icon to display at the top of the empty state: an icon component or a rendered element */
	icon: ReactNode | ComponentType<IconProps>;
	/** The title to display as the main message */
	title: string;
	/** Optional description text to provide more context */
	description?: string;
	/** Optional action component like a button */
	action?: ReactNode;
	/** Optional custom height, defaults to h-60 */
	height?: string;
	/** Optional additional className */
	className?: string;
}

/**
 * EmptyState component displays a consistent empty state pattern across the application
 * when there is no content to show.
 */
export function EmptyState({
	icon,
	title,
	description,
	action,
	height = "h-60",
	className,
}: EmptyStateProps) {
	// Render the icon based on its type
	const renderIcon = () => {
		if (React.isValidElement(icon)) {
			return icon;
		}

		if (isIconComponent(icon)) {
			const IconComponent = icon;
			return <IconComponent className="h-6 w-6 text-muted-foreground" size={24} />;
		}

		return null;
	};

	return (
		<Card className={`border-dashed ${height} ${className || ""}`}>
			<CardContent className="flex flex-col items-center justify-center py-8 px-4 text-center h-full">
				<div className="rounded-full bg-muted p-3 mb-3">{renderIcon()}</div>
				<h3 className="font-medium text-lg mb-1">{title}</h3>
				{description && (
					<p className="text-muted-foreground text-sm mb-4 max-w-md">{description}</p>
				)}
				{action && <div className="mt-2">{action}</div>}
			</CardContent>
		</Card>
	);
}
