# Stage 1: Build the React application
FROM node:22-alpine AS build

WORKDIR /app

# Copy package.json for dependency installation
COPY webapp/package.json /app/

# Install dependencies
# Note: Using npm install instead of npm ci because this is a workspace package
# and the package-lock.json is at the monorepo root, not in this folder
RUN npm install --ignore-scripts

# Copy the webapp application code
COPY webapp/ /app/

# Copy intelligence-service shared types (required for @intelligence-service/* imports)
COPY server/intelligence-service/src/mentor/ /server/intelligence-service/src/mentor/
COPY server/intelligence-service/src/shared/ /server/intelligence-service/src/shared/

# Build the React application using the production build script
# This runs copy-env-prod.js first to use the production environment config
RUN npm run build:prod

# Stage 2: Serve the app with Nginx
FROM nginx:stable-alpine

# Install necessary packages and explicitly update libxml2 for security fixes
RUN apk update && \
    apk upgrade && \
    apk add --no-cache bash grep sed perl libxml2>=2.13.4-r6 && \
    rm -rf /var/cache/apk/*

# Copy built React app from the build stage 
COPY --from=build /app/dist /usr/share/nginx/html

# Copy the substitution script into the image
COPY webapp/substitute_env_variables.sh /usr/local/bin/

# Make the substitution script executable
RUN chmod +x /usr/local/bin/substitute_env_variables.sh

# Copy custom Nginx configuration
COPY webapp/nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80
EXPOSE 80

# Set the substitution script to run before starting Nginx
ENTRYPOINT ["/bin/bash", "-c", "/usr/local/bin/substitute_env_variables.sh && exec nginx -g 'daemon off;'"]
