import { Component, computed, effect, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import dayjs from 'dayjs';
import weekOfYear from 'dayjs/plugin/weekOfYear';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmLabelModule } from '@spartan-ng/ui-label-helm';

interface SelectOption {
  id: number;
  value: string;
  label: string;
}

dayjs.extend(weekOfYear);

function formatLabel(startDate: dayjs.Dayjs, endDate: dayjs.Dayjs | undefined) {
  const calendarWeek = startDate.week();
  if (!endDate || endDate.isSame(dayjs(), 'day')) {
    return `CW\xa0${calendarWeek}:\xa0${startDate.format('MMM D')}\xa0-\xa0Today`;
  }

  const sameMonth = startDate.month() === endDate.month();
  if (sameMonth) {
    return `CW\xa0${calendarWeek}:\xa0${startDate.format('MMM D')}\xa0-\xa0${endDate.format('D')}`;
  } else {
    return `CW\xa0${calendarWeek}:\xa0${startDate.format('MMM D')}\xa0-\xa0${endDate.format('MMM D')}`;
  }
}

@Component({
  selector: 'app-leaderboard-filter-timeframe',
  standalone: true,
  imports: [RouterLink, BrnSelectModule, HlmSelectModule, HlmLabelModule, FormsModule],
  templateUrl: './timeframe.component.html'
})
export class LeaderboardFilterTimeframeComponent {
  after = signal<string>('');
  before = signal<string>('');
  value = signal<string>(`${this.after()}.${this.before()}`);

  placeholder = computed(() => {
    return formatLabel(dayjs(this.after()) ?? dayjs().day(1), this.before() === undefined ? undefined : dayjs(this.before()));
  });

  options = computed(() => {
    const now = dayjs();
    let currentDate = dayjs().day(1);
    const options: SelectOption[] = [
      {
        id: now.unix(),
        value: `${currentDate.format('YYYY-MM-DD')}.${now.format('YYYY-MM-DD')}`,
        label: formatLabel(currentDate, undefined)
      }
    ];

    for (let i = 0; i < 4; i++) {
      const startDate = currentDate.subtract(7, 'day');
      const endDate = currentDate.subtract(1, 'day');
      options.push({
        id: startDate.unix(),
        value: `${startDate.format('YYYY-MM-DD')}.${endDate.format('YYYY-MM-DD')}`,
        label: formatLabel(startDate, endDate)
      });
      currentDate = startDate;
    }

    return options;
  });

  constructor(private router: Router) {
    // init params
    const queryParams = this.router.parseUrl(this.router.url).queryParams;
    this.after.set(queryParams['after'] ?? dayjs().day(1).format('YYYY-MM-DD'));
    this.before.set(queryParams['before'] ?? dayjs().format('YYYY-MM-DD'));

    // persist changes in url
    effect(() => {
      if (this.value().length === 1) return;

      const queryParams = this.router.parseUrl(this.router.url).queryParams;
      const dates = this.value().split('.');
      queryParams['after'] = dates[0];
      queryParams['before'] = dates[1];

      this.router.navigate([], {
        queryParams
      });
    });
  }
}
