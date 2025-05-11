import React from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import {
  GitBranch,
  GitPullRequest,
  Users,
  Zap,
  Code,
  LineChart,
  Briefcase,
  BookOpen,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export interface HomeProps {
  username: string;
  onViewDashboard?: () => void;
}

interface FeatureCard {
  title: string;
  description: string;
  icon: React.ReactNode;
  className?: string;
}

export function Home({ username, onViewDashboard }: HomeProps) {
  const features: FeatureCard[] = [
    {
      title: 'Repository Analysis',
      description: 'Get detailed insights into your repository structure and activity.',
      icon: <GitBranch className="h-6 w-6" />,
      className: 'bg-blue-50 text-blue-700 dark:bg-blue-900 dark:text-blue-300',
    },
    {
      title: 'PR Tracking',
      description: 'Track and manage pull requests across all your repositories.',
      icon: <GitPullRequest className="h-6 w-6" />,
      className: 'bg-purple-50 text-purple-700 dark:bg-purple-900 dark:text-purple-300',
    },
    {
      title: 'Code Quality',
      description: 'Identify and fix code quality issues in your projects.',
      icon: <Code className="h-6 w-6" />,
      className: 'bg-amber-50 text-amber-700 dark:bg-amber-900 dark:text-amber-300',
    },
    {
      title: 'Team Collaboration',
      description: 'Improve team communication and collaboration on projects.',
      icon: <Users className="h-6 w-6" />,
      className: 'bg-green-50 text-green-700 dark:bg-green-900 dark:text-green-300',
    },
    {
      title: 'Performance Insights',
      description: 'Track your team\'s performance and identify bottlenecks.',
      icon: <LineChart className="h-6 w-6" />,
      className: 'bg-red-50 text-red-700 dark:bg-red-900 dark:text-red-300',
    },
    {
      title: 'Project Management',
      description: 'Manage your projects and tasks with easy-to-use tools.',
      icon: <Briefcase className="h-6 w-6" />,
      className: 'bg-indigo-50 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300',
    },
  ];

  return (
    <div className="container py-8 max-w-7xl mx-auto">
      <div className="flex flex-col space-y-12">
        {/* Hero Section */}
        <section className="py-12 space-y-6 text-center">
          <h1 className="text-4xl font-bold tracking-tighter sm:text-5xl md:text-6xl">
            Welcome back, <span className="text-primary">{username}</span>
          </h1>
          <p className="mx-auto max-w-[700px] text-muted-foreground md:text-xl">
            Stay on top of your development workflow and analyze your repositories with powerful tools.
          </p>
          <div className="mx-auto flex flex-col sm:flex-row gap-4 justify-center">
            <Button size="lg" onClick={onViewDashboard}>
              <Zap className="mr-2 h-4 w-4" />
              View Dashboard
            </Button>
            <Button size="lg" variant="outline">
              <BookOpen className="mr-2 h-4 w-4" />
              View Documentation
            </Button>
          </div>
        </section>

        {/* Feature Cards */}
        <section>
          <h2 className="text-3xl font-bold mb-6 text-center">Key Features</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {features.map((feature, index) => (
              <Card key={index} className="overflow-hidden">
                <CardHeader className="pb-4">
                  <div className={cn("p-2 w-fit rounded-md", feature.className)}>
                    {feature.icon}
                  </div>
                  <CardTitle className="mt-4">{feature.title}</CardTitle>
                </CardHeader>
                <CardContent>
                  <CardDescription className="text-base">
                    {feature.description}
                  </CardDescription>
                </CardContent>
                <CardFooter>
                  <Button variant="ghost" className="w-full justify-start">Learn More</Button>
                </CardFooter>
              </Card>
            ))}
          </div>
        </section>

        {/* Quick Links */}
        <section className="py-8">
          <Card>
            <CardHeader>
              <CardTitle>Quick Actions</CardTitle>
              <CardDescription>Get started with these common actions</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                <Button variant="outline" className="justify-start h-auto py-4">
                  <div className="flex flex-col items-start">
                    <span className="font-medium">Connect Repository</span>
                    <span className="text-xs text-muted-foreground mt-1">Link a new GitHub repository</span>
                  </div>
                </Button>
                <Button variant="outline" className="justify-start h-auto py-4">
                  <div className="flex flex-col items-start">
                    <span className="font-medium">Run Analysis</span>
                    <span className="text-xs text-muted-foreground mt-1">Analyze code health and quality</span>
                  </div>
                </Button>
                <Button variant="outline" className="justify-start h-auto py-4">
                  <div className="flex flex-col items-start">
                    <span className="font-medium">View Reports</span>
                    <span className="text-xs text-muted-foreground mt-1">Access previous analysis reports</span>
                  </div>
                </Button>
              </div>
            </CardContent>
          </Card>
        </section>
      </div>
    </div>
  );
}