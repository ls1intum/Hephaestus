import type { IFileElement } from "@chainlit/react-client";

import { FileElement } from "@/components/mentor/Elements/File";

interface Props {
	items: IFileElement[];
}

const InlinedFileList = ({ items }: Props) => {
	return (
		<div className="flex items-center gap-2">
			{items.map((file) => {
				return (
					<div
						key={file.id || file.name || `file-${file.mime || Math.random()}`}
					>
						<FileElement element={file} />
					</div>
				);
			})}
		</div>
	);
};

export { InlinedFileList };
