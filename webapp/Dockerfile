# Stage 1: Build the Angular application
FROM node:22-alpine AS build

WORKDIR /app

# Copy and install dependencies early to leverage Docker layer caching
COPY package.json package-lock.json /app/

RUN npm install

# Copy the rest of the application code
COPY ./ /app/

# Ensure that environment.prod.ts contains placeholders (already present in codebase)

# Build the Angular application
RUN npm run build --prod

# Stage 2: Serve the app with Nginx
FROM nginx:stable-alpine

# Install necessary packages: bash, grep, perl
RUN apk update && apk add --no-cache bash grep perl

# Copy built Angular app from the build stage
COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

# Copy the substitution script into the image
COPY substitute_env_variables.sh /usr/local/bin/

# Make the substitution script executable
RUN chmod +x /usr/local/bin/substitute_env_variables.sh

# Copy custom Nginx configuration if necessary
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80
EXPOSE 80

# Set the substitution script to run before starting Nginx
ENTRYPOINT ["/bin/bash", "-c", "/usr/local/bin/substitute_env_variables.sh && exec nginx -g 'daemon off;'"]
