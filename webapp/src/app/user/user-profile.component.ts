import { Component, inject, signal } from '@angular/core';
import { PullRequestInfo, PullRequestReviewInfo, UserService } from 'app/core/modules/openapi';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { HlmIconModule } from 'libs/ui/ui-icon-helm/src/index';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmAlertModule } from '@spartan-ng/ui-alert-helm';
import { ReviewActivityCardComponent } from '@app/user/review-activity-card/review-activity-card.component';
import { IssueCardComponent } from '@app/user/issue-card/issue-card.component';
import { combineLatest, lastValueFrom, map, timer } from 'rxjs';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideCircleX, lucideInfo } from '@ng-icons/lucide';
import { UserHeaderComponent } from './header/header.component';
import { NgScrollbarModule } from 'ngx-scrollbar';
import { HlmScrollAreaDirective } from '@spartan-ng/ui-scrollarea-helm';

@Component({
  imports: [
    NgIconComponent,
    ReviewActivityCardComponent,
    IssueCardComponent,
    HlmAvatarModule,
    HlmSkeletonModule,
    HlmIconModule,
    HlmButtonModule,
    UserHeaderComponent,
    HlmAlertModule,
    NgScrollbarModule,
    HlmScrollAreaDirective,
    HlmAlertModule,
    HlmScrollAreaDirective
  ],
  providers: [provideIcons({ lucideCircleX, lucideInfo })],
  templateUrl: './user-profile.component.html'
})
export class UserProfileComponent {
  userService = inject(UserService);

  // get user id from the url
  protected userLogin = signal<string | null>(null);

  constructor(private route: ActivatedRoute) {
    this.userLogin.set(this.route.snapshot.paramMap.get('id'));
    this.route.params.subscribe((params) => {
      if (params['id']) {
        this.userLogin.set(params['id']);
      }
    });
  }

  skeletonReviews = this.genSkeletonArray<PullRequestReviewInfo>(3);
  skeletonPullRequests = this.genSkeletonArray<PullRequestInfo>(2);

  genSkeletonArray<T>(length: number): T[] {
    return Array.from({ length }, (_, i) => ({ id: i })) as T[];
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  calcScrollHeight = (arr: any[] | Set<any> | undefined, elHeight = 100) => {
    if (Array.isArray(arr)) return `min(400px, calc(${arr.length * elHeight}px + ${8 * arr.length}px))`;
    return '400px';
  };

  query = injectQuery(() => ({
    queryKey: ['user', { id: this.userLogin() }],
    enabled: !!this.userLogin(),
    queryFn: async () => lastValueFrom(combineLatest([this.userService.getUserProfile(this.userLogin()!), timer(400)]).pipe(map(([user]) => user)))
  }));
}
