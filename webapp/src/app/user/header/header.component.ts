import { Component, computed, input } from '@angular/core';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { octClockFill } from '@ng-icons/octicons';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { HlmIconModule } from 'libs/ui/ui-icon-helm/src/index';
import { BrnTooltipContentDirective } from '@spartan-ng/brain/tooltip';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import dayjs from 'dayjs/esm';
import advancedFormat from 'dayjs/esm/plugin/advancedFormat';
import { RepositoryInfo, UserInfo } from '@app/core/modules/openapi';
import { LeagueIconComponent } from '@app/ui/league/icon/league-icon.component';

dayjs.extend(advancedFormat);

const repoImages: { [key: string]: string } = {
  Hephaestus: 'https://github.com/ls1intum/Hephaestus/raw/refs/heads/develop/docs/images/hammer_bg.svg',
  Artemis: 'https://artemis.tum.de/public/images/logo.png',
  Athena: 'https://raw.githubusercontent.com/ls1intum/Athena/develop/playground/public/logo.png'
};

@Component({
  selector: 'app-user-header',
  imports: [
    NgIconComponent,
    HlmAvatarModule,
    HlmSkeletonModule,
    HlmIconModule,
    HlmTooltipComponent,
    HlmTooltipTriggerDirective,
    BrnTooltipContentDirective,
    HlmButtonModule,
    LeagueIconComponent
  ],
  providers: [provideIcons({ octClockFill })],
  templateUrl: './header.component.html'
})
export class UserHeaderComponent {
  isLoading = input(false);
  user = input<UserInfo>();
  firstContribution = input<string>();
  contributedRepositories = input<RepositoryInfo[]>();
  leaguePoints = input<number>();

  displayFirstContribution = computed(() => {
    if (this.firstContribution()) {
      return dayjs(this.firstContribution()).format('Do [of] MMMM YYYY');
    }
    return null;
  });

  getRepositoryImage = (name: string) => (name ? repoImages[name.split('/')[1]] : null) || 'https://avatars.githubusercontent.com/u/11064260?v=4';
}
