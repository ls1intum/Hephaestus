import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LeaderboardEntry, LeaderboardService } from 'app/core/modules/openapi';
import { PullRequestApprovedIconComponent } from 'app/ui/icons/PullRequestApprovedIcon.component';
import { PullRequestChangesRequestedIconComponent } from 'app/ui/icons/PullRequestChangesRequestedIcon.component';
import { PullRequestCommentIconComponent } from 'app/ui/icons/PullRequestCommentIcon.component';
import { TableBodyDirective } from 'app/ui/table/table-body.directive';
import { TableCaptionDirective } from 'app/ui/table/table-caption.directive';
import { TableCellDirective } from 'app/ui/table/table-cell.directive';
import { TableFooterDirective } from 'app/ui/table/table-footer.directive';
import { TableHeadDirective } from 'app/ui/table/table-head.directive';
import { TableHeaderDirective } from 'app/ui/table/table-header.directive';
import { TableRowDirective } from 'app/ui/table/table-row.directive';
import { TableComponent } from 'app/ui/table/table.component';
import { lastValueFrom } from 'rxjs';

const defaultData: LeaderboardEntry[] = [
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'I',
    score: 90,
    changesRequested: 0,
    approvals: 0,
    comments: 0
  },
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'A',
    score: 10,
    changesRequested: 1,
    approvals: 0,
    comments: 0
  },
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'B',
    score: 20,
    changesRequested: 0,
    approvals: 1,
    comments: 0
  },
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'C',
    score: 30,
    changesRequested: 0,
    approvals: 0,
    comments: 1
  },
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'D',
    score: 40,
    changesRequested: 0,
    approvals: 0,
    comments: 0
  },
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'E',
    score: 50,
    changesRequested: 0,
    approvals: 0,
    comments: 0
  },
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'F',
    score: 60,
    changesRequested: 0,
    approvals: 0,
    comments: 0
  },
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'G',
    score: 70,
    changesRequested: 0,
    approvals: 0,
    comments: 0
  },
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'H',
    score: 80,
    changesRequested: 0,
    approvals: 0,
    comments: 0
  }
];

const sortByScore = (a: LeaderboardEntry, b: LeaderboardEntry) => {
  if (!b.score) {
    return -1;
  }
  if (!a.score) {
    return 1;
  }
  return b.score - a.score;
};

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [
    TableComponent,
    TableBodyDirective,
    TableCaptionDirective,
    TableCellDirective,
    TableFooterDirective,
    TableHeaderDirective,
    TableHeadDirective,
    TableRowDirective,
    PullRequestChangesRequestedIconComponent,
    PullRequestApprovedIconComponent,
    PullRequestCommentIconComponent
  ],
  templateUrl: './leaderboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LeaderboardComponent {
  leaderboardService = inject(LeaderboardService);

  query = injectQuery(() => ({
    queryKey: ['leaderboard'],
    queryFn: async () => lastValueFrom(this.leaderboardService.getLeaderboard()),
    gcTime: Infinity
  }));

  leaderboard = computed(() => {
    let data = this.query.data() ?? defaultData;
    data = data.filter((entry) => entry.type === LeaderboardEntry.TypeEnum.User).sort(sortByScore);
    return data;
  });
}
