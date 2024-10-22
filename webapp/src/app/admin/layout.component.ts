import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { RouterModule, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, HlmButtonModule, RouterOutlet],
  templateUrl: './layout.component.html'
})
export class AdminLayoutComponent {}
