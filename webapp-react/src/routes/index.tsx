import { createFileRoute } from "@tanstack/react-router";
import { useState } from 'react';

import { 
	Hammer,
	Trophy,
	Users,
	BotMessageSquare,
} from 'lucide-react';
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import type { LeaderboardEntry, PullRequestInfo } from '@/lib/api/models';
import Leaderboard from '@/components/Leaderboard';

export const Route = createFileRoute("/")({
	component: Landing,
});

// Helper function to create sample pull requests
const samplePullRequests = (count: number): PullRequestInfo[] => {
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
    htmlUrl: '',
  } as PullRequestInfo));
};

// Sample leaderboard data for demonstration
const leaderboardEntries: LeaderboardEntry[] = [
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

// FAQ entries
const faqItems = [
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

function Landing() {
  const [isSigningIn, setIsSigningIn] = useState(false);

  const signIn = () => {
    setIsSigningIn(true);
    // In a real implementation, you'd call your auth service here
    const authUrl = `https://github.com/login/oauth/authorize?client_id=your_client_id&redirect_uri=${encodeURIComponent(window.location.origin)}`;
    window.location.href = authUrl;
  };

  return (
    <div className="flex flex-col">
      {/* Hero Section */}
      <section className="w-full pt-12 md:pt-24 lg:pt-32 xl:pt-48 text-foreground">
        <div className="container px-4 md:px-6">
          <div className="flex flex-col items-center space-y-4 text-center">
            <div className="space-y-2">
              <h1 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl lg:text-6xl/none">Forge Healthy Software Teams with Hephaestus</h1>
              <p className="mx-auto max-w-[700px] text-secondary-foreground md:text-xl">
                Empower your novice engineers, foster collaboration, and build sustainable development practices with our AI-powered platform.
              </p>
            </div>
            <div className="space-x-4">
              <Button onClick={signIn} disabled={isSigningIn}>
                {isSigningIn ? 'Signing in...' : 'Get Started'}
              </Button>
              <Button variant="outline" asChild>
                <a href="#features">Learn More</a>
              </Button>
            </div>
          </div>
        </div>
        <div className="mx-4 md:mx-10">
          <div className="shadow-2xl mt-24 max-w-4xl mx-auto">
            <div className="border border-b-0 rounded-bl-none rounded-br-none rounded-md border-input overflow-auto leaderboard-fade-out pointer-events-none">
              <Leaderboard leaderboard={leaderboardEntries} />
            </div>
          </div>
        </div>
      </section>
      
      {/* Features Section */}
      <section id="features" className="relative left-1/2 -translate-x-1/2 w-screen py-12 md:py-24 lg:py-32 bg-muted -mt-2">
        <div className="container px-4 md:px-6">
          <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl text-center mb-12">Forge Excellence with Our Features</h2>
          <div className="grid gap-6 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <Trophy className="text-3xl mb-2 text-yellow-500" />
                <CardTitle>Code Review Gamification</CardTitle>
                <CardDescription>Transform code reviews into an engaging, competitive experience</CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="list-disc pl-5 space-y-2">
                  <li>Dynamic weekly leaderboards with real-time GitHub integration</li>
                  <li>Team competitions across multiple repositories</li>
                  <li>Elo-like ranking system in structured leagues</li>
                  <li>Automated Slack recognition for top reviewers</li>
                </ul>
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <BotMessageSquare className="text-3xl mb-2 text-cyan-500 hover:text-cyan-500" />
                <CardTitle>AI Mentor</CardTitle>
                <CardDescription>Personalized guidance for continuous improvement</CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="list-disc pl-5 space-y-2">
                  <li>AI-assisted weekly reflective sessions</li>
                  <li>Automated standups from reflection insights</li>
                  <li>GitHub activity awareness for data-driven feedback</li>
                  <li>Structured goal-setting and adjustment</li>
                </ul>
              </CardContent>
            </Card>
          </div>
        </div>
      </section>
      
      {/* Why Choose Section */}
      <section className="w-full py-12 md:py-24 lg:py-32">
        <div className="container px-4 md:px-6">
          <div className="grid gap-6 lg:grid-cols-[1fr_400px] lg:gap-12 xl:grid-cols-[1fr_600px]">
            <img src="/images/agile_hephaestus.png" alt="Agile Hephaestus" width="600" height="400" className="mx-auto aspect-square sm:max-w-xs md:sm:max-w-md lg:order-last" />
            <div className="flex flex-col justify-center space-y-4">
              <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl">Why Choose Hephaestus?</h2>
              <p className="max-w-[600px] text-gray-500 md:text-xl/relaxed lg:text-base/relaxed xl:text-xl/relaxed">
                Hephaestus, named after the Greek god of craftsmen, fuses creativity with technical skill to forge the future of software development teams.
              </p>
              <ul className="grid gap-6 mt-6">
                <li className="flex items-center gap-2">
                  <Hammer className="text-2xl text-primary" />
                  <span>Empower novice engineers with actionable tools</span>
                </li>
                <li className="flex items-center gap-2">
                  <Users className="text-2xl text-primary" />
                  <span>Foster sustainable, collaborative practices</span>
                </li>
                <li className="flex items-center gap-2">
                  <Trophy className="text-2xl text-primary" />
                  <span>Gamify code reviews for increased engagement</span>
                </li>
                <li className="flex items-center gap-2">
                  <BotMessageSquare className="text-2xl text-primary" />
                  <span>Leverage AI for personalized team growth</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>
      
      {/* Testimonials Section */}
      <section id="testimonials" className="relative left-1/2 -translate-x-1/2 w-screen py-12 md:py-24 lg:py-32 bg-muted">
        <div className="container px-4 md:px-6 flex flex-col items-center">
          <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl text-center mb-12">What Teams Are Saying</h2>
          <div className="lg:w-1/2">
            <Card>
              <CardHeader>
                <CardTitle>Instant Team Insights</CardTitle>
                <CardDescription>
                  Ege Kocabas at <Button variant="link" className="text-muted-foreground p-0" asChild>
                    <a href="https://github.com/ls1intum/helios">Helios</a>
                  </Button>
                </CardDescription>
              </CardHeader>
              <CardContent>
                "I heavily use Hepheastus just to see how our team is doing. It also helps me to write all the things I have made for a week when its time to fill a weekly report."
              </CardContent>
            </Card>
          </div>
        </div>
      </section>
      
      {/* FAQ Section */}
      <section id="faq" className="w-full py-12 md:py-24 lg:py-32">
        <div className="container px-4 md:px-6">
          <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl text-center mb-12">Frequently Asked Questions</h2>
          <div>
            <Accordion type="single" collapsible className="w-full">
              {faqItems.map((item, index) => (
                <AccordionItem key={index} value={`item-${index}`}>
                  <AccordionTrigger className="text-base font-medium">
                    {item.q}
                  </AccordionTrigger>
                  <AccordionContent>
                    {item.a}
                  </AccordionContent>
                </AccordionItem>
              ))}
            </Accordion>
          </div>
        </div>
      </section>
      
      {/* CTA Section */}
      <section className="relative left-1/2 -translate-x-1/2 w-screen py-12 md:py-24 lg:py-32 bg-foreground">
        <div className="container px-4 md:px-6">
          <div className="flex flex-col items-center space-y-4 text-center">
            <div className="space-y-2">
              <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl text-primary-foreground">Ready to Forge a Stronger Team?</h2>
              <p className="mx-auto max-w-[700px] text-secondary md:text-xl/relaxed lg:text-base/relaxed xl:text-xl/relaxed">
                Join the growing community of teams using Hephaestus to build sustainable, collaborative, and effective software development practices.
              </p>
            </div>
            <div className="w-full max-w-sm space-y-2">
              <Button 
                className="w-full bg-background text-foreground hover:bg-background/90" 
                onClick={signIn}
                disabled={isSigningIn}
              >
                Get Started Now
              </Button>
              <p className="text-xs text-secondary">No credit card required. Start for free today.</p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}