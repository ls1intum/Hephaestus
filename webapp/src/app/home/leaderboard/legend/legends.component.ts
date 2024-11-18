import { Component, computed, input, signal } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octFileDiff, octCheck, octComment, octCommentDiscussion, octGitPullRequest } from '@ng-icons/octicons';
import { HlmIconComponent } from '@spartan-ng/ui-icon-helm';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { provideIcons } from '@spartan-ng/ui-icon-helm';
import { lucideChevronsDown, lucideChevronsUp } from '@ng-icons/lucide';
import { cn } from '@app/utils';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'app-leaderboard-legend',
  standalone: true,
  imports: [HlmCardModule, NgIconComponent, HlmIconComponent, HlmButtonDirective],
  providers: [provideIcons({ lucideChevronsDown, lucideChevronsUp })],
  templateUrl: './legend.component.html'
})
export class LeaderboardLegendComponent {
  protected octFileDiff = octFileDiff;
  protected octCheck = octCheck;
  protected octComment = octComment;
  protected octCommentDiscussion = octCommentDiscussion;
  protected octGitPullRequest = octGitPullRequest;

  isLoading = input<boolean>();
  open = signal(false);

  contentClass = computed(() => cn('flex flex-wrap gap-y-4 gap-x-8 pt-2', { hidden: !this.open() }));

  toggleOpen() {
    this.open.set(!this.open());
  }
}
