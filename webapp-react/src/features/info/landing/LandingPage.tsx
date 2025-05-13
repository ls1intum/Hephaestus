import { useRef } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { 
  BotMessageSquare, 
  Hammer, 
  Trophy, 
  Users 
} from 'lucide-react';

import { LeaderboardTable } from '@/features/leaderboard/LeaderboardTable';
import type { LeaderboardEntry } from '@/api/types.gen';

import agileHephaestus from "@/assets/agile_hephaestus.png";
import aliceAvatar from "@/assets/alice_developer.jpg";
import bobAvatar from "@/assets/bob_builder.jpg";
import charlieAvatar from "@/assets/charlie_coder.jpg";
import { useNavigate } from '@tanstack/react-router';

interface LandingPageProps {
  onSignIn: () => void;
  isSignedIn?: boolean;
}

// Sample data for the leaderboard preview
const sampleLeaderboardEntries: LeaderboardEntry[] = [
  {
    rank: 1,
    score: 520,
    user: {
      id: 0,
      leaguePoints: 2000,
      login: 'codeMaster',
      avatarUrl: aliceAvatar,
      name: 'Alice Developer',
      htmlUrl: 'https://example.com/alice'
    },
    numberOfReviewedPRs: 15,
    numberOfApprovals: 8,
    numberOfChangeRequests: 3,
    numberOfComments: 4,
    numberOfCodeComments: 6,
    numberOfUnknowns: 0,
    reviewedPullRequests: []
  },
  {
    rank: 2,
    score: 431,
    user: {
      id: 1,
      leaguePoints: 1000,
      login: 'devWizard',
      avatarUrl: bobAvatar,
      name: 'Bob Builder',
      htmlUrl: 'https://example.com/bob'
    },
    numberOfReviewedPRs: 12,
    numberOfApprovals: 5,
    numberOfChangeRequests: 2,
    numberOfComments: 5,
    numberOfCodeComments: 3,
    numberOfUnknowns: 0,
    reviewedPullRequests: []
  },
  {
    rank: 3,
    score: 302,
    user: {
      id: 2,
      leaguePoints: 1500,
      login: 'codeNinja',
      avatarUrl: charlieAvatar,
      name: 'Charlie Coder',
      htmlUrl: 'https://example.com/charlie'
    },
    numberOfReviewedPRs: 9,
    numberOfApprovals: 4,
    numberOfChangeRequests: 1,
    numberOfComments: 4,
    numberOfCodeComments: 2,
    numberOfUnknowns: 0,
    reviewedPullRequests: []
  }
];

// FAQ items
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

export function LandingPage({ onSignIn, isSignedIn = false }: LandingPageProps) {
  const learnMoreRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  // Determine the action for CTA buttons based on sign-in status
  const handleCTAClick = () => {
    if (isSignedIn) {
      navigate({ to: '/' });
    } else {
      // Trigger sign-in process
      onSignIn();
    }
  };

  return (
    <div className="flex flex-col min-h-screen -mx-8 -mb-8">
      <section className="w-full pt-12 md:pt-24 lg:pt-32 xl:pt-48 text-foreground">
        <div className="container px-4 md:px-6">
          <div className="flex flex-col items-center space-y-4 text-center">
            <div className="space-y-2">
              <h1 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl lg:text-6xl/none">
                Forge Healthy Software Teams with Hephaestus
              </h1>
              <p className="mx-auto max-w-[700px] text-secondary-foreground md:text-xl">
                Empower your novice engineers, foster collaboration, and build sustainable development practices with our AI-powered platform.
              </p>
            </div>
            <div className="space-x-4">
              <Button onClick={handleCTAClick}>
                {isSignedIn ? 'Go to Dashboard' : 'Get Started'}
              </Button>
              <Button variant="outline" onClick={() => learnMoreRef.current && learnMoreRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' })}>
                Learn More
              </Button>
            </div>
          </div>
        </div>
        <div className="mx-4 md:mx-10">
          <div className="shadow-2xl mt-24 max-w-4xl mx-auto">
            <div className="border border-b-0 rounded-bl-none rounded-br-none rounded-md border-input overflow-auto pointer-events-none" 
                 style={{ maskImage: "linear-gradient(to bottom, rgba(0, 0, 0, 1) 60%, rgba(0, 0, 0, 0))" }}>
              <LeaderboardTable 
                leaderboard={sampleLeaderboardEntries}
                isLoading={false}
              />
            </div>
          </div>
        </div>
      </section>
      
      <section ref={learnMoreRef} id="features" className="relative left-1/2 -translate-x-1/2 w-screen py-12 md:py-24 lg:py-32 bg-muted -mt-2">
        <div className="container px-4 md:px-6">
          <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl text-center mb-12">
            Forge Excellence with Our Features
          </h2>
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
                <BotMessageSquare className="text-3xl mb-2 text-cyan-500" />
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
      
      <section className="w-full py-12 md:py-24 lg:py-32">
        <div className="container px-4 md:px-6">
          <div className="grid gap-6 lg:grid-cols-[1fr_400px] lg:gap-12 xl:grid-cols-[1fr_600px]">
            <img src={agileHephaestus} alt="Agile Hephaestus" width="600" height="400" 
                 className="mx-auto aspect-square sm:max-w-xs md:sm:max-w-md lg:order-last" />
            <div className="flex flex-col justify-center space-y-4">
              <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl">Why Choose Hephaestus?</h2>
              <p className="max-w-[600px] text-muted-foreground md:text-xl/relaxed lg:text-base/relaxed xl:text-xl/relaxed">
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
      
      <section id="testimonials" className="relative left-1/2 -translate-x-1/2 w-screen py-12 md:py-24 lg:py-32 bg-muted">
        <div className="container px-4 md:px-6 flex flex-col items-center">
          <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl text-center mb-12">What Teams Are Saying</h2>
          <div className="lg:w-1/2">
            <Card>
              <CardHeader>
                <CardTitle>Instant Team Insights</CardTitle>
                <CardDescription>Ege Kocabas at <a className="text-muted-foreground" href="https://github.com/ls1intum/helios">Helios</a></CardDescription>
              </CardHeader>
              <CardContent>
                "I heavily use Hepheastus just to see how our team is doing. It also helps me to write all the things I have made for a week when its time to fill a weekly report."
              </CardContent>
            </Card>
          </div>
        </div>
      </section>
      
      <section id="faq" className="w-full py-12 md:py-24 lg:py-32">
        <div className="container px-4 md:px-6">
          <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl text-center mb-12">Frequently Asked Questions</h2>
          <div>
            <Accordion type="single" collapsible className="w-full">
              {faqItems.map((item, index) => (
                <AccordionItem key={index} value={`item-${index}`}>
                  <AccordionTrigger>{item.q}</AccordionTrigger>
                  <AccordionContent>{item.a}</AccordionContent>
                </AccordionItem>
              ))}
            </Accordion>
          </div>
        </div>
      </section>
      
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
              <Button className="w-full bg-background text-foreground hover:bg-background/90" onClick={handleCTAClick}>
                {isSignedIn ? 'Go to Dashboard' : 'Get Started Now'}
              </Button>
              {!isSignedIn && (
                <p className="text-xs text-secondary">No credit card required. Start for free today.</p>
              )}
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}