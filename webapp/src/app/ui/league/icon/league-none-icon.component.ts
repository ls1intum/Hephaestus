import { Component, input } from '@angular/core';

@Component({
  selector: 'app-league-none-icon',
  template: `<svg [class]="class()" width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path
      fill-rule="evenodd"
      clip-rule="evenodd"
      d="M15.4641 20L17.1962 19L16.1962 17.268L14.4641 18.268L15.4641 20ZM9.5359 18.2679L7.80385 17.2679L6.80385 19L8.5359 20L9.5359 18.2679ZM3.33976 13H5.33976V11H3.33976V13ZM6.80386 5L7.80386 6.73205L9.53591 5.73205L8.53591 4L6.80386 5ZM20.6603 11H18.6603V13H20.6603V11ZM17.1962 5L15.4641 4L14.4641 5.73205L16.1962 6.73205L17.1962 5Z"
      fill="hsl(var(--muted-foreground))"
    />
    <path
      fill-rule="evenodd"
      clip-rule="evenodd"
      d="M14.1651 3.25L12 2L9.83494 3.25L10.8349 4.98205L12 4.3094L13.1651 4.98205L14.1651 3.25ZM3.33975 14.5H5.33975V15.8453L6.50482 16.5179L5.50482 18.25L3.33975 17V14.5ZM9.83494 20.75L12 22L14.1651 20.75L13.1651 19.018L12 19.6906L10.8349 19.018L9.83494 20.75ZM20.6603 9.5H18.6603V8.1547L17.4952 7.48205L18.4952 5.75L20.6603 7V9.5ZM20.6603 14.5H18.6603V15.8453L17.4952 16.5179L18.4952 18.25L20.6603 17V14.5ZM3.33975 9.5H5.33975V8.1547L6.50482 7.48205L5.50482 5.75L3.33975 7V9.5Z"
      fill="hsl(var(--muted-foreground))"
    />
  </svg>`
})
export class LeagueNoneIconComponent {
  class = input<string>('');
}
