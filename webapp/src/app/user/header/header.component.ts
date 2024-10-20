import { Component, computed, input } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octClockFill } from '@ng-icons/octicons';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { HlmIconModule } from 'libs/ui/ui-icon-helm/src/index';
import { BrnTooltipContentDirective } from '@spartan-ng/ui-tooltip-brain';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { LucideAngularModule } from 'lucide-angular';
import dayjs from 'dayjs';
import advancedFormat from 'dayjs/plugin/advancedFormat';

dayjs.extend(advancedFormat);

type UserHeaderProps = {
  avatarUrl: string;
  login: string;
  firstContribution: string;
  repositories: Set<string>;
};

const repoImages: { [key: string]: string } = {
  Hephaestus: 'https://github.com/ls1intum/Hephaestus/raw/refs/heads/develop/docs/images/hammer.svg',
  Artemis: 'https://artemis.in.tum.de/public/images/logo.png',
  Athena: 'https://raw.githubusercontent.com/ls1intum/Athena/develop/playground/public/logo.png'
};

@Component({
  selector: 'app-user-header',
  standalone: true,
  imports: [
    LucideAngularModule,
    NgIconComponent,
    HlmAvatarModule,
    HlmSkeletonModule,
    HlmIconModule,
    HlmTooltipComponent,
    HlmTooltipTriggerDirective,
    BrnTooltipContentDirective,
    HlmButtonModule
  ],
  templateUrl: './header.component.html'
})
export class UserHeaderComponent {
  protected octClockFill = octClockFill;

  isLoading = input(false);
  userData = input<UserHeaderProps>();

  displayFirstContribution = computed(() => {
    if (this.userData()?.firstContribution) {
      return dayjs(this.userData()?.firstContribution).format('Do [of] MMMM YYYY');
    }
    return null;
  });

  getRepositoryImage = (name: string) => (name ? repoImages[name.split('/')[1]] : null) || 'https://avatars.githubusercontent.com/u/11064260?v=4';
}
