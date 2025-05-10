import { createFileRoute } from "@tanstack/react-router";
import { AboutContainer } from "@/features/info/about/AboutContainer";

export const Route = createFileRoute("/about")({
  component: AboutContainer,
});
