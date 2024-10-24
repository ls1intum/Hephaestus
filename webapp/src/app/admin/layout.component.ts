import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { RouterLinkActive, RouterModule, RouterOutlet } from '@angular/router';
import { HlmIconComponent, provideIcons } from '@spartan-ng/ui-icon-helm';
import { lucideUserCircle, lucideFileCog, lucideUsers2 } from '@ng-icons/lucide';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, HlmButtonModule, RouterOutlet, RouterLinkActive, HlmIconComponent],
  providers: [provideIcons({ lucideUserCircle, lucideFileCog, lucideUsers2 })],
  templateUrl: './layout.component.html'
})
export class AdminLayoutComponent {}
