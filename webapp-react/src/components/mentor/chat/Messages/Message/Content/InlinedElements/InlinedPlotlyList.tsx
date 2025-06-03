import type { IPlotlyElement } from "@chainlit/react-client";

import { PlotlyElement } from "@/components/mentor/Elements/Plotly";

interface Props {
	items: IPlotlyElement[];
}

const InlinedPlotlyList = ({ items }: Props) => (
	<div className="flex flex-col gap-2">
		{items.map((element) => {
			return (
				<div
					key={
						element.id ||
						element.name ||
						`plotly-${element.url || Math.random()}`
					}
					className="max-w-[600px] h-[400px]"
					style={{
						maxWidth: "fit-content",
						height: "400px",
					}}
				>
					<PlotlyElement element={element} />
				</div>
			);
		})}
	</div>
);

export { InlinedPlotlyList };
