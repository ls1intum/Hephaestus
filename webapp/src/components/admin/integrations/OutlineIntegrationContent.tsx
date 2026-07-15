import {
	OutlineCollectionsSection,
	type OutlineCollectionsSectionProps,
} from "./outline/OutlineCollectionsSection";
import { OutlineConnectCard, type OutlineConnectCardProps } from "./outline/OutlineConnectCard";

export interface OutlineIntegrationContentProps {
	connectCardProps: OutlineConnectCardProps;
	collectionsProps?: OutlineCollectionsSectionProps;
}

export function OutlineIntegrationContent({
	connectCardProps,
	collectionsProps,
}: OutlineIntegrationContentProps) {
	return (
		<div className="space-y-10">
			<OutlineConnectCard {...connectCardProps} />
			{collectionsProps && <OutlineCollectionsSection {...collectionsProps} />}
		</div>
	);
}
