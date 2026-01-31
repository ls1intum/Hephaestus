import { motion, useReducedMotion } from "framer-motion";
import { ClipboardList } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import {
	selectHasPendingSurvey,
	useSurveyNotificationStore,
} from "@/stores/survey-notification-store";

export const SURVEY_LAYOUT_ID = "survey-notification-morph";

const spring = { type: "spring", stiffness: 400, damping: 30 } as const;

export function SurveyNotificationButton() {
	const hasPending = useSurveyNotificationStore(selectHasPendingSurvey);
	const survey = useSurveyNotificationStore((s) => s.pendingSurvey);
	const reopen = useSurveyNotificationStore((s) => s.reopenSurvey);
	const prefersReducedMotion = useReducedMotion();

	if (!hasPending || !survey) return null;

	return (
		<motion.div
			layoutId={SURVEY_LAYOUT_ID}
			layout
			transition={spring}
			style={{ borderRadius: 8, overflow: "visible" }}
		>
			<motion.div
				initial={{ opacity: 0, scale: 0.8 }}
				animate={{ opacity: 1, scale: 1 }}
				transition={{ delay: 0.1, ...spring }}
			>
				<Tooltip>
					<TooltipTrigger
						render={
							<Button
								variant="outline"
								size="icon"
								onClick={reopen}
								className="relative"
								style={{ overflow: "visible" }}
								aria-label={`Open survey: ${survey.name}`}
							>
								<ClipboardList className="h-[1.2rem] w-[1.2rem]" />
								<Badge animated={!prefersReducedMotion} />
							</Button>
						}
					/>
					<TooltipContent>
						<p className="font-medium">Survey pending</p>
						<p className="text-xs text-muted-foreground">{survey.name}</p>
					</TooltipContent>
				</Tooltip>
			</motion.div>
		</motion.div>
	);
}

function Badge({ animated }: { animated: boolean }) {
	return (
		<motion.span
			className="absolute -top-1 -right-1 flex h-3 w-3"
			initial={{ scale: 0 }}
			animate={{ scale: 1 }}
			transition={{ type: "spring", stiffness: 500, damping: 15, delay: 0.2 }}
		>
			{animated && (
				<motion.span
					className="absolute inline-flex h-full w-full rounded-full bg-destructive"
					animate={{ scale: [1, 1.5], opacity: [0.7, 0] }}
					transition={{
						duration: 1,
						repeat: Number.POSITIVE_INFINITY,
						repeatDelay: 1,
					}}
				/>
			)}
			<span className="relative inline-flex h-3 w-3 rounded-full bg-destructive" />
		</motion.span>
	);
}
