import { Component, inject, input } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest, octChevronLeft, octNoEntry } from '@ng-icons/octicons';
import { LeaderboardEntry } from 'app/core/modules/openapi';
import { TableBodyDirective } from 'app/ui/table/table-body.directive';
import { TableCaptionDirective } from 'app/ui/table/table-caption.directive';
import { TableCellDirective } from 'app/ui/table/table-cell.directive';
import { TableFooterDirective } from 'app/ui/table/table-footer.directive';
import { TableHeadDirective } from 'app/ui/table/table-head.directive';
import { TableHeaderDirective } from 'app/ui/table/table-header.directive';
import { TableRowDirective } from 'app/ui/table/table-row.directive';
import { TableComponent } from 'app/ui/table/table.component';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { RouterLink, RouterOutlet } from '@angular/router';
import { SecurityStore } from '@app/core/security/security-store.service';
import { cn } from '@app/utils';

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [
    HlmAvatarModule,
    HlmSkeletonModule,
    TableComponent,
    TableBodyDirective,
    TableCaptionDirective,
    TableCellDirective,
    TableFooterDirective,
    TableHeaderDirective,
    TableHeadDirective,
    TableRowDirective,
    NgIconComponent,
    RouterLink,
    RouterOutlet
  ],
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

  trClass = (entry: LeaderboardEntry) => {
    return cn('cursor-pointer', this.signedIn() && this.user()?.username.toLowerCase() === entry.user.login.toLowerCase() ? 'bg-accent' : '');
  };

  leaderboard = input<LeaderboardEntry[]>();
  isLoading = input<boolean>();
}
