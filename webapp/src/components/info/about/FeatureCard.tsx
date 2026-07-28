import { motion, useReducedMotion } from "motion/react";
import type { ElementType } from "react";
import { Badge } from "@/components/ui/badge";

export interface FeatureData {
	icon: ElementType<{
		className?: string;
		size?: number;
		strokeWidth?: number;
	}>;
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
	const shouldReduceMotion = useReducedMotion();

	return (
		<motion.article
			initial={shouldReduceMotion ? false : { opacity: 0, y: 14 }}
			whileInView={{ opacity: 1, y: 0 }}
			viewport={{ once: true, amount: 0.5 }}
			transition={{ duration: 0.55, ease: [0.22, 1, 0.36, 1] }}
			whileHover={shouldReduceMotion ? undefined : { y: -4 }}
			className="group border-t border-border py-6"
		>
			<div className="mb-5 flex items-center justify-between gap-3">
				<div className="flex size-11 items-center justify-center rounded-2xl bg-mentor/10 text-mentor transition-transform group-hover:-rotate-3">
					<Icon className="size-5" size={20} strokeWidth={1.7} />
				</div>
				<Badge variant="secondary">{badge}</Badge>
			</div>
			<h3 className="text-xl font-semibold">{title}</h3>
			<p className="mt-1 text-sm font-medium text-muted-foreground">{description}</p>
			<p className="mt-4 leading-relaxed text-muted-foreground">{content}</p>
		</motion.article>
	);
}
