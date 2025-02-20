import { RouterLink } from '@angular/router';
import { Component, inject, input } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest, octChevronLeft, octNoEntry } from '@ng-icons/octicons';
import { cn } from '@app/utils';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { SecurityStore } from '@app/core/security/security-store.service';
import { LeaderboardEntry } from 'app/core/modules/openapi';
import { TableBodyDirective } from 'app/ui/table/table-body.directive';
import { TableCellDirective } from 'app/ui/table/table-cell.directive';
import { TableHeadDirective } from 'app/ui/table/table-head.directive';
import { TableHeaderDirective } from 'app/ui/table/table-header.directive';
import { TableRowDirective } from 'app/ui/table/table-row.directive';
import { TableComponent } from 'app/ui/table/table.component';
import { ReviewsPopoverComponent } from './reviews-popover/reviews-popover.component';
import { HlmIconDirective } from '@spartan-ng/ui-icon-helm';
import { provideIcons } from '@ng-icons/core';
import { lucideAward } from '@ng-icons/lucide';
import { LeagueIconComponent } from '@app/ui/league/icon/league-icon.component';

@Component({
  selector: 'app-leaderboard',
  imports: [
    HlmAvatarModule,
    HlmSkeletonModule,
    TableComponent,
    TableBodyDirective,
    TableCellDirective,
    TableHeaderDirective,
    TableHeadDirective,
    TableRowDirective,
    ReviewsPopoverComponent,
    NgIconComponent,
    HlmIconDirective,
    RouterLink,
    LeagueIconComponent
  ],
  providers: [provideIcons({ lucideAward })],
  templateUrl: './leaderboard.component.html'
})
export class LeaderboardComponent {
  securityStore = inject(SecurityStore);
  protected octFileDiff = octFileDiff;
  protected octCheck = octCheck;
  protected octComment = octComment;
  protected octCommentDiscussion = octCommentDiscussion;
  protected octGitPullRequest = octGitPullRequest;
  protected octChevronLeft = octChevronLeft;
  protected octNoEntry = octNoEntry;

  signedIn = this.securityStore.signedIn;
  user = this.securityStore.loadedUser;

  leaderboard = input<LeaderboardEntry[]>();
  isLoading = input<boolean>();

  trClass = (entry: LeaderboardEntry) => {
    return cn(
      'cursor-pointer',
      this.signedIn() && this.user()?.username.toLowerCase() === entry.user.login.toLowerCase() ? 'bg-accent dark:bg-accent/30 dark:hover:bg-accent/50' : ''
    );
  };
}
