import React from "react";

interface JiraIconProps extends React.SVGProps<SVGSVGElement> {}

export function JiraIcon(props: JiraIconProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      {...props}
    >
      <path d="M11.68 16.323a.835.835 0 0 1-1.177 0l-2.361-2.361a.834.834 0 0 1 0-1.177L16.31 4.62a.835.835 0 0 1 1.177 0l2.361 2.36a.834.834 0 0 1 0 1.178l-8.168 8.165z" />
      <path d="M6.902 11.545a.834.834 0 0 1 0-1.177L13.87 3.4a.835.835 0 0 1 1.177 0l2.36 2.36a.834.834 0 0 1 0 1.178l-6.966 6.966a.835.835 0 0 1-1.177 0l-2.361-2.36z" />
      <path d="M4.48 16.32a.834.834 0 0 1 0-1.176l6.967-6.968a.835.835 0 0 1 1.177 0l2.36 2.36a.834.834 0 0 1 0 1.178l-6.966 6.967a.835.835 0 0 1-1.177 0l-2.36-2.36z" />
    </svg>
  );
}