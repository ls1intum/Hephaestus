import { RouterLink } from '@angular/router';
import { Component, computed, inject, input } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest, octChevronLeft, octNoEntry } from '@ng-icons/octicons';
import { cn } from '@app/utils';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { SecurityStore } from '@app/core/security/security-store.service';
import { LeaderboardEntry, PullRequestInfo } from 'app/core/modules/openapi';
import { TableBodyDirective } from 'app/ui/table/table-body.directive';
import { TableCellDirective } from 'app/ui/table/table-cell.directive';
import { TableHeadDirective } from 'app/ui/table/table-head.directive';
import { TableHeaderDirective } from 'app/ui/table/table-header.directive';
import { TableRowDirective } from 'app/ui/table/table-row.directive';
import { TableComponent } from 'app/ui/table/table.component';
import { ReviewsPopoverComponent } from './reviews-popover/reviews-popover.component';
import { HlmIconComponent, provideIcons } from '@spartan-ng/ui-icon-helm';
import { lucideAward } from '@ng-icons/lucide';

@Component({
  selector: 'app-leaderboard',
  standalone: true,
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
    HlmIconComponent,
    RouterLink
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

  // Leaderboard with the current user's entry at the top
  protected adjustedLeaderboard = computed(() => {
    const entries = this.leaderboard();
    if (!this.signedIn() || !entries || entries.length === 0) return entries;

    const currentUser = this.user();
    if (!currentUser) return entries;

    const userInLeaderboard = entries.find((entry) => entry.user.login.toLowerCase() === currentUser.username);

    return [userInLeaderboard ?? this.defaultSelfEntry(), ...entries];
  });

  defaultSelfEntry = computed(() => {
    return {
      user: {
        login: this.user()?.username,
        avatarUrl: `https://github.com/${this.user()?.username}.png`,
        name: this.user()?.name
      },
      rank: this.leaderboard()?.length ?? 0 + 1,
      score: 0,
      numberOfApprovals: 0,
      numberOfChangeRequests: 0,
      numberOfComments: 0,
      numberOfCodeComments: 0,
      numberOfReviewedPRs: 0,
      numberOfUnknowns: 0,
      reviewedPullRequests: [] as PullRequestInfo[]
    } as LeaderboardEntry;
  });

  trClass = (entry: LeaderboardEntry) => {
    return cn(
      'cursor-pointer',
      this.signedIn() && this.user()?.username.toLowerCase() === entry.user.login.toLowerCase() ? 'bg-accent dark:bg-accent/30 dark:hover:bg-accent/50' : ''
    );
  };
}
