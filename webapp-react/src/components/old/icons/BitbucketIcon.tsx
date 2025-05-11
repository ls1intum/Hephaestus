import React from "react";

interface BitbucketIconProps extends React.SVGProps<SVGSVGElement> {}

export function BitbucketIcon(props: BitbucketIconProps) {
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
      <path d="M3.42 5c-.42.32-.7 1.1-.54 1.72L6.39 18c.18.63.89 1 1.45 1h8.32c.42 0 .96-.27 1.07-.64l3.51-11.27c.17-.62-.11-1.4-.54-1.72L3.42 5Z" />
      <path d="M14.5 5 12.7 15.1c-.07.4-.45.9-.9.9H8.11c-.45 0-.83-.5-.9-.9L5.5 5" />
    </svg>
  );
}