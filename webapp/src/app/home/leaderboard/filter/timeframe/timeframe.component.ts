import { Component, computed, effect, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import dayjs from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmLabelModule } from '@spartan-ng/ui-label-helm';

interface SelectOption {
  id: number;
  value: string;
  label: string;
}

dayjs.extend(isoWeek);

function formatLabel(startDate: dayjs.Dayjs, endDate: dayjs.Dayjs | undefined) {
  const calendarWeek = startDate.isoWeek();
  if (!endDate || endDate.isSame(dayjs(), 'day')) {
    return `CW\xa0${calendarWeek}:\xa0${startDate.format('MMM D, h[am]')}\xa0-\xa0Today`;
  }

  const sameMonth = startDate.month() === endDate.month();
  if (sameMonth) {
    return `CW\xa0${calendarWeek}:\xa0${startDate.format('MMM D, h[am]')}\xa0-\xa0${endDate.format('D, h[am]')}`;
  } else {
    return `CW\xa0${calendarWeek}:\xa0${startDate.format('MMM D, h[am]')}\xa0-\xa0${endDate.format('MMM D, h[am]')}`;
  }
}

@Component({
  selector: 'app-leaderboard-filter-timeframe',
  standalone: true,
  imports: [BrnSelectModule, HlmSelectModule, HlmLabelModule, FormsModule],
  templateUrl: './timeframe.component.html'
})
export class LeaderboardFilterTimeframeComponent {
  after = signal<string>('');
  before = signal<string>('');
  value = signal<string>(`${this.after()}.${this.before()}`);

  placeholder = computed(() => {
    return formatLabel(dayjs(this.after()) ?? dayjs().day(2).startOf('hour').hour(9), !this.before() ? undefined : dayjs(this.before()));
  });

  options = computed(() => {
    const now = dayjs();
    let currentDate = dayjs().isoWeekday(2).startOf('hour').hour(9);
    const options: SelectOption[] = [
      {
        id: now.unix(),
        value: `${currentDate.format()}.${now.format()}`,
        label: formatLabel(currentDate, undefined)
      }
    ];

    for (let i = 0; i < 4; i++) {
      const startDate = currentDate.subtract(7, 'day');
      const endDate = currentDate.subtract(1, 'day');
      options.push({
        id: startDate.unix(),
        value: `${startDate.format()}.${endDate.format()}`,
        label: formatLabel(startDate, endDate)
      });
      currentDate = startDate;
    }

    return options;
  });

  constructor(private router: Router) {
    // init params
    const queryParams = this.router.parseUrl(this.router.url).queryParams;
    this.after.set(queryParams['after'] ?? dayjs().isoWeekday(2).hour(9).format());
    this.before.set(queryParams['before'] ?? dayjs().format());

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
