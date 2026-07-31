import type { ReactNode } from "react";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { cn } from "@/lib/utils";

export function ReviewTechnicalDetails({
	children,
	className,
}: {
	children: ReactNode;
	className?: string;
}) {
	return (
		<Accordion>
			<AccordionItem value="technical">
				<AccordionTrigger>Technical details</AccordionTrigger>
				<AccordionContent>
					<div className={cn("pt-2", className)}>{children}</div>
				</AccordionContent>
			</AccordionItem>
		</Accordion>
	);
}
