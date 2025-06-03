import type { IPdfElement } from "@chainlit/react-client";

import { PDFElement } from "@/components/mentor/Elements/PDF";

interface Props {
	items: IPdfElement[];
}

const InlinedPDFList = ({ items }: Props) => (
	<div className="flex flex-col gap-2">
		{items.map((pdf) => {
			return (
				<div
					key={pdf.id || pdf.name || `pdf-${pdf.url || Math.random()}`}
					className="h-[400px]"
				>
					<PDFElement element={pdf} />
				</div>
			);
		})}
	</div>
);

export { InlinedPDFList };
