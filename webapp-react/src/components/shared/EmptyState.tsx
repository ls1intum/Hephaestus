import { Card, CardContent } from "@/components/ui/card";
import type { LucideIcon } from "lucide-react";
import React, { type ComponentType, type ReactNode } from "react";

export interface EmptyStateProps {
	/** The icon to display at the top of the empty state */
	icon:
		| LucideIcon
		| ReactNode
		| ComponentType<{ className?: string; size?: number }>;
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

		if (typeof icon === "function") {
			const IconComponent = icon as ComponentType<{
				className?: string;
				size?: number;
			}>;
			return React.createElement(IconComponent, {
				className: "h-6 w-6 text-muted-foreground",
				size: 24,
			});
		}

		// Handle Lucide icons which are objects with a render method
		if (
			icon &&
			typeof icon === "object" &&
			"render" in icon &&
			typeof icon.render === "function"
		) {
			// Cast to unknown first to avoid TypeScript errors
			// Use a specific interface for Lucide icon props
			interface IconProps {
				className?: string;
				size?: number;
			}
			const IconComponent = icon as unknown as React.ComponentType<IconProps>;
			return (
				<IconComponent className="h-6 w-6 text-muted-foreground" size={24} />
			);
		}

		return null;
	};

	return (
		<Card className={`border-dashed ${height} ${className || ""}`}>
			<CardContent className="flex flex-col items-center justify-center py-8 px-4 text-center h-full">
				<div className="rounded-full bg-muted p-3 mb-3">{renderIcon()}</div>
				<h3 className="font-medium text-lg mb-1">{title}</h3>
				{description && (
					<p className="text-muted-foreground text-sm mb-4 max-w-md">
						{description}
					</p>
				)}
				{action && <div className="mt-2">{action}</div>}
			</CardContent>
		</Card>
	);
}
