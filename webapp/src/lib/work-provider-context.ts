import { createContext, use } from "react";

import type { ItemProvider } from "@/lib/item-kinds";

/**
 * The source-control provider of the workspace on screen, so a sentence that counts its pull
 * requests can call them merge requests on GitLab. Absent where no workspace names one; the noun is
 * then "pull or merge requests".
 */
export const WorkProviderContext = createContext<ItemProvider | undefined>(undefined);

export function useWorkProvider(): ItemProvider | undefined {
	return use(WorkProviderContext);
}
