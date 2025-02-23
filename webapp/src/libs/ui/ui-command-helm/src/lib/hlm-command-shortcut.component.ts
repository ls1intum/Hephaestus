import { Component } from '@angular/core';

@Component({
	selector: 'hlm-command-shortcut',
	template: '<ng-content />',
	host: {
		class: 'font-light ml-auto opacity-60 text-xs tracking-widest',
	},
})
export class HlmCommandShortcutComponent {}
