import { Component, inject } from '@angular/core';
import { HlmAccordionModule } from '@spartan-ng/ui-accordion-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideBotMessageSquare, lucideChevronDown, lucideHammer, lucideTrophy, lucideUsers } from '@ng-icons/lucide';
import { LeaderboardComponent } from '../home/leaderboard/leaderboard.component';
import { LeaderboardEntry, PullRequestInfo } from '@app/core/modules/openapi';
import { environment } from 'environments/environment';
import { SecurityStore } from '@app/core/security/security-store.service';

function samplePullRequests(count: number): Array<PullRequestInfo> {
  return Array.from({ length: count }, (_, i) => ({
    id: i,
    number: i,
    title: `PR #${i}`,
    state: 'OPEN',
    isDraft: false,
    isMerged: false,
    commentsCount: i,
    additions: i,
    deletions: i,
    htmlUrl: ''
  }));
}

@Component({
  selector: 'app-landing',
  templateUrl: './landing.component.html',
  styles: `
    .leaderboard-fade-out {
      position: relative;
      mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 1) 60%, rgba(0, 0, 0, 0));
    }
  `,
  imports: [HlmCardModule, HlmButtonDirective, NgIconComponent, HlmAccordionModule, LeaderboardComponent],
  providers: [provideIcons({ lucideBotMessageSquare, lucideChevronDown, lucideHammer, lucideTrophy, lucideUsers })]
})
export class LandingComponent {
  securityStore = inject(SecurityStore);

  protected faq = [
    {
      q: 'How does Hephaestus integrate with our existing workflow?',
      a: 'Hephaestus seamlessly integrates with GitHub, providing real-time updates and insights without disrupting your current processes.'
    },
    {
      q: 'Is Hephaestus suitable for large teams?',
      a: 'Hephaestus scales effortlessly, supporting teams of all sizes with customizable features for sub-teams to meet your specific needs.'
    },
    {
      q: 'How does the AI Mentor work?',
      a: 'The AI Mentor analyzes your GitHub activity and reflection inputs to provide personalized guidance, helping team members set and achieve meaningful goals.'
    }
  ];

  leaderboardEntries: LeaderboardEntry[] = [
    {
      rank: 1,
      score: 520,
      user: {
        id: 0,
        leaguePoints: 2000,
        login: 'codeMaster',
        avatarUrl: '/images/alice_developer.jpg',
        name: 'Alice Developer',
        htmlUrl: 'https://example.com/alice'
      },
      reviewedPullRequests: samplePullRequests(10),
      numberOfReviewedPRs: 18,
      numberOfApprovals: 8,
      numberOfChangeRequests: 7,
      numberOfComments: 2,
      numberOfUnknowns: 1,
      numberOfCodeComments: 5
    },
    {
      rank: 2,
      score: 431,
      user: {
        id: 1,
        leaguePoints: 1000,
        login: 'devWizard',
        avatarUrl: '/images/bob_builder.jpg',
        name: 'Bob Builder',
        htmlUrl: 'https://example.com/bob'
      },
      reviewedPullRequests: samplePullRequests(4),
      numberOfReviewedPRs: 8,
      numberOfApprovals: 1,
      numberOfChangeRequests: 5,
      numberOfComments: 2,
      numberOfUnknowns: 0,
      numberOfCodeComments: 21
    },
    {
      rank: 3,
      score: 302,
      user: {
        id: 2,
        leaguePoints: 1500,
        login: 'codeNinja',
        avatarUrl: '/images/charlie_coder.jpg',
        name: 'Charlie Coder',
        htmlUrl: 'https://example.com/charlie'
      },
      reviewedPullRequests: samplePullRequests(3),
      numberOfReviewedPRs: 5,
      numberOfApprovals: 3,
      numberOfChangeRequests: 1,
      numberOfComments: 0,
      numberOfUnknowns: 0,
      numberOfCodeComments: 2
    }
  ];

  signIn() {
    if (environment.keycloak.skipLoginPage) {
      const authUrl =
        `${environment.keycloak.url}/realms/${environment.keycloak.realm}/protocol/openid-connect/auth` +
        `?client_id=${encodeURIComponent(environment.keycloak.clientId)}` +
        `&redirect_uri=${encodeURIComponent(environment.clientUrl)}` +
        `&response_type=code` +
        `&scope=openid` +
        `&kc_idp_hint=${encodeURIComponent('github')}`;

      // Redirect the user to the Keycloak GitHub login
      window.location.href = authUrl;
    } else {
      this.securityStore.signIn();
    }
  }
}
