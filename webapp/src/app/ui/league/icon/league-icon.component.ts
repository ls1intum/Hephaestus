import { Component, computed, input } from '@angular/core';
import { cn, getLeagueFromPoints } from '@app/utils';
import { LucideAngularModule, Crown } from 'lucide-angular';
import { type VariantProps, cva } from 'class-variance-authority';

export const leagueVariants = cva('size-8', {
  variants: {
    size: {
      default: '',
      sm: 'size-6',
      lg: 'size-12',
      max: 'size-28',
      full: 'size-full'
    },
    league: {
      none: 'text-gray-400',
      bronze: 'text-league-bronze',
      silver: 'text-league-silver',
      gold: 'text-league-gold',
      emerald: 'text-league-emerald',
      diamond: 'text-league-diamond',
      master: 'text-league-master'
    }
  },
  defaultVariants: {
    size: 'default',
    league: 'none'
  }
});
type LeagueVariants = VariantProps<typeof leagueVariants>;

@Component({
  selector: 'app-icon-league',
  standalone: true,
  imports: [LucideAngularModule],
  template: ` <lucide-angular [img]="Crown" strokeWidth="2px" [class]="computedClass()" /> `
})
export class LeagueIconComponent {
  protected Crown = Crown;

  size = input<LeagueVariants['size']>('default');
  leaguePoints = input<number>();
  league = input<LeagueVariants['league']>('none');
  class = input<string>('');

  computedLeague = computed(() => (this.leaguePoints() ? getLeagueFromPoints(this.leaguePoints()!)?.name.toLowerCase() : this.league()));

  computedClass = computed(() => cn(leagueVariants({ size: this.size(), league: this.computedLeague() as LeagueVariants['league'] }), this.class()));
}
