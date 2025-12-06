import { Spinner } from "@/components/ui/spinner";
import type { Attachment } from "@/lib/types";

export const PreviewAttachment = ({
	attachment,
	isUploading = false,
}: {
	attachment: Attachment;
	isUploading?: boolean;
}) => {
	const { name, url, contentType } = attachment;

	return (
		<div data-testid="input-attachment-preview" className="flex flex-col gap-2">
			<div className="w-20 h-16 aspect-video bg-muted rounded-md relative flex flex-col items-center justify-center">
				{contentType ? (
					contentType.startsWith("image") ? (
						<img
							key={url}
							src={url}
							alt={name ?? "An image attachment"}
							className="rounded-md size-full object-cover"
						/>
					) : (
						<div />
					)
				) : (
					<div />
				)}

				{isUploading && (
					<div
						data-testid="input-attachment-loader"
						className="absolute text-zinc-500"
					>
						<Spinner size="sm" />
					</div>
				)}
			</div>
			<div className="text-xs text-zinc-500 max-w-16 truncate">{name}</div>
		</div>
	);
};
