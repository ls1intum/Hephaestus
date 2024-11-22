import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const alertVariants = cva(
    "flex items-center gap-2 w-full rounded-lg border px-4 py-3 text-sm [&>svg]:text-foreground",
    {
        variants: {
            variant: {
                default: "bg-background text-foreground",
                error: "border-destructive/50 text-destructive dark:border-destructive [&>svg]:text-destructive",
                success:
                    "border-lime-500/50 text-lime-500 dark:border-lime-500 [&>svg]:text-lime-500",
                warning:
                    "border-yellow-500/50 text-yellow-500 dark:border-yellow-500 [&>svg]:text-yellow-500",
                info: "border-blue-500/50 text-blue-500 dark:border-blue-500 [&>svg]:text-blue-500"
            }
        },
        defaultVariants: {
            variant: "default"
        }
    }
);

const Alert = React.forwardRef<
    HTMLDivElement,
    React.HTMLAttributes<HTMLDivElement> & VariantProps<typeof alertVariants>
>(({ className, variant, ...props }, ref) => (
    <div
        ref={ref}
        role="alert"
        className={cn(alertVariants({ variant }), className)}
        {...props}
    />
));
Alert.displayName = "Alert";

const AlertTitle = React.forwardRef<
    HTMLParagraphElement,
    React.HTMLAttributes<HTMLHeadingElement>
>(({ className, ...props }, ref) => (
    <h5
        ref={ref}
        className={cn("mb-1 font-medium leading-none tracking-tight", className)}
        {...props}
    />
));
AlertTitle.displayName = "AlertTitle";

const AlertDescription = React.forwardRef<
    HTMLParagraphElement,
    React.HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => (
    <div
        ref={ref}
        className={cn("text-sm [&_p]:leading-relaxed", className)}
        {...props}
    />
));
AlertDescription.displayName = "AlertDescription";

export { Alert, AlertTitle, AlertDescription };
