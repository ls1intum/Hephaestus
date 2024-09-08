import { HttpClient } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { Leaderboard } from 'app/@types/leaderboard';
import { TableComponent } from 'app/ui/table/table.component';
import { lastValueFrom } from 'rxjs';

const defaultData: Leaderboard.Entry[] = [
  { githubName: 'shadcn', name: 'I', score: 90, total: 100, changes_requested: 0, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'A', score: 10, total: 100, changes_requested: 1, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'B', score: 20, total: 100, changes_requested: 0, approvals: 1, comments: 0 },
  { githubName: 'shadcn', name: 'C', score: 30, total: 100, changes_requested: 0, approvals: 0, comments: 1 },
  { githubName: 'shadcn', name: 'D', score: 40, total: 100, changes_requested: 0, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'E', score: 50, total: 100, changes_requested: 0, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'F', score: 60, total: 100, changes_requested: 0, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'G', score: 70, total: 100, changes_requested: 0, approvals: 0, comments: 0 },
  { githubName: 'shadcn', name: 'H', score: 80, total: 100, changes_requested: 0, approvals: 0, comments: 0 }
];

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [TableComponent],
  templateUrl: './leaderboard.component.html'
})
export class LeaderboardComponent {
  http = inject(HttpClient);

  query = injectQuery(() => ({
    queryKey: ['leaderboard'],
    queryFn: async () => lastValueFrom(this.http.get('http://127.0.0.1:8080/leaderboard')) as Promise<Leaderboard.Entry[]>,
    gcTime: Infinity
  }));

  leaderboard = computed(() => {
    const data = this.query.data();
    if (!data) {
      return defaultData;
    }
    return data;
  });
}
