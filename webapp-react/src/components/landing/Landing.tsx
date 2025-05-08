import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Check, ArrowRight, Github, GitMerge, Activity, Shield } from 'lucide-react';

export interface LandingProps {
  onLogin?: () => void;
  onSignup?: () => void;
}

export function Landing({ onLogin, onSignup }: LandingProps) {
  const features = [
    "Code Quality Analysis",
    "Pull Request Analytics",
    "Team Collaboration Metrics",
    "Repository Insights",
    "Automated Code Reviews",
    "Performance Tracking",
    "Custom Report Generation",
    "Integration with GitHub, GitLab, and Bitbucket"
  ];

  const testimonials = [
    {
      quote: "Hephaestus has transformed our code review process and significantly improved our code quality across all repositories.",
      author: "Jane Smith",
      role: "CTO at TechCorp"
    },
    {
      quote: "We've seen a 40% decrease in bugs after implementing Hephaestus in our development workflow.",
      author: "Michael Johnson",
      role: "Engineering Lead at DevStudio"
    },
    {
      quote: "The insights from Hephaestus have helped our team identify and address code quality issues we weren't even aware of.",
      author: "Alex Wong",
      role: "Senior Developer at CodeWorks"
    }
  ];

  return (
    <div className="flex flex-col min-h-screen">
      {/* Hero Section */}
      <section className="py-20 md:py-32 bg-gradient-to-b from-background to-muted">
        <div className="container mx-auto px-4 text-center">
          <h1 className="text-4xl md:text-6xl font-bold tracking-tight mb-6">
            Transform Your Development Workflow
          </h1>
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto mb-10">
            Hephaestus analyzes your code and provides insights to help your team write better code, 
            faster. Track quality metrics and improve collaboration across repositories.
          </p>
          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <Button size="lg" onClick={onSignup}>
              Get Started
              <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
            <Button size="lg" variant="outline" onClick={onLogin}>
              Log In
            </Button>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="py-20 bg-background">
        <div className="container mx-auto px-4">
          <h2 className="text-3xl font-bold text-center mb-16">
            Powerful Features for Development Teams
          </h2>
          
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8 mb-16">
            <Card className="p-6 flex flex-col items-center text-center">
              <div className="h-12 w-12 rounded-full bg-blue-100 flex items-center justify-center mb-4">
                <Github className="h-6 w-6 text-blue-700" />
              </div>
              <h3 className="text-xl font-bold mb-2">Repository Integration</h3>
              <p className="text-muted-foreground">
                Connect your repositories from GitHub, GitLab, or Bitbucket for seamless integration.
              </p>
            </Card>
            
            <Card className="p-6 flex flex-col items-center text-center">
              <div className="h-12 w-12 rounded-full bg-green-100 flex items-center justify-center mb-4">
                <Activity className="h-6 w-6 text-green-700" />
              </div>
              <h3 className="text-xl font-bold mb-2">Code Analytics</h3>
              <p className="text-muted-foreground">
                Get detailed analytics about your code quality, performance, and maintainability.
              </p>
            </Card>
            
            <Card className="p-6 flex flex-col items-center text-center">
              <div className="h-12 w-12 rounded-full bg-amber-100 flex items-center justify-center mb-4">
                <GitMerge className="h-6 w-6 text-amber-700" />
              </div>
              <h3 className="text-xl font-bold mb-2">PR Management</h3>
              <p className="text-muted-foreground">
                Track and analyze pull requests to improve review processes and reduce bottlenecks.
              </p>
            </Card>
            
            <Card className="p-6 flex flex-col items-center text-center">
              <div className="h-12 w-12 rounded-full bg-purple-100 flex items-center justify-center mb-4">
                <Shield className="h-6 w-6 text-purple-700" />
              </div>
              <h3 className="text-xl font-bold mb-2">Quality Assurance</h3>
              <p className="text-muted-foreground">
                Automatically detect code smells, security issues, and bad practices in your codebase.
              </p>
            </Card>
          </div>
          
          <div className="flex flex-col md:flex-row gap-8 md:gap-16 items-center">
            <div className="md:w-1/2">
              <h3 className="text-2xl font-bold mb-4">Everything You Need to Improve Code Quality</h3>
              <ul className="space-y-3">
                {features.map((feature, index) => (
                  <li key={index} className="flex items-start">
                    <div className="mr-3 mt-1">
                      <Check className="h-5 w-5 text-green-500" />
                    </div>
                    <span>{feature}</span>
                  </li>
                ))}
              </ul>
              <Button className="mt-6" size="lg" onClick={onSignup}>
                Start Free Trial
              </Button>
            </div>
            
            <div className="md:w-1/2 mt-8 md:mt-0">
              <div className="rounded-lg overflow-hidden shadow-xl bg-card">
                {/* Placeholder for a dashboard screenshot or illustration */}
                <div className="aspect-video bg-muted flex items-center justify-center">
                  <p className="text-muted-foreground">Dashboard Preview</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
      
      {/* Testimonials Section */}
      <section className="py-20 bg-muted">
        <div className="container mx-auto px-4">
          <h2 className="text-3xl font-bold text-center mb-16">
            Trusted by Development Teams
          </h2>
          
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {testimonials.map((testimonial, index) => (
              <Card key={index} className="p-6">
                <CardContent className="pt-6">
                  <p className="text-lg italic mb-6">"{testimonial.quote}"</p>
                  <div>
                    <p className="font-bold">{testimonial.author}</p>
                    <p className="text-sm text-muted-foreground">{testimonial.role}</p>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </section>
      
      {/* CTA Section */}
      <section className="py-20 bg-primary text-primary-foreground">
        <div className="container mx-auto px-4 text-center">
          <h2 className="text-3xl font-bold mb-6">
            Ready to improve your development workflow?
          </h2>
          <p className="text-xl opacity-90 max-w-2xl mx-auto mb-8">
            Join thousands of developers who are using Hephaestus to write better code and ship faster.
          </p>
          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <Button 
              size="lg" 
              variant="secondary"
              onClick={onSignup}
            >
              Start Free Trial
            </Button>
            <Button 
              size="lg" 
              variant="outline" 
              className="border-primary-foreground hover:bg-primary-foreground hover:text-primary"
              onClick={onLogin}
            >
              Schedule a Demo
            </Button>
          </div>
        </div>
      </section>
      
      {/* Footer */}
      <footer className="py-12 bg-background border-t">
        <div className="container mx-auto px-4">
          <div className="flex flex-col md:flex-row justify-between">
            <div className="mb-8 md:mb-0">
              <h3 className="font-bold text-xl mb-4">Hephaestus</h3>
              <p className="text-muted-foreground max-w-sm">
                Empowering development teams with data-driven insights and code quality tools.
              </p>
            </div>
            
            <div className="grid grid-cols-2 md:grid-cols-3 gap-8">
              <div>
                <h4 className="font-medium mb-3">Product</h4>
                <ul className="space-y-2">
                  <li><Button variant="link" className="p-0 h-auto">Features</Button></li>
                  <li><Button variant="link" className="p-0 h-auto">Pricing</Button></li>
                  <li><Button variant="link" className="p-0 h-auto">Integrations</Button></li>
                  <li><Button variant="link" className="p-0 h-auto">Documentation</Button></li>
                </ul>
              </div>
              
              <div>
                <h4 className="font-medium mb-3">Company</h4>
                <ul className="space-y-2">
                  <li><Button variant="link" className="p-0 h-auto">About</Button></li>
                  <li><Button variant="link" className="p-0 h-auto">Blog</Button></li>
                  <li><Button variant="link" className="p-0 h-auto">Careers</Button></li>
                  <li><Button variant="link" className="p-0 h-auto">Contact</Button></li>
                </ul>
              </div>
              
              <div>
                <h4 className="font-medium mb-3">Legal</h4>
                <ul className="space-y-2">
                  <li><Button variant="link" className="p-0 h-auto">Privacy</Button></li>
                  <li><Button variant="link" className="p-0 h-auto">Terms</Button></li>
                  <li><Button variant="link" className="p-0 h-auto">Security</Button></li>
                </ul>
              </div>
            </div>
          </div>
          
          <div className="border-t mt-12 pt-8 flex flex-col md:flex-row justify-between items-center">
            <p className="text-sm text-muted-foreground">
              &copy; {new Date().getFullYear()} Hephaestus. All rights reserved.
            </p>
            
            <div className="flex space-x-4 mt-4 md:mt-0">
              <Button size="icon" variant="ghost">
                <Github className="h-5 w-5" />
                <span className="sr-only">GitHub</span>
              </Button>
              <Button size="icon" variant="ghost">
                <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M8.29 20.251c7.547 0 11.675-6.253 11.675-11.675 0-.178 0-.355-.012-.53A8.348 8.348 0 0022 5.92a8.19 8.19 0 01-2.357.646 4.118 4.118 0 001.804-2.27 8.224 8.224 0 01-2.605.996 4.107 4.107 0 00-6.993 3.743 11.65 11.65 0 01-8.457-4.287 4.106 4.106 0 001.27 5.477A4.072 4.072 0 012.8 9.713v.052a4.105 4.105 0 003.292 4.022 4.095 4.095 0 01-1.853.07 4.108 4.108 0 003.834 2.85A8.233 8.233 0 012 18.407a11.616 11.616 0 006.29 1.84" />
                </svg>
                <span className="sr-only">Twitter</span>
              </Button>
              <Button size="icon" variant="ghost">
                <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path fillRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clipRule="evenodd" />
                </svg>
                <span className="sr-only">GitHub Organization</span>
              </Button>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}