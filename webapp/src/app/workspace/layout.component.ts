import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { RouterLinkActive, RouterModule, RouterOutlet } from '@angular/router';
import { HlmIconComponent, provideIcons } from '@spartan-ng/ui-icon-helm';
import { lucideUserCircle, lucideCog, lucideUsers2, lucideAxe } from '@ng-icons/lucide';

type NavItem = { icon: string; label: string; route: string; exact?: boolean };
@Component({
  selector: 'app-workspace-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, HlmButtonModule, RouterOutlet, RouterLinkActive, HlmIconComponent],
  providers: [provideIcons({ lucideUserCircle, lucideCog, lucideUsers2, lucideAxe })],
  templateUrl: './layout.component.html'
})
export class WorkspaceLayoutComponent {
  navItems: NavItem[] = [
    { icon: 'lucideCog', label: 'Settings', route: '.', exact: true },
    { icon: 'lucideUserCircle', label: 'Users', route: 'users' },
    { icon: 'lucideUsers2', label: 'Teams', route: 'teams' },
    { icon: 'lucideAxe', label: 'Rules', route: 'rules' }
  ];
}
