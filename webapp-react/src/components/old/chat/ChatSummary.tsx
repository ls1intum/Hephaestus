import { Card } from '@/components/ui/card';
import type { StatusItem } from './types';

interface ChatSummaryProps {
  status: StatusItem[];
  impediments: StatusItem[];
  promises: StatusItem[];
}

export function ChatSummary({ status, impediments, promises }: ChatSummaryProps) {
  return (
    <Card className="w-full max-w-[600px]">
      {status.length > 0 && (
        <div className="p-4 border-b">
          <h3 className="text-md font-semibold mb-2">Status</h3>
          <ul className="space-y-2">
            {status.map((item, index) => (
              <li key={`status-${index}`} className="ml-4 list-disc">
                <p className="font-medium">{item.title}</p>
                <p className="text-sm text-muted-foreground">{item.description}</p>
              </li>
            ))}
          </ul>
        </div>
      )}

      {impediments.length > 0 && (
        <div className="p-4 border-b">
          <h3 className="text-md font-semibold mb-2">Impediments</h3>
          <ul className="space-y-2">
            {impediments.map((item, index) => (
              <li key={`impediment-${index}`} className="ml-4 list-disc">
                <p className="font-medium">{item.title}</p>
                <p className="text-sm text-muted-foreground">{item.description}</p>
              </li>
            ))}
          </ul>
        </div>
      )}

      {promises.length > 0 && (
        <div className="p-4">
          <h3 className="text-md font-semibold mb-2">Next Steps</h3>
          <ul className="space-y-2">
            {promises.map((item, index) => (
              <li key={`promise-${index}`} className="ml-4 list-disc">
                <p className="font-medium">{item.title}</p>
                <p className="text-sm text-muted-foreground">{item.description}</p>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}