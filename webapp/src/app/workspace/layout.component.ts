import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { RouterLinkActive, RouterModule, RouterOutlet } from '@angular/router';
import { HlmIconDirective } from '@spartan-ng/ui-icon-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideCircleUser, lucideCog, lucideUsers } from '@ng-icons/lucide';

type NavItem = { icon: string; label: string; route: string; exact?: boolean };
@Component({
  selector: 'app-workspace-layout',
  imports: [CommonModule, RouterModule, ReactiveFormsModule, HlmButtonModule, RouterOutlet, RouterLinkActive, HlmIconDirective, NgIconComponent],
  providers: [provideIcons({ lucideCircleUser, lucideCog, lucideUsers })],
  templateUrl: './layout.component.html'
})
export class WorkspaceLayoutComponent {
  navItems: NavItem[] = [
    { icon: 'lucideCog', label: 'Settings', route: '.', exact: true },
    { icon: 'lucideCircleUser', label: 'Users', route: 'users' },
    { icon: 'lucideUsers', label: 'Teams', route: 'teams' }
  ];
}
