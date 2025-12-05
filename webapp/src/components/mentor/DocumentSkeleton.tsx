import { Skeleton } from "@/components/ui/skeleton";
import type { ArtifactKind } from "@/lib/types";

export const DocumentSkeleton = ({
	artifactKind: _artifactKind,
}: {
	artifactKind: ArtifactKind;
}) => {
	return (
		<div className="flex flex-col gap-4 w-full">
			<Skeleton className="h-12 w-1/2" />
			<Skeleton className="h-5 w-full" />
			<Skeleton className="h-5 w-full" />
			<Skeleton className="h-5 w-1/3" />
			<Skeleton className="h-5 w-52" />
			<Skeleton className="h-8 w-52" />
			<Skeleton className="h-5 w-2/3" />
		</div>
	);
};

export const InlineDocumentSkeleton = () => {
	return (
		<div className="flex flex-col gap-4 w-full">
			<Skeleton className="h-4 w-48" />
			<Skeleton className="h-4 w-3/4" />
			<Skeleton className="h-4 w-1/2" />
			<Skeleton className="h-4 w-64" />
			<Skeleton className="h-4 w-40" />
			<Skeleton className="h-4 w-36" />
			<Skeleton className="h-4 w-64" />
		</div>
	);
};
