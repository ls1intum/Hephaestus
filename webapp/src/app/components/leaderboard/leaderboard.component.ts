import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { Leaderboard } from 'app/@types/leaderboard';
import { TableBodyDirective } from 'app/ui/table/table-body.directive';
import { TableCaptionDirective } from 'app/ui/table/table-caption.directive';
import { TableCellDirective } from 'app/ui/table/table-cell.directive';
import { TableFooterDirective } from 'app/ui/table/table-footer.directive';
import { TableHeadDirective } from 'app/ui/table/table-head.directive';
import { TableHeaderDirective } from 'app/ui/table/table-header.directive';
import { TableRowDirective } from 'app/ui/table/table-row.directive';
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
  imports: [TableComponent, TableBodyDirective, TableCaptionDirective, TableCellDirective, TableFooterDirective, TableHeaderDirective, TableHeadDirective, TableRowDirective],
  templateUrl: './leaderboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LeaderboardComponent {
  http = inject(HttpClient);

  query = injectQuery(() => ({
    queryKey: ['leaderboard'],
    queryFn: async () => lastValueFrom(this.http.get('http://127.0.0.1:8080/leaderboard')) as Promise<Leaderboard.Entry[]>,
    gcTime: Infinity
  }));
  // TODO: replace with leadboard service when merged
  // pullrequest = inject(PullRequestService);

  // query = injectQuery(() => ({
  //   queryKey: ['leaderboard'],
  //   queryFn: async () => lastValueFrom(this.pullrequest.getPullRequest(1)),
  //   gcTime: Infinity
  // }));

  leaderboard = computed(() => {
    let data = this.query.data() ?? defaultData;
    data = data.sort((a, b) => b.score - a.score);
    return data;
  });
}
