import type { LucideIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export interface FeatureData {
	icon: LucideIcon;
	badge: string;
	title: string;
	description: string;
	content: string;
}

interface FeatureCardProps {
	feature: FeatureData;
}

export function FeatureCard({ feature }: FeatureCardProps) {
	const { icon: Icon, badge, title, description, content } = feature;

	return (
		<Card className="bg-gradient-to-br from-background to-muted/50 border-muted">
			<CardHeader>
				<div className="flex items-center gap-2 mb-2">
					<Icon className="h-5 w-5 text-primary" />
					<Badge variant="secondary">{badge}</Badge>
				</div>
				<CardTitle>{title}</CardTitle>
				<CardDescription>{description}</CardDescription>
			</CardHeader>
			<CardContent>
				<p className="text-muted-foreground">{content}</p>
			</CardContent>
		</Card>
	);
}
