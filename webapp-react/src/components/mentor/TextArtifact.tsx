import { formatDistance } from "date-fns";
import { Copy, History, Redo2, Undo2 } from "lucide-react";
import type { ReactNode } from "react";
import type { ArtifactAction } from "./ArtifactActions";
import { ArtifactShell, type ArtifactShellHeaderMeta } from "./ArtifactShell";
import type { ArtifactShellModel, TextContentModel } from "./artifact-model";
import { TextArtifactContent } from "./TextArtifactContent";
import { VersionFooter } from "./VersionFooter";

export type TextArtifactProps = { model: ArtifactShellModel<TextContentModel> };

export function TextArtifact({ model }: TextArtifactProps) {
	const { overlay, ui, chat, version, content: text } = model;
	const { isVisible, isMobile, readonly, className } = ui;
	const {
		messages,
		votes,
		status,
		attachments,
		partRenderers,
		onMessageSubmit,
		onStop,
		onFileUpload,
		onMessageEdit,
		onCopy,
		onVote,
		onClose,
	} = chat;
	const isCurrentVersion = version?.isCurrentVersion ?? true;
	const currentVersionIndex = version?.currentVersionIndex ?? -1;
	const selectedUpdatedAt = version?.selectedUpdatedAt;
	const canPrev = version?.canPrev;
	const canNext = version?.canNext;
	const onPrevVersion = version?.onPrevVersion;
	const onNextVersion = version?.onNextVersion;
	const onBackToLatestVersion = version?.onBackToLatestVersion;
	const onRestoreSelectedVersion = version?.onRestoreSelectedVersion;
	const isRestoring = version?.isRestoring;
	const documents = version?.documents;
	const { content, mode, onSaveContent, isLoading } = text;
	// Local helper: normalize unknown date values (Date | number seconds/ms | string)
	const toDate = (v: unknown): Date | undefined => {
		if (v == null) return undefined;
		if (v instanceof Date) return v;
		if (typeof v === "number") {
			const ms = v > 1_000_000_000_000 ? v : v * 1000; // seconds vs ms
			return new Date(ms);
		}
		if (typeof v === "string") {
			const d = new Date(v);
			if (d.getFullYear() < 2001) {
				// numeric string likely in seconds
				const n = Number(v);
				if (!Number.isNaN(n)) return new Date(n * 1000);
			}
			return d;
		}
		return undefined;
	};

	// Build a friendly subtitle (handle very fresh versions nicely)
	const subtitle = (() => {
		const v = version?.versionNumber;
		const dt = toDate(selectedUpdatedAt);
		if (!dt) return v != null ? `Version ${v}` : undefined;
		const now = new Date();
		const deltaMs = Math.abs(now.getTime() - dt.getTime());
		const isFresh = deltaMs < 60_000; // under 1 minute
		const rel = isFresh
			? "just now"
			: formatDistance(dt, now, { addSuffix: true });
		return v != null ? `Version ${v} • ${rel}` : rel;
	})();

	const computedHeader: ArtifactShellHeaderMeta | undefined = {
		subtitle,
		// Let the shell show the spinner when saving; container drives this via hook
		isSaving: model.isSaving ?? false,
	};

	const computedActions: ArtifactAction[] = [
		{
			id: "view-changes",
			icon: <History size={16} />,
			description: isCurrentVersion ? "View changes" : "View content",
			disabled: false,
			onClick: () => {
				if (isCurrentVersion) {
					onPrevVersion?.();
				} else {
					onBackToLatestVersion?.();
				}
			},
		},
		{
			id: "prev-version",
			icon: <Undo2 size={16} />,
			description: "View previous version",
			disabled: !onPrevVersion || !canPrev,
			onClick: () => onPrevVersion?.(),
		},
		{
			id: "next-version",
			icon: <Redo2 size={16} />,
			description: "View next version",
			disabled: !onNextVersion || !canNext,
			onClick: () => onNextVersion?.(),
		},
		{
			id: "copy",
			icon: <Copy size={16} />,
			description: "Copy content to clipboard",
			onClick: () => onCopy?.(content ?? ""),
		},
	];

	const footerEl: ReactNode =
		version && !isCurrentVersion ? (
			<VersionFooter
				currentVersionIndex={currentVersionIndex}
				documents={documents ?? []}
				handleVersionChange={(type) => {
					if (type === "latest") onBackToLatestVersion?.();
					if (type === "prev") onPrevVersion?.();
					if (type === "next") onNextVersion?.();
				}}
				isRestoring={Boolean(isRestoring)}
				onRestore={onRestoreSelectedVersion}
			/>
		) : null;

	return (
		<ArtifactShell
			overlay={overlay}
			isVisible={isVisible}
			isMobile={isMobile}
			readonly={readonly}
			messages={messages}
			votes={votes}
			status={status}
			attachments={attachments}
			partRenderers={partRenderers}
			onMessageSubmit={onMessageSubmit}
			onStop={onStop}
			onFileUpload={onFileUpload}
			onMessageEdit={onMessageEdit}
			onCopy={onCopy}
			onVote={onVote}
			headerMeta={computedHeader}
			actions={computedActions}
			onClose={onClose}
			interactionDisabled={!isCurrentVersion}
			footer={footerEl}
			className={className}
		>
			<TextArtifactContent
				content={content}
				mode={mode}
				status={overlay.status}
				isCurrentVersion={isCurrentVersion}
				currentVersionIndex={currentVersionIndex}
				onSaveContent={onSaveContent}
				isLoading={isLoading}
			/>
		</ArtifactShell>
	);
}
