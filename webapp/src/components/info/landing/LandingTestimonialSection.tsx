import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

export function LandingTestimonialSection() {
	return (
		<section id="testimonials" className="w-full py-8 md:py-16 bg-background">
			<div className="container px-4 md:px-6">
				<div className="mb-8 text-center max-w-3xl mx-auto">
					<Badge className="mb-4" variant="outline">
						Testimonial
					</Badge>
					<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl mb-4">
						From Our Community
					</h2>
				</div>

				<div className="max-w-3xl mx-auto">
					<Card>
						<CardContent className="pt-6">
							<div className="border-l-4 border-primary pl-4 py-2 italic mb-4">
								"I heavily use Hephaestus to see how our team is doing. It helps
								me write all the things I've accomplished when it's time to fill
								a weekly report."
							</div>
							<div className="flex items-center gap-3">
								<div className="font-medium">Ege Kocabas</div>
								<div className="text-sm text-muted-foreground">
									<Button
										variant="link"
										size="none"
										className="text-muted-foreground"
										asChild
									>
										<a href="https://github.com/ls1intum/helios">
											Helios Project, TU Munich
										</a>
									</Button>
								</div>
							</div>
						</CardContent>
					</Card>
				</div>
			</div>
		</section>
	);
}
