import { createFileRoute } from "@tanstack/react-router";
import { ImprintContainer } from "@/features/info/legal/ImprintContainer";

export const Route = createFileRoute("/imprint")({
  component: ImprintContainer,
});