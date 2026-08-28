import type { ComponentProps } from "react";

import { cn } from "@/lib/utils";

export function StandardPageSurface({ className, ...props }: ComponentProps<"div">) {
	return <div className={cn("flex w-full flex-col px-4 py-6", className)} {...props} />;
}
