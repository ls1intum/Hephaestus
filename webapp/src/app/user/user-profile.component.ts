import { Component, inject } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { PullRequestDTO, PullRequestReviewDTO, UserService } from 'app/core/modules/openapi';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { HlmIconModule } from 'libs/ui/ui-icon-helm/src/index';
import { BrnTooltipContentDirective } from '@spartan-ng/ui-tooltip-brain';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmScrollAreaComponent } from '@spartan-ng/ui-scrollarea-helm';
import { ReviewActivityCardComponent } from '@app/user/review-activity-card/review-activity-card.component';
import { IssueCardComponent } from '@app/user/issue-card/issue-card.component';
import { combineLatest, lastValueFrom, map, timer } from 'rxjs';
import { CircleX, LucideAngularModule, Info } from 'lucide-angular';
import { UserHeaderComponent } from './header/header.component';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [
    LucideAngularModule,
    NgIconComponent,
    ReviewActivityCardComponent,
    IssueCardComponent,
    HlmAvatarModule,
    HlmSkeletonModule,
    HlmIconModule,
    HlmTooltipComponent,
    HlmTooltipTriggerDirective,
    BrnTooltipContentDirective,
    HlmButtonModule,
    HlmScrollAreaComponent,
    UserHeaderComponent
  ],
  templateUrl: './user-profile.component.html'
})
export class UserProfileComponent {
  userService = inject(UserService);

  protected CircleX = CircleX;
  protected Info = Info;
  // get user id from the url
  protected userLogin: string | null = null;

  constructor(private route: ActivatedRoute) {
    this.userLogin = this.route.snapshot.paramMap.get('id');
  }

  skeletonReviews = this.genSkeletonArray<PullRequestReviewDTO>(3);
  skeletonPullRequests = this.genSkeletonArray<PullRequestDTO>(2);

  genSkeletonArray<T>(length: number): T[] {
    return Array.from({ length }, (_, i) => ({ id: i })) as T[];
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  calcScrollHeight = (arr: any[] | Set<any> | undefined, elHeight = 100) => {
    if (Array.isArray(arr)) return `min(400px, calc(${arr.length * elHeight}px + ${8 * arr.length}px))`;
    return '400px';
  };

  query = injectQuery(() => ({
    queryKey: ['user', { id: this.userLogin }],
    enabled: !!this.userLogin,
    queryFn: async () => lastValueFrom(combineLatest([this.userService.getUserProfile(this.userLogin!), timer(400)]).pipe(map(([user]) => user)))
  }));
}
