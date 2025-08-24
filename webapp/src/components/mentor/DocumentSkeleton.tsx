import type { ArtifactKind } from "@/lib/types";

export const DocumentSkeleton = ({
	artifactKind: _artifactKind,
}: {
	artifactKind: ArtifactKind;
}) => {
	return (
		<div className="flex flex-col gap-4 w-full">
			<div className="animate-pulse rounded-lg h-12 bg-accent w-1/2" />
			<div className="animate-pulse rounded-lg h-5 bg-accent w-full" />
			<div className="animate-pulse rounded-lg h-5 bg-accent w-full" />
			<div className="animate-pulse rounded-lg h-5 bg-accent w-1/3" />
			<div className="animate-pulse rounded-lg h-5 bg-transparent w-52" />
			<div className="animate-pulse rounded-lg h-8 bg-accent w-52" />
			<div className="animate-pulse rounded-lg h-5 bg-accent w-2/3" />
		</div>
	);
};

export const InlineDocumentSkeleton = () => {
	return (
		<div className="flex flex-col gap-4 w-full">
			<div className="animate-pulse rounded-lg h-4 bg-accent w-48" />
			<div className="animate-pulse rounded-lg h-4 bg-accent w-3/4" />
			<div className="animate-pulse rounded-lg h-4 bg-accent w-1/2" />
			<div className="animate-pulse rounded-lg h-4 bg-accent w-64" />
			<div className="animate-pulse rounded-lg h-4 bg-accent w-40" />
			<div className="animate-pulse rounded-lg h-4 bg-accent w-36" />
			<div className="animate-pulse rounded-lg h-4 bg-accent w-64" />
		</div>
	);
};
