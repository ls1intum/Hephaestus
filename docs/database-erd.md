# Database Entity Relationship Diagram (ERD)

## Overview

The Hephaestus project includes an automated ERD generation system that creates a Mermaid diagram from the PostgreSQL database schema. This diagram visualizes the database structure, including tables, columns, data types, constraints, and relationships.

## Generated ERD

The current database schema is visualized in the [database-schema.mmd](./database-schema.mmd) file, which can be viewed using any Mermaid-compatible viewer.

## ERD Generation Process

### How it Works

1. **Database Setup**: The application-server's PostgreSQL database is started via Docker Compose
2. **Schema Application**: Liquibase applies all database migrations to ensure the schema is up-to-date
3. **Metadata Extraction**: A Python script connects to the database and extracts table and relationship metadata
4. **Filtering**: Liquibase metadata tables (`databasechangelog` and `databasechangeloglock`) are automatically excluded
5. **Mermaid Generation**: The metadata is converted into Mermaid ERD syntax
6. **File Output**: The resulting diagram is saved to `docs/database-schema.mmd`

### Running ERD Generation

To generate a fresh ERD, run the following command from the project root:

```bash
npm run db:erd:generate
```

This command will:
- Start the PostgreSQL database using Docker Compose
- Wait for the database to be ready
- Apply all Liquibase migrations
- Install Python dependencies
- Extract database schema metadata
- Generate the Mermaid ERD file

### Prerequisites

- Docker and Docker Compose installed
- Python 3.x available in your system
- Maven for running Liquibase commands

### Dependencies

The ERD generation script requires the following Python packages:
- `psycopg[binary]==3.2.3` - PostgreSQL adapter for Python 3

These are automatically installed when running the npm script.

## Generated ERD Features

### Table Information
- **Table Names**: All business domain tables are included (Liquibase metadata tables are excluded)
- **Column Details**: Each column shows its data type, constraints (PK, FK, NOT NULL)
- **Relationships**: Foreign key relationships are visualized with appropriate cardinality
- **Excluded Tables**: `databasechangelog` and `databasechangeloglock` (Liquibase internal tables)

### Relationship Types
- `||--o{` : One-to-Many relationship
- `||--||` : One-to-One relationship  
- `}o--o{` : Many-to-Many relationship

### Database Schema Overview

The Hephaestus database includes several main entity groups:

#### Core Entities
- **Repository**: Git repositories being monitored
- **User**: GitHub users interacting with repositories
- **Issue/Pull Request**: GitHub issues and pull requests (using inheritance)

#### Analysis Entities
- **Bad Practice Detection**: Automated detection of code quality issues
- **Bad Practice Feedback**: AI-generated feedback and suggestions
- **Pull Request Bad Practice**: Links between pull requests and detected issues

#### Monitoring & Communication
- **Session/Message**: User interaction tracking
- **Team**: Team management and permissions
- **Workspace**: Organizational workspace management

#### Metadata
- **Label**: GitHub labels for issues and PRs
- **Milestone**: Project milestones

## File Structure

```
docs/
├── database-schema.mmd          # Generated Mermaid ERD
└── database-erd.md             # This documentation file

supporting_scripts/
├── generate_mermaid_erd.py     # ERD generation script
└── requirements.txt            # Python dependencies
```

## Customization

### Modifying the ERD Generator

The ERD generation script (`supporting_scripts/generate_mermaid_erd.py`) can be customized to:
- Include/exclude specific tables
- Modify the output format
- Add custom styling or annotations
- Change relationship detection logic

### Updating Dependencies

To update Python dependencies:
1. Edit `supporting_scripts/requirements.txt`
2. The next ERD generation will install the updated dependencies

## Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Ensure Docker is running
   - Check if PostgreSQL container is healthy: `docker compose ps`
   - Verify database credentials in the script

2. **Python Import Errors**
   - Check Python version compatibility
   - Ensure all dependencies are installed correctly
   - Try running the script manually: `python3 generate_mermaid_erd.py ...`

3. **Liquibase Migration Errors**
   - Ensure the application-server builds successfully
   - Check for any pending migration issues
   - Verify database schema integrity

### Manual ERD Generation

If the npm script fails, you can run the ERD generation manually:

```bash
cd server/application-server
docker compose up -d postgres
mvn liquibase:update
cd ../../supporting_scripts
pip install -r requirements.txt
python3 generate_mermaid_erd.py jdbc:postgresql://localhost:5432/hephaestus root root ../docs/database-schema.mmd
```

## Integration with Documentation

The generated ERD file can be:
- Viewed in GitHub (with Mermaid support)
- Included in documentation sites
- Converted to images for presentations
- Used in API documentation tools

## Maintenance

The ERD should be regenerated whenever:
- New database migrations are added
- Table structures are modified
- Relationships change
- Before major releases

Consider adding ERD generation to your CI/CD pipeline to ensure the diagram stays up-to-date automatically.

```yaml
# Example GitHub Actions step
- name: Generate Database ERD
  run: |
    npm run db:erd:generate
    git add docs/database-schema.mmd
    git diff --staged --quiet || git commit -m "docs: update database ERD"
```

## Customization

The ERD generation can be customized by modifying the SchemaCrawler configuration in `server/application-server/pom.xml`:

- **Schema filtering:** Change `<schemas>` to include/exclude specific schemas
- **Table filtering:** Modify `<grepExclude>` to filter out certain tables
- **Output format:** Currently set to Mermaid (`.mmd`), but can be changed to other formats
- **Detail level:** Adjust `<infolevel>` for more or less detail in the diagram

## Troubleshooting

- **Database connection issues:** Ensure PostgreSQL is running and accessible
- **Missing tables:** Run `mvn liquibase:update` to apply all migrations
- **Plugin errors:** Check that all Maven dependencies are properly resolved
- **Large schemas:** Consider adding more table filters to focus on core entities
