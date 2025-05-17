import type { LabelInfo } from "@/api/types.gen";

interface GitHubLabelProps {
  label: LabelInfo;
}

export function GitHubLabel({ label }: GitHubLabelProps) {
  return (
    <span
      className="text-xs px-2 py-1 rounded-full"
      style={{ 
        backgroundColor: `#${label.color}33`, // 20% opacity version of the color
        color: `#${label.color}`
      }}
    >
      {label.name}
    </span>
  );
}
