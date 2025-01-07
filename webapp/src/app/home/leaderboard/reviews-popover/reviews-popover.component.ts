import { Component, computed, input, signal } from '@angular/core';
import { LucideAngularModule, ClipboardCopy, Check } from 'lucide-angular';
import { octGitPullRequest } from '@ng-icons/octicons';
import { HlmPopoverModule } from '@spartan-ng/ui-popover-helm';
import { BrnPopoverComponent, BrnPopoverContentDirective, BrnPopoverTriggerDirective } from '@spartan-ng/ui-popover-brain';
import { HlmScrollAreaComponent } from '@spartan-ng/ui-scrollarea-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { NgIconComponent } from '@ng-icons/core';

interface PullRequestInfo {
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
        HlmScrollAreaComponent,
        HlmButtonModule,
        NgIconComponent,
        LucideAngularModule
    ],
    templateUrl: './reviews-popover.component.html'
})
export class ReviewsPopoverComponent {
  protected ClipboardCopy = ClipboardCopy;
  protected Check = Check;
  protected octGitPullRequest = octGitPullRequest;

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
