import { useRouter } from "@tanstack/react-router";

import { Button } from "@/components/ui/button";

export function RouteError() {
	const router = useRouter();

	return (
		<div className="flex min-h-[50vh] items-center justify-center p-6">
			<section className="max-w-md space-y-4 text-center" role="alert">
				<h1 className="text-2xl font-semibold">We could not load this page</h1>
				<p className="text-muted-foreground">
					Try again. If it keeps failing, contact your instance operator.
				</p>
				<div className="flex justify-center gap-2">
					<Button
						onClick={() => {
							void router.invalidate();
						}}
					>
						Retry
					</Button>
				</div>
			</section>
		</div>
	);
}
