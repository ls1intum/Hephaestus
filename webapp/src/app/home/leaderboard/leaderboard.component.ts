import { Component, inject, input, signal } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest, octChevronLeft, octNoEntry } from '@ng-icons/octicons';
import { LeaderboardEntry, type PullRequestInfo } from 'app/core/modules/openapi';
import { TableBodyDirective } from 'app/ui/table/table-body.directive';
import { TableCellDirective } from 'app/ui/table/table-cell.directive';
import { TableHeadDirective } from 'app/ui/table/table-head.directive';
import { TableHeaderDirective } from 'app/ui/table/table-header.directive';
import { TableRowDirective } from 'app/ui/table/table-row.directive';
import { TableComponent } from 'app/ui/table/table.component';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { HlmPopoverModule } from '@spartan-ng/ui-popover-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { BrnPopoverComponent, BrnPopoverContentDirective, BrnPopoverTriggerDirective } from '@spartan-ng/ui-popover-brain';
import { HlmScrollAreaComponent } from '@spartan-ng/ui-scrollarea-helm';
import { HlmIconComponent, provideIcons } from '@spartan-ng/ui-icon-helm';

import { RouterLink } from '@angular/router';
import { SecurityStore } from '@app/core/security/security-store.service';
import { cn } from '@app/utils';
import { lucideCheck, lucideClipboardCopy } from '@ng-icons/lucide';

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
    NgIconComponent,
    RouterLink,
    HlmPopoverModule,
    BrnPopoverComponent,
    BrnPopoverContentDirective,
    BrnPopoverTriggerDirective,
    HlmScrollAreaComponent,
    HlmButtonModule,
    HlmIconComponent
  ],
  providers: [provideIcons({ lucideClipboardCopy, lucideCheck })],
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
  showCopySuccess = signal(false);

  trClass = (entry: LeaderboardEntry) => {
    return cn('cursor-pointer', this.signedIn() && this.user()?.username.toLowerCase() === entry.user.login.toLowerCase() ? 'bg-accent' : '');
  };

  calcScrollHeight = (arr: PullRequestInfo[]) => {
    return `min(200px, calc(${15 + 42 * arr.length}px))`;
  };
  displayPullRequestTitle = (title: string) => title.replace(/`([^`]+)`/g, '<code class="textCode">$1</code>');

  copyPullRequests = (entry: LeaderboardEntry) => {
    const htmlList = `<ul>
      ${entry.reviewedPullRequests.map((pr) => `<li><a href="${pr.htmlUrl}">#${pr.number}</a></li>`).join('\n')}
    </ul>`;

    const plainText = entry.reviewedPullRequests.map((pr) => `#${pr.number}`).join('\n');

    const clipboardItem = new ClipboardItem({
      'text/html': new Blob([htmlList], { type: 'text/html' }),
      'text/plain': new Blob([plainText], { type: 'text/plain' })
    });

    navigator.clipboard.write([clipboardItem]).catch(() => {
      // Fallback to plain text if html copying fails
      navigator.clipboard.writeText(plainText);
    });

    this.showCopySuccess.set(true);

    setTimeout(() => {
      this.showCopySuccess.set(false);
    }, 2000);
  };
}
