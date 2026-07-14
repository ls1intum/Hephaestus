import type { ReactNode } from "react";

export interface IntegrationPageHeaderProps {
	icon?: ReactNode;
	title: string;
	description?: ReactNode;
	/** Right-aligned action slot — typically a SyncNowButton and/or a health badge. */
	actions?: ReactNode;
}

/** Shared header for every Integrations detail page: icon + title + description, actions right-aligned. */
export function IntegrationPageHeader({
	icon,
	title,
	description,
	actions,
}: IntegrationPageHeaderProps) {
	return (
		<header className="flex flex-wrap items-start justify-between gap-4">
			<div className="flex items-start gap-3">
				{icon && <div className="mt-0.5 text-muted-foreground">{icon}</div>}
				<div>
					<h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
					{description && <p className="text-muted-foreground">{description}</p>}
				</div>
			</div>
			{actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
		</header>
	);
}
