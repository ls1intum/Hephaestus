import { Volume2, VolumeX } from "lucide-react";
import { useState } from "react";
import type { InstanceSettings } from "@/api/types.gen";
import { formatTimestamp } from "@/components/admin/audit/auditFormat";
import {
	AlertDialog,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardContent,
	CardDescription,
	CardFooter,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

/** The word the admin types to release the brake — matches the action verb. */
const RELEASE_CONFIRM_WORD = "release";

interface SilentModeCardProps {
	settings: InstanceSettings;
	isPending: boolean;
	/** Engage the brake with an optional operator-facing reason. */
	onEngage: (reason: string | undefined) => void;
	/** Release the brake — delivery resumes instance-wide immediately. */
	onRelease: () => void;
}

/**
 * The emergency silent-mode control (#1386), with asymmetric friction: engaging is one click plus an
 * optional reason; releasing (re-opens delivery to every workspace at once) takes a heavy
 * type-to-confirm dialog. Cheap to silence, expensive to un-silence.
 */
export function SilentModeCard({ settings, isPending, onEngage, onRelease }: SilentModeCardProps) {
	const engaged = settings.silentModeEngaged;
	const [engageOpen, setEngageOpen] = useState(false);
	const [releaseOpen, setReleaseOpen] = useState(false);
	const [reason, setReason] = useState("");
	const [confirmWord, setConfirmWord] = useState("");

	const changedAt = settings.silentModeChangedAt
		? formatTimestamp(settings.silentModeChangedAt)
		: null;

	const openEngage = () => {
		setReason("");
		setEngageOpen(true);
	};
	const openRelease = () => {
		setConfirmWord("");
		setReleaseOpen(true);
	};

	return (
		<Card>
			<CardHeader>
				<CardTitle className="flex items-center gap-2">
					{engaged ? (
						<VolumeX className="size-4 text-destructive" aria-hidden />
					) : (
						<Volume2 className="size-4 text-muted-foreground" aria-hidden />
					)}
					Silent mode
				</CardTitle>
				<CardDescription>
					The instance-wide emergency brake. While engaged, Hephaestus posts no practice feedback
					and sends no Slack messages — for any workspace. Workspace settings are untouched and
					apply again the moment silent mode is released.
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-2">
				<div className="flex items-center gap-2">
					{engaged ? (
						<Badge variant="destructive">Engaged</Badge>
					) : (
						<Badge variant="success">Released</Badge>
					)}
					{changedAt ? (
						<span
							className="text-sm text-muted-foreground"
							title={`${changedAt.local} (${changedAt.isoUtc})`}
						>
							{engaged ? "engaged" : "last changed"}
							{settings.silentModeChangedBy ? ` by ${settings.silentModeChangedBy}` : ""} —{" "}
							{changedAt.local}
						</span>
					) : null}
				</div>
				{engaged && settings.silentModeReason ? (
					<p className="text-sm text-muted-foreground">Reason: “{settings.silentModeReason}”</p>
				) : null}
			</CardContent>
			<CardFooter>
				{engaged ? (
					<Button variant="outline" onClick={openRelease} disabled={isPending}>
						<Volume2 aria-hidden />
						Release silent mode…
					</Button>
				) : (
					<Button variant="destructive-outline" onClick={openEngage} disabled={isPending}>
						<VolumeX aria-hidden />
						Engage silent mode…
					</Button>
				)}
			</CardFooter>

			{/* Engage: deliberately cheap — one confirm, optional reason. */}
			<Dialog open={engageOpen} onOpenChange={setEngageOpen}>
				<DialogContent>
					<DialogHeader>
						<DialogTitle>Engage silent mode</DialogTitle>
						<DialogDescription>
							All outbound delivery stops immediately: PR/MR feedback comments, Slack messages, and
							mentor replies in Slack, across every workspace. Reviews still run and their findings
							are saved — they are only held back from posting, and are not auto-posted on release.
						</DialogDescription>
					</DialogHeader>
					<Field>
						<FieldLabel htmlFor="silent-mode-reason">Reason</FieldLabel>
						<Textarea
							id="silent-mode-reason"
							value={reason}
							onChange={(event) => setReason(event.target.value)}
							placeholder="e.g. Investigating incident #42 — bad feedback going out"
							maxLength={500}
							rows={3}
						/>
						<FieldDescription>
							Optional, but it is what other admins will see on the banner.
						</FieldDescription>
					</Field>
					<DialogFooter>
						<Button variant="outline" onClick={() => setEngageOpen(false)}>
							Cancel
						</Button>
						<Button
							variant="destructive"
							disabled={isPending}
							onClick={() => {
								onEngage(reason.trim() === "" ? undefined : reason.trim());
								setEngageOpen(false);
							}}
						>
							<VolumeX aria-hidden />
							Engage silent mode
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			{/* Release: deliberately heavy — restate consequences + type-to-confirm. */}
			<AlertDialog open={releaseOpen} onOpenChange={setReleaseOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Release silent mode?</AlertDialogTitle>
						<AlertDialogDescription>
							Delivery resumes for every workspace immediately: practice feedback lands on PRs and
							MRs again and Slack messages go out. Make sure whatever prompted the brake is resolved
							first.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<Field>
						<FieldLabel htmlFor="silent-mode-release-confirm">
							Type <span className="font-mono font-semibold">{RELEASE_CONFIRM_WORD}</span> to
							confirm
						</FieldLabel>
						<Input
							id="silent-mode-release-confirm"
							value={confirmWord}
							onChange={(event) => setConfirmWord(event.target.value)}
							placeholder={RELEASE_CONFIRM_WORD}
							autoComplete="off"
						/>
					</Field>
					<AlertDialogFooter>
						<Button variant="outline" onClick={() => setReleaseOpen(false)}>
							Cancel
						</Button>
						<Button
							variant="destructive"
							disabled={isPending || confirmWord.trim().toLowerCase() !== RELEASE_CONFIRM_WORD}
							onClick={() => {
								onRelease();
								setReleaseOpen(false);
							}}
						>
							<Volume2 aria-hidden />
							Release silent mode
						</Button>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</Card>
	);
}
