import { Component, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { SelectComponent, SelectOption } from 'app/ui/select/select.component';
import dayjs from 'dayjs';
import { LabelComponent } from '../../../ui/label/label.component';
import { octFilter } from '@ng-icons/octicons';
import { NgIconComponent } from '@ng-icons/core';

@Component({
  selector: 'app-leaderboard-filter',
  standalone: true,
  imports: [SelectComponent, RouterLink, LabelComponent, NgIconComponent],
  templateUrl: './filter.component.html'
})
export class LeaderboardFilterComponent {
  protected octFilter = octFilter;
  after = input<string>();
  before = input<string>();

  options = signal<SelectOption[]>([]);

  constructor(private router: Router) {
    // get monday - sunday of last 4 weeks
    const options = new Array<SelectOption>();
    const now = dayjs();
    let currentDate = dayjs().day(1);
    options.push({
      id: now.unix(),
      value: `${currentDate.format('YYYY-MM-DD')}.${now.format('YYYY-MM-DD')}`,
      label: `${currentDate.format('MMM D')} - ${now.format('MMM D')}`
    });
    for (let i = 0; i < 4; i++) {
      const startDate = currentDate.subtract(7, 'day');
      const endDate = currentDate.subtract(1, 'day');
      options.push({
        id: startDate.unix(),
        value: `${startDate.format('YYYY-MM-DD')}.${endDate.format('YYYY-MM-DD')}`,
        label: `${startDate.format('MMM D')} - ${endDate.format('MMM D')}`
      });
      currentDate = startDate;
    }
    this.options.set(options);
  }

  selectFn(value: string) {
    const dates = value.split('.');
    // change query params
    this.router.navigate([], {
      queryParams: {
        after: dates[0],
        before: dates[1]
      }
    });
  }
}
