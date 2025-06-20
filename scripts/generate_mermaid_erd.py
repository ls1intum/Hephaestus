#!/usr/bin/env python3
"""
Mermaid ERD Generator for Hephaestus Database Schema

This script connects to a PostgreSQL database and generates a Mermaid ERD diagram
showing all tables, columns, data types, constraints, and relationships.

Usage:
    python generate_mermaid_erd.py <jdbc_url> <username> <password> <output_file>

Example:
    python generate_mermaid_erd.py jdbc:postgresql://localhost:5432/hephaestus root root docs/database/schema.mmd

Requirements:
    - psycopg[binary]
    Install with: pip install psycopg[binary]
"""

import sys
import os
import logging
from datetime import datetime
from typing import Dict, List, Tuple, Optional, Any
import re
from pathlib import Path

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Import database connection library
try:
    import psycopg
    logger.info("Using psycopg")
except ImportError:
    logger.error("PostgreSQL adapter not found. Please install:")
    logger.error("  pip install psycopg[binary]")
    sys.exit(1)


class DatabaseConnectionError(Exception):
    """Custom exception for database connection issues."""
    pass


class MermaidErdGenerator:
    """Generator for creating Mermaid ERD diagrams from PostgreSQL database schema."""
    
    def __init__(self, host: str, port: int, database: str, username: str, password: str):
        """Initialize the ERD generator with database connection parameters.
        
        Args:
            host: Database host
            port: Database port
            database: Database name
            username: Database username
            password: Database password
        """
        self.host = host
        self.port = port
        self.database = database
        self.username = username
        self.password = password
        self.connection = None

    def connect(self) -> None:
        """Connect to the PostgreSQL database.
        
        Raises:
            DatabaseConnectionError: If connection fails
        """
        try:
            connection_params = {
                'host': self.host,
                'port': self.port,
                'user': self.username,
                'password': self.password,
                'dbname': self.database
            }
            
            self.connection = psycopg.connect(**connection_params)
            logger.info(f"✅ Connected to database: {self.database}")
            
        except Exception as e:
            error_msg = f"Failed to connect to database {self.database}: {e}"
            logger.error(error_msg)
            raise DatabaseConnectionError(error_msg) from e

    def disconnect(self) -> None:
        """Close the database connection."""
        if self.connection:
            try:
                self.connection.close()
                logger.info("Database connection closed")
            except Exception as e:
                logger.warning(f"Error closing database connection: {e}")

    def __enter__(self):
        """Context manager entry."""
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit."""
        self.disconnect()

    def get_tables(self) -> List[str]:
        """Get all table names from the database, excluding Liquibase metadata tables.
        
        Returns:
            List of table names
            
        Raises:
            DatabaseConnectionError: If query fails
        """
        if not self.connection:
            raise DatabaseConnectionError("Not connected to database")
            
        try:
            with self.connection.cursor() as cursor:
                cursor.execute("""
                    SELECT table_name 
                    FROM information_schema.tables 
                    WHERE table_schema = 'public' 
                    AND table_type = 'BASE TABLE'
                    AND table_name NOT IN ('databasechangelog', 'databasechangeloglock')
                    ORDER BY table_name
                """)
                return [row[0] for row in cursor.fetchall()]
        except Exception as e:
            error_msg = f"Failed to fetch table names: {e}"
            logger.error(error_msg)
            raise DatabaseConnectionError(error_msg) from e

    def get_table_columns(self, table_name: str) -> List[Dict]:
        """Get column information for a specific table."""
        with self.connection.cursor() as cursor:
            cursor.execute("""
                SELECT 
                    c.column_name,
                    c.data_type,
                    c.character_maximum_length,
                    c.numeric_precision,
                    c.numeric_scale,
                    c.is_nullable,
                    c.column_default,
                    CASE 
                        WHEN pk.column_name IS NOT NULL THEN 'YES'
                        ELSE 'NO'
                    END as is_primary_key,
                    CASE 
                        WHEN fk.column_name IS NOT NULL THEN 'YES'
                        ELSE 'NO'
                    END as is_foreign_key,
                    CASE 
                        WHEN uk.column_name IS NOT NULL THEN 'YES'
                        ELSE 'NO'
                    END as is_unique_key
                FROM information_schema.columns c
                LEFT JOIN (
                    SELECT ku.column_name
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage ku
                        ON tc.constraint_name = ku.constraint_name
                    WHERE tc.table_name = %s
                    AND tc.constraint_type = 'PRIMARY KEY'
                ) pk ON c.column_name = pk.column_name
                LEFT JOIN (
                    SELECT ku.column_name
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage ku
                        ON tc.constraint_name = ku.constraint_name
                    WHERE tc.table_name = %s
                    AND tc.constraint_type = 'FOREIGN KEY'
                ) fk ON c.column_name = fk.column_name
                LEFT JOIN (
                    SELECT ku.column_name
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage ku
                        ON tc.constraint_name = ku.constraint_name
                    WHERE tc.table_name = %s
                    AND tc.constraint_type = 'UNIQUE'
                ) uk ON c.column_name = uk.column_name
                WHERE c.table_name = %s
                ORDER BY c.ordinal_position
            """, (table_name, table_name, table_name, table_name))
            
            columns = []
            for row in cursor.fetchall():
                column_name, data_type, char_max_len, num_precision, num_scale, is_nullable, column_default, is_pk, is_fk, is_uk = row
                
                # Format the data type
                formatted_type = self._format_data_type(data_type, char_max_len, num_precision, num_scale)
                
                # Build constraints list
                constraints = []
                if is_pk == 'YES':
                    constraints.append("PK")
                if is_fk == 'YES':
                    constraints.append("FK")
                if is_uk == 'YES':
                    constraints.append("UK")
                
                # Add comment for nullable/not null
                comment = ""
                if is_nullable == 'NO' and is_pk != 'YES':  # PK is implicitly NOT NULL
                    comment = "NOT NULL"
                
                columns.append({
                    'name': column_name,
                    'type': formatted_type,
                    'constraints': constraints,
                    'comment': comment,
                    'is_primary_key': is_pk == 'YES',
                    'is_foreign_key': is_fk == 'YES'
                })
            
            return columns

    def get_foreign_key_relationships(self) -> List[Dict]:
        """Get all foreign key relationships in the database, excluding Liquibase metadata tables."""
        with self.connection.cursor() as cursor:
            cursor.execute("""
                SELECT DISTINCT
                    tc.table_name as child_table,
                    kcu.column_name as child_column,
                    ccu.table_name as parent_table,
                    ccu.column_name as parent_column,
                    tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name
                JOIN information_schema.constraint_column_usage ccu
                    ON ccu.constraint_name = tc.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY'
                AND tc.table_schema = 'public'
                AND tc.table_name NOT IN ('databasechangelog', 'databasechangeloglock')
                AND ccu.table_name NOT IN ('databasechangelog', 'databasechangeloglock')
                ORDER BY tc.table_name, kcu.column_name
            """)
            
            relationships = []
            for row in cursor.fetchall():
                child_table, child_column, parent_table, parent_column, constraint_name = row
                
                # Generate a meaningful relationship label
                relationship_label = self._generate_relationship_label(child_table, parent_table, child_column)
                
                # Detect relationship cardinality
                cardinality = self._detect_relationship_cardinality(child_table, child_column, parent_table, parent_column)
                
                relationships.append({
                    'child_table': child_table,
                    'child_column': child_column,
                    'parent_table': parent_table,
                    'parent_column': parent_column,
                    'constraint_name': constraint_name,
                    'label': relationship_label,
                    'cardinality': cardinality
                })
            
            return relationships

    def _generate_relationship_label(self, child_table: str, parent_table: str, child_column: str) -> str:
        """Generate a meaningful relationship label."""
        # Remove common suffixes from column names
        clean_column = child_column.replace('_id', '').replace('id', '')
        
        # Common relationship patterns
        if 'assignee' in child_table:
            return 'assigned_to'
        elif 'comment' in child_table:
            return 'commented_on'
        elif 'review' in child_table:
            return 'reviewed'
        elif 'label' in child_table:
            return 'labeled'
        elif 'member' in child_table:
            return 'belongs_to'
        elif 'repository' in child_table and 'monitor' in child_table:
            return 'monitors'
        elif parent_table == 'user' and 'author' in child_column:
            return 'authored_by'
        elif parent_table == 'user' and 'creator' in child_column:
            return 'created_by'
        elif parent_table == 'user' and 'merged_by' in child_column:
            return 'merged_by'
        elif parent_table in child_column or clean_column in parent_table:
            return 'has'
        else:
            return 'references'

    def _detect_relationship_cardinality(self, child_table: str, child_column: str, 
                                       parent_table: str, parent_column: str) -> str:
        """Detect the cardinality of the relationship."""
        with self.connection.cursor() as cursor:
            # Check if child column is part of a composite primary key or has unique constraint
            cursor.execute("""
                SELECT COUNT(*) FROM (
                    SELECT ku.column_name
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage ku
                        ON tc.constraint_name = ku.constraint_name
                    WHERE tc.table_name = %s
                    AND ku.column_name = %s
                    AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
                ) AS unique_constraints
            """, (child_table, child_column))
            
            is_unique = cursor.fetchone()[0] > 0
            
            # Check if it's a junction/bridge table (many-to-many)
            cursor.execute("""
                SELECT COUNT(*) FROM information_schema.table_constraints tc
                WHERE tc.table_name = %s
                AND tc.constraint_type = 'FOREIGN KEY'
            """, (child_table,))
            
            fk_count = cursor.fetchone()[0]
            
            cursor.execute("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = %s
            """, (child_table,))
            
            total_columns = cursor.fetchone()[0]
            
            # Junction table: has 2+ foreign keys and few other columns
            if fk_count >= 2 and total_columns <= fk_count + 2:
                return "}o--o{"  # Many-to-many
            elif is_unique:
                return "||--||"  # One-to-one
            else:
                return "||--o{"  # One-to-many (default)

    def _format_data_type(self, data_type: str, char_max_len: Optional[int], 
                         num_precision: Optional[int], num_scale: Optional[int]) -> str:
        """Format PostgreSQL data type for display."""
        type_mapping = {
            'character varying': 'VARCHAR',
            'character': 'CHAR',
            'text': 'TEXT',
            'integer': 'INTEGER',
            'bigint': 'BIGINT',
            'smallint': 'SMALLINT',
            'boolean': 'BOOLEAN',
            'timestamp without time zone': 'TIMESTAMP',
            'timestamp with time zone': 'TIMESTAMPTZ',
            'date': 'DATE',
            'time': 'TIME',
            'numeric': 'NUMERIC',
            'decimal': 'DECIMAL',
            'real': 'REAL',
            'double precision': 'DOUBLE',
            'oid': 'OID',
            'uuid': 'UUID',
            'json': 'JSON',
            'jsonb': 'JSONB'
        }
        
        formatted_type = type_mapping.get(data_type, data_type.upper())
        
        # Add length/precision information
        if char_max_len and formatted_type in ['VARCHAR', 'CHAR']:
            formatted_type = f"{formatted_type}({char_max_len})"
        elif num_precision and formatted_type in ['NUMERIC', 'DECIMAL']:
            if num_scale:
                formatted_type = f"{formatted_type}({num_precision},{num_scale})"
            else:
                formatted_type = f"{formatted_type}({num_precision})"
        
        return formatted_type

    def generate_mermaid_erd(self, output_file: str) -> None:
        """Generate the complete Mermaid ERD and save to file.
        
        Args:
            output_file: Path to the output file
            
        Raises:
            DatabaseConnectionError: If database operations fail
        """
        logger.info("📊 Generating Mermaid ERD...")
        
        # Get all tables and their information
        tables = self.get_tables()
        table_data = {}
        
        for table in tables:
            columns = self.get_table_columns(table)
            table_data[table] = columns
        
        # Get relationships
        relationships = self.get_foreign_key_relationships()
        
        # Generate Mermaid ERD content
        content = self._build_mermaid_content(table_data, relationships)
        
        # Write to file
        try:
            output_path = Path(output_file)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            
            with open(output_path, 'w', encoding='utf-8') as f:
                f.write(content)
            logger.info(f"✅ Mermaid ERD generated successfully: {output_file}")
        except Exception as e:
            error_msg = f"Failed to write ERD to file {output_file}: {e}"
            logger.error(error_msg)
            raise DatabaseConnectionError(error_msg) from e

    def _build_mermaid_content(self, table_data: Dict, relationships: List[Dict]) -> str:
        """Build the complete Mermaid ERD content."""
        lines = []
        
        # Header with title
        lines.append("---")
        lines.append("config:")
        lines.append("    layout: elk")
        lines.append("---")
        lines.append("erDiagram")
        lines.append("    %% Generated automatically from PostgreSQL database schema")
        lines.append("    %% using scripts/generate_mermaid_erd.py")
        lines.append("    %% To regenerate: npm run db:erd:generate")
        lines.append("")
        
        # Add direction for better layout
        lines.append("    direction LR")
        lines.append("")
        
        # Tables with proper Mermaid syntax
        for table_name, columns in table_data.items():
            # Convert table name to singular form if needed (following ER modeling best practices)
            entity_name = self._to_entity_name(table_name)
            lines.append(f"    {entity_name} {{")
            
            for column in columns:
                # Build the attribute line: type name constraints "comment"
                attribute_parts = []
                
                # Start with type and name
                type_and_name = f"{column['type']} {column['name']}"
                
                # Add constraints directly after name (PK, FK, UK)
                if column["constraints"]:
                    constraint_str = ",".join(column["constraints"])
                    type_and_name += f" {constraint_str}"
                
                # Add comment if exists
                if column["comment"]:
                    type_and_name += f' "{column["comment"]}"'
                
                lines.append(f"        {type_and_name}")
            
            lines.append("    }")
            lines.append("")
        
        # Relationships with improved labels and cardinality
        lines.append("    %% Relationships")
        
        # Group relationships by type for better organization
        relationship_groups = {
            '||--||': [],  # One-to-One
            '||--o{': [],  # One-to-Many
            '}o--o{': []   # Many-to-Many
        }
        
        for rel in relationships:
            cardinality = rel.get('cardinality', '||--o{')
            relationship_groups[cardinality].append(rel)
        
        # Output relationships by type
        for cardinality, rels in relationship_groups.items():
            if rels:
                cardinality_name = {
                    '||--||': 'One-to-One',
                    '||--o{': 'One-to-Many', 
                    '}o--o{': 'Many-to-Many'
                }[cardinality]
                lines.append(f"    %% {cardinality_name} relationships")
                
                for rel in rels:
                    parent_entity = self._to_entity_name(rel["parent_table"])
                    child_entity = self._to_entity_name(rel["child_table"])
                    label = rel.get('label', 'has')
                    lines.append(f'    {parent_entity} {cardinality} {child_entity} : {label}')
                lines.append("")
        
        # Add styling for better visual hierarchy
        lines.append("    %% Styling")
        lines.append("    classDef primaryEntity fill:#e1f5fe,stroke:#01579b,stroke-width:2px")
        lines.append("    classDef associationEntity fill:#f3e5f5,stroke:#4a148c,stroke-width:2px")
        lines.append("    classDef metadataEntity fill:#e8f5e8,stroke:#1b5e20,stroke-width:2px")
        lines.append("")
        
        # Apply styles based on entity types
        for table_name in table_data.keys():
            entity_name = self._to_entity_name(table_name)
            style_class = self._get_entity_style_class(table_name)
            if style_class:
                lines.append(f"    class {entity_name} {style_class}")
        
        return "\n".join(lines)

    def _to_entity_name(self, table_name: str) -> str:
        """Convert table name to proper entity name (singular, PascalCase)."""
        # Handle special cases
        special_cases = {
            'issue_assignee': 'IssueAssignee',
            'issue_comment': 'IssueComment', 
            'issue_label': 'IssueLabel',
            'pull_request_review': 'PullRequestReview',
            'pull_request_review_comment': 'PullRequestReviewComment',
            'pull_request_requested_reviewers': 'PullRequestRequestedReviewer',
            'pullrequestbadpractice': 'PullRequestBadPractice',
            'bad_practice_detection': 'BadPracticeDetection',
            'bad_practice_feedback': 'BadPracticeFeedback',
            'repository_to_monitor': 'RepositoryToMonitor',
            'team_labels': 'TeamLabel',
            'team_members': 'TeamMember',
            'team_repositories': 'TeamRepository'
        }
        
        if table_name in special_cases:
            return special_cases[table_name]
        
        # Convert to singular and PascalCase
        # Simple singularization rules
        if table_name.endswith('ies'):
            singular = table_name[:-3] + 'y'
        elif table_name.endswith('s') and not table_name.endswith('ss'):
            singular = table_name[:-1]
        else:
            singular = table_name
        
        # Convert to PascalCase
        words = singular.split('_')
        return ''.join(word.capitalize() for word in words)

    def _get_entity_style_class(self, table_name: str) -> str:
        """Get the appropriate style class for an entity."""
        # Core business entities
        primary_entities = ['user', 'repository', 'issue', 'milestone', 'label', 'team', 'workspace']
        
        # Association/junction tables
        association_entities = [
            'issue_assignee', 'issue_label', 'team_members', 'team_labels', 
            'team_repositories', 'pull_request_requested_reviewers'
        ]
        
        # Metadata and tracking entities
        metadata_entities = ['session', 'message', 'repository_to_monitor']
        
        if table_name in primary_entities:
            return 'primaryEntity'
        elif table_name in association_entities:
            return 'associationEntity'
        elif table_name in metadata_entities:
            return 'metadataEntity'
        
        return None


def parse_jdbc_url(jdbc_url: str) -> Tuple[str, int, str]:
    """Parse JDBC URL to extract host, port, and database name."""
    # jdbc:postgresql://localhost:5432/hephaestus
    pattern = r'jdbc:postgresql://([^:]+):(\d+)/(.+)'
    match = re.match(pattern, jdbc_url)
    
    if not match:
        raise ValueError(f"Invalid JDBC URL format: {jdbc_url}")
    
    host = match.group(1)
    port = int(match.group(2))
    database = match.group(3)
    
    return host, port, database


def main():
    """Main function to run the ERD generator."""
    if len(sys.argv) != 5:
        logger.error("Usage: python generate_mermaid_erd.py <jdbc_url> <username> <password> <output_file>")
        logger.error("Example: python generate_mermaid_erd.py jdbc:postgresql://localhost:5432/hephaestus root root docs/database/schema.mmd")
        sys.exit(1)
    
    jdbc_url = sys.argv[1]
    username = sys.argv[2]
    password = sys.argv[3]
    output_file = sys.argv[4]
    
    try:
        # Parse JDBC URL
        host, port, database = parse_jdbc_url(jdbc_url)
        
        # Use context manager for proper resource management
        with MermaidErdGenerator(host, port, database, username, password) as generator:
            generator.generate_mermaid_erd(output_file)
            
    except DatabaseConnectionError as e:
        logger.error(f"Database error: {e}")
        sys.exit(1)
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
