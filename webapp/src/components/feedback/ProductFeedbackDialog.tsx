import { MessageSquarePlus } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogHeader,
	DialogTitle,
	DialogTrigger,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";

const FEEDBACK_KINDS = [
	{ label: "Feedback", value: "FEEDBACK" },
	{ label: "Bug report", value: "BUG" },
] as const;

interface ProductFeedbackDialogProps {
	isSubmitting: boolean;
	onSubmit: (kind: "FEEDBACK" | "BUG", message: string) => Promise<boolean>;
}

export function ProductFeedbackDialog({ isSubmitting, onSubmit }: ProductFeedbackDialogProps) {
	const [open, setOpen] = useState(false);
	const [kind, setKind] = useState<"FEEDBACK" | "BUG">("FEEDBACK");
	const [message, setMessage] = useState("");
	const submit = async () => {
		if (await onSubmit(kind, message.trim())) {
			setMessage("");
			setOpen(false);
		}
	};
	return (
		<Dialog open={open} onOpenChange={setOpen}>
			<DialogTrigger
				render={<Button variant="ghost" size="icon" aria-label="Send product feedback" />}
			>
				<MessageSquarePlus />
			</DialogTrigger>
			<DialogContent>
				<DialogHeader>
					<DialogTitle>Send product feedback</DialogTitle>
					<DialogDescription>
						Your message is stored on this Hephaestus instance and is visible to its administrators.
						The current page path is included; no logs, configuration, or page content are attached.
						Do not include secrets or sensitive personal data. Contact your instance administrator
						to object to or request deletion of a submission.
					</DialogDescription>
				</DialogHeader>
				<form
					className="space-y-4"
					onSubmit={(event) => {
						event.preventDefault();
						void submit();
					}}
				>
					<div className="space-y-2">
						<Label id="feedback-kind-label" htmlFor="feedback-kind">
							Type
						</Label>
						<Select
							items={FEEDBACK_KINDS}
							value={kind}
							onValueChange={(value) => value && setKind(value)}
						>
							<SelectTrigger id="feedback-kind">
								<SelectValue />
							</SelectTrigger>
							<SelectContent aria-labelledby="feedback-kind-label">
								{FEEDBACK_KINDS.map((item) => (
									<SelectItem key={item.value} value={item.value}>
										{item.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>
					<div className="space-y-2">
						<Label htmlFor="feedback-message">Message</Label>
						<Textarea
							id="feedback-message"
							name="message"
							required
							value={message}
							maxLength={5000}
							rows={7}
							onChange={(event) => setMessage(event.target.value)}
						/>
					</div>
					<div className="flex justify-end">
						<Button type="submit" disabled={!message.trim() || isSubmitting}>
							{isSubmitting ? "Sending…" : "Send"}
						</Button>
					</div>
				</form>
			</DialogContent>
		</Dialog>
	);
}
