import React from 'react';
import { Dialog, DialogTrigger, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { InfoIcon } from '@primer/octicons-react';
import { LeagueIcon } from './LeagueIcon';

export function LeagueInfoModal() {
  const [isOpen, setIsOpen] = React.useState(false);
  
  const leagueInfos = [
    { name: 'Bronze', points: 0, description: 'Starting league for newcomers' },
    { name: 'Silver', points: 500, description: 'Regular contributors' },
    { name: 'Gold', points: 1000, description: 'Established contributors' },
    { name: 'Diamond', points: 1500, description: 'Exceptional contributors' },
    { name: 'Master', points: 2000, description: 'Elite contributors' },
  ];

  return (
    <Dialog open={isOpen} onOpenChange={setIsOpen}>
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm" className="p-0 h-auto">
          <InfoIcon className="h-4 w-4 text-muted-foreground" />
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-[525px]">
        <DialogHeader>
          <DialogTitle>League System</DialogTitle>
          <DialogDescription>
            League points are earned through contributions to the project. Here's what each league represents:
          </DialogDescription>
        </DialogHeader>
        
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[100px]">League</TableHead>
              <TableHead>Points</TableHead>
              <TableHead>Description</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {leagueInfos.map((league) => (
              <TableRow key={league.name}>
                <TableCell className="flex items-center gap-2">
                  <LeagueIcon leaguePoints={league.points} />
                  <span>{league.name}</span>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {league.points === 0 ? '0 - 499' : 
                   league.points === 2000 ? '2000+' : 
                   `${league.points} - ${league.points + 499}`}
                </TableCell>
                <TableCell>{league.description}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        
        <DialogFooter>
          <Button variant="outline" onClick={() => setIsOpen(false)}>Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}