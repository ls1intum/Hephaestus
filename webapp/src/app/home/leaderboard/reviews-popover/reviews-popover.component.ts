import { Component, computed, input, signal } from '@angular/core';
import { octGitPullRequest } from '@ng-icons/octicons';
import { lucideClipboardCopy, lucideCheck } from '@ng-icons/lucide';
import { HlmPopoverModule } from '@spartan-ng/ui-popover-helm';
import { BrnPopoverComponent, BrnPopoverContentDirective, BrnPopoverTriggerDirective } from '@spartan-ng/brain/popover';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { NgScrollbarModule } from 'ngx-scrollbar';
import { HlmScrollAreaDirective } from '@spartan-ng/ui-scrollarea-helm';

export interface PullRequestInfo {
  id: number;
  repository?: {
    name: string;
  };
  number: number;
  title: string;
  htmlUrl: string;
}

@Component({
  selector: 'app-reviews-popover',
  imports: [
    HlmPopoverModule,
    BrnPopoverComponent,
    BrnPopoverContentDirective,
    BrnPopoverTriggerDirective,
    HlmButtonModule,
    NgIconComponent,
    NgScrollbarModule,
    HlmScrollAreaDirective,
    HlmScrollAreaDirective
  ],
  providers: [provideIcons({ octGitPullRequest, lucideCheck, lucideClipboardCopy })],
  templateUrl: './reviews-popover.component.html'
})
export class ReviewsPopoverComponent {
  highlight = input.required<boolean>();
  reviewedPRs = input.required<PullRequestInfo[]>();

  sortedReviewedPRs = computed(() =>
    this.reviewedPRs().sort((a, b) => {
      if (a.repository?.name === b.repository?.name) {
        return a.number - b.number;
      }
      return (a.repository?.name ?? '').localeCompare(b.repository?.name ?? '');
    })
  );
  showCopySuccess = signal(false);

  private timeoutShowCopySuccess: ReturnType<typeof setTimeout> | undefined;

  copyPullRequests() {
    const htmlList = `<ul>
      ${this.sortedReviewedPRs()
        .map((pullRequest) => `<li><a href="${pullRequest.htmlUrl}">${pullRequest.repository?.name ?? ''} #${pullRequest.number}</a></li>`)
        .join('\n')}
    </ul>`;

    // As markdown text
    const plainText = this.sortedReviewedPRs()
      .map((pullRequest) => `[${pullRequest.repository?.name ?? ''} #${pullRequest.number}](${pullRequest.htmlUrl})`)
      .join('\n');

    const clipboardItem = new ClipboardItem({
      'text/html': new Blob([htmlList], { type: 'text/html' }),
      'text/plain': new Blob([plainText], { type: 'text/plain' })
    });

    navigator.clipboard.write([clipboardItem]).catch(() => {
      // Fallback to plain text if html copying fails
      navigator.clipboard.writeText(plainText);
    });

    this.showCopySuccess.set(true);

    if (this.timeoutShowCopySuccess) {
      clearTimeout(this.timeoutShowCopySuccess);
    }
    this.timeoutShowCopySuccess = setTimeout(() => {
      this.showCopySuccess.set(false);
    }, 2000);
  }
}
