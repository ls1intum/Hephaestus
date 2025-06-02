import { Suspense, lazy } from "react";

import type { IDataframeElement } from "@chainlit/react-client";

import { Skeleton } from "@/components/ui/skeleton";

interface Props {
	element: IDataframeElement;
}
const DataframeElement = lazy(() => import("../Elements/Dataframe"));

const LazyDataframe = ({ element }: Props) => {
	return (
		<Suspense fallback={<Skeleton className="h-full rounded-md" />}>
			<DataframeElement element={element} />
		</Suspense>
	);
};

export { LazyDataframe };
