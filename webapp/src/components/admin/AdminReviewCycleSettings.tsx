import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { updateReviewCycleMutation } from "@/api/@tanstack/react-query.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";

const TIME_24H = /^([01]\d|2[0-3]):[0-5]\d$/;
const DEFAULT_DAY = 2;
const DEFAULT_TIME = "09:00";

const DAYS = [
	{ value: "1", label: "Monday" },
	{ value: "2", label: "Tuesday" },
	{ value: "3", label: "Wednesday" },
	{ value: "4", label: "Thursday" },
	{ value: "5", label: "Friday" },
	{ value: "6", label: "Saturday" },
	{ value: "7", label: "Sunday" },
];

export interface AdminReviewCycleSettingsProps {
	workspaceSlug: string;
	/** Day of week the weekly review cycle ends (1=Monday … 7=Sunday). */
	day?: number;
	/** Time of day the weekly cycle ends, "HH:mm" (24h). */
	time?: string;
	onSaved: () => void;
}

/**
 * Editor for the weekly practice review cycle. The parent remounts this via a server-snapshot key,
 * so state initializes once from props with no prop→state effect.
 */
export function AdminReviewCycleSettings({
	workspaceSlug,
	day,
	time,
	onSaved,
}: AdminReviewCycleSettingsProps) {
	const [dayInput, setDayInput] = useState(String(day ?? DEFAULT_DAY));
	const [timeInput, setTimeInput] = useState(time ?? DEFAULT_TIME);

	const timeInvalid = !TIME_24H.test(timeInput);

	const save = useMutation({
		...updateReviewCycleMutation(),
		onSuccess: () => {
			toast.success("Review cycle saved");
			onSaved();
		},
		onError: (e) => {
			toast.error("Failed to save review cycle", {
				description: e instanceof Error ? e.message : undefined,
			});
		},
	});

	return (
		<div className="space-y-6">
			<div>
				<h2 className="text-lg font-semibold mb-4">Review cycle</h2>
				<Card>
					<CardContent className="space-y-4">
						<p className="text-sm text-muted-foreground">
							The weekly window practice feedback is grouped into. Each cycle ends on the day and
							time below (workspace timezone).
						</p>

						<div className="grid grid-cols-2 gap-4">
							<Field>
								<FieldLabel htmlFor="review-cycle-day">Day</FieldLabel>
								<Select
									items={DAYS}
									value={dayInput}
									disabled={save.isPending}
									onValueChange={(value) => {
										if (value) setDayInput(value);
									}}
								>
									<SelectTrigger id="review-cycle-day">
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										{DAYS.map((d) => (
											<SelectItem key={d.value} value={d.value}>
												{d.label}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</Field>

							<Field data-invalid={timeInvalid}>
								<FieldLabel htmlFor="review-cycle-time">Time (24h)</FieldLabel>
								<Input
									id="review-cycle-time"
									type="time"
									value={timeInput}
									disabled={save.isPending}
									onChange={(e) => setTimeInput(e.target.value)}
									aria-invalid={timeInvalid}
								/>
								<FieldDescription>When the weekly cycle ends.</FieldDescription>
								{timeInvalid && <FieldError>Time must be in HH:mm format.</FieldError>}
							</Field>
						</div>

						<div className="flex pt-2">
							<Button
								onClick={() =>
									save.mutate({
										path: { workspaceSlug },
										body: { day: Number(dayInput), time: timeInput },
									})
								}
								disabled={save.isPending || timeInvalid}
							>
								{save.isPending ? "Saving…" : "Save"}
							</Button>
						</div>
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
