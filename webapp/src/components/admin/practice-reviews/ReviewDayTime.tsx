import { useNow } from "@/components/common/use-now";
import { formatDayTime } from "@/lib/dates";

export interface ReviewDayTimeProps {
	/** Absent when the record has no such time, which reads as a dash rather than as "now". */
	at: Date | undefined;
	className?: string;
}

/**
 * When something happened, as a day and a time in plain text: "6 Oct, 14:05". It is no tooltip
 * button, so a row or a level keeps its focus stops for what the reader acts on, and the time can
 * be read and compared against a log without a hover.
 */
export function ReviewDayTime({ at, className }: ReviewDayTimeProps) {
	const today = new Date(useNow());
	if (at === undefined) {
		return <span className={className}>–</span>;
	}
	return (
		<time dateTime={at.toISOString()} className={className}>
			{formatDayTime(at, today)}
		</time>
	);
}
