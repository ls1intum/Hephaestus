import { stateConfig } from "../utils";
import { Card, CardContent } from "@/components/ui/card";

export function BadPracticeLegendCard() {
  const stateList = Object.values(stateConfig);

  return (
    <Card>
      <CardContent className="flex flex-col gap-y-4 py-4">
        <div className="flex flex-col gap-2 sm:min-w-[250px]">
          <h4 className="mb-2 text-lg font-semibold leading-none tracking-tight">Icons</h4>
          {stateList.map((state) => {
            const Icon = state.icon;
            return (
              <div key={state.text} className="flex flex-row items-center text-center gap-2">
                <Icon className={`${state.color} size-5`} />
                <span className="text-github-muted-foreground">{state.text}</span>
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}
