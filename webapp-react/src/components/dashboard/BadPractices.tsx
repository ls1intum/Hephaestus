import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { 
  Bug, 
  AlertTriangle, 
  AlertCircle,
  ExternalLink
} from 'lucide-react';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

export interface BadPractice {
  id: string;
  type: string;
  message: string;
  repository: string;
  filePath: string;
  severity: 'high' | 'medium' | 'low';
  url?: string;
}

export interface BadPracticesProps {
  badPractices: BadPractice[];
}

export function BadPractices({ badPractices = [] }: BadPracticesProps) {
  const getIcon = (type: string) => {
    switch(type.toLowerCase()) {
      case 'bug':
        return <Bug className="h-5 w-5" />;
      case 'vulnerability':
        return <AlertTriangle className="h-5 w-5" />;
      default:
        return <AlertCircle className="h-5 w-5" />;
    }
  };

  return (
    <Card className="h-[500px] overflow-hidden">
      <CardHeader>
        <CardTitle>Bad Practices</CardTitle>
        <CardDescription>Detected issues in your repositories</CardDescription>
      </CardHeader>
      <CardContent className="p-0 overflow-auto h-[400px]">
        {badPractices.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-muted-foreground p-6">
            <p>No bad practices detected.</p>
            <p className="text-sm">All your repositories follow best practices!</p>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Issue</TableHead>
                <TableHead>Repository / File</TableHead>
                <TableHead>Severity</TableHead>
                <TableHead className="w-[100px]">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {badPractices.map((practice) => (
                <TableRow key={practice.id}>
                  <TableCell>
                    <div className="flex items-center">
                      <div className="mr-2">
                        {getIcon(practice.type)}
                      </div>
                      <div>
                        <div className="font-medium">{practice.type}</div>
                        <div className="text-sm text-muted-foreground">{practice.message}</div>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="font-medium">{practice.repository}</div>
                    <div className="text-sm text-muted-foreground">{practice.filePath}</div>
                  </TableCell>
                  <TableCell>
                    <Badge 
                      variant={
                        practice.severity === 'high' 
                          ? 'destructive' 
                          : practice.severity === 'medium' 
                            ? 'default' 
                            : 'outline'
                      }
                    >
                      {practice.severity}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {practice.url ? (
                      <Button variant="ghost" size="sm" asChild>
                        <a href={practice.url} target="_blank" rel="noopener noreferrer">
                          <ExternalLink className="h-4 w-4 mr-1" />
                          View
                        </a>
                      </Button>
                    ) : (
                      <Button variant="ghost" size="sm" disabled>
                        <ExternalLink className="h-4 w-4 mr-1" />
                        View
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}