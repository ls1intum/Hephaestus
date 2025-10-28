#!/usr/bin/env python3
"""
GitHub webhook example extractor for the HephaestusTest organization.
Extracts webhook payload examples from NATS stream and saves them as JSON files.
"""

import argparse
import asyncio
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Optional, Set
import nats

# Default configuration
DEFAULT_NATS_SERVER = "nats://131.159.89.80:4222"
DEFAULT_EXAMPLES_DIR = Path("server/application-server/src/test/resources/github")
DEFAULT_NATS_SUBJECT = "github.HephaestusTest.>"
DEFAULT_NATS_STREAM = "github"


def get_example_filename(event_type: str, action: str = None) -> str:
    """
    Generate filename for webhook example based on event type and action.
    Format: <event_type>.<action>.json or <event_type>.json (if no action)
    """
    if action:
        return f"{event_type}.{action}.json"
    else:
        return f"{event_type}.json"


def get_existing_examples(examples_dir: Path) -> Set[str]:
    """Get set of existing example filenames"""
    existing = set()
    if examples_dir.exists():
        for file in examples_dir.glob("*.json"):
            existing.add(file.name)
    return existing


async def extract_webhook_examples(
    nats_server: str,
    examples_dir: Path,
    nats_subject: str,
    nats_stream: str,
    event_filters: Dict[str, Set[str]],
    since: Optional[datetime],
    until: Optional[datetime],
    allow_duplicates: bool
):
    """
    Main extraction function.
    Consumes all available messages from HephaestusTest and extracts unique examples.
    """
    print("🚀 Starting webhook example extraction...")
    print(f"📡 Connecting to NATS server: {nats_server}")
    print(f"📁 Output directory: {examples_dir}")
    print(f"🎯 Subject pattern: {nats_subject}")
    
    # Ensure output directory exists
    examples_dir.mkdir(parents=True, exist_ok=True)
    
    # Track existing examples to avoid duplicates
    existing_examples = get_existing_examples(examples_dir)
    extracted_examples = {}  # filename -> payload
    filename_counts: Dict[str, int] = {}
    processed_count = 0
    matched_count = 0
    
    try:
        # Connect to NATS
        nc = await nats.connect(nats_server)
        js = nc.jetstream()
    except Exception as e:
        print(f"❌ Failed to connect to NATS server: {e}")
        return False
    
    try:
        print(f"📊 Found {len(existing_examples)} existing examples")
        
        # First, let's check what messages are available in the stream
        try:
            stream_info = await js.stream_info(nats_stream)
            print(f"📊 Stream info: {stream_info.state.messages} total messages")
        except Exception as e:
            print(f"⚠️  Could not get stream info: {e}")

        # Create a pull subscription to consume all available messages
        # Use ephemeral consumer to start from beginning every time
        try:
            psub = await js.pull_subscribe(
                subject=nats_subject,
                stream=nats_stream
            )
            print(f"✅ Successfully created subscription for: {nats_subject}")
        except Exception as e:
            print(f"❌ Failed to create subscription: {e}")
            return False
        
        print("🔄 Consuming messages...")
        
        # Consume all available messages
        while True:
            try:
                # Fetch messages in batches
                msgs = await psub.fetch(50, timeout=5.0)
                
                if not msgs:
                    print("ℹ️  No more messages available")
                    break
                
                for msg in msgs:
                    try:
                        # Parse the webhook payload
                        payload = json.loads(msg.data.decode())
                        processed_count += 1
                        
                        # Extract event type from NATS subject
                        # Subject format: github.{org}.{repo}.{event_type}
                        subject_parts = msg.subject.split('.')
                        if len(subject_parts) >= 4:
                            event_type = subject_parts[3]
                            normalized_event_type = event_type.lower()
                        else:
                            print(f"   ⚠️  Unexpected subject format: {msg.subject}")
                            await msg.ack()
                            continue
                        
                        # Extract action from payload if present
                        action = payload.get("action")

                        # Skip events that do not match requested filters
                        if event_filters:
                            allowed_actions = event_filters.get(normalized_event_type)
                            if allowed_actions is None:
                                await msg.ack()
                                continue
                            normalized_action = (action or "").lower()
                            if allowed_actions and normalized_action not in allowed_actions:
                                await msg.ack()
                                continue
                            matched_count += 1
                        
                        metadata = getattr(msg, "metadata", None)
                        msg_timestamp = getattr(metadata, "timestamp", None)

                        if since and msg_timestamp and msg_timestamp < since:
                            await msg.ack()
                            continue
                        if until and msg_timestamp and msg_timestamp > until:
                            await msg.ack()
                            continue

                        # Generate filename for this webhook type
                        filename = get_example_filename(event_type, action)

                        if allow_duplicates:
                            base_name = filename[:-5]  # drop .json
                            count = filename_counts.get(base_name, 0)
                            candidate = filename

                            while (
                                candidate in existing_examples
                                or candidate in extracted_examples
                            ):
                                count += 1
                                candidate = f"{base_name}.{count}.json"

                            filename = candidate
                            filename_counts[base_name] = count
                        else:
                            if filename in existing_examples or filename in extracted_examples:
                                await msg.ack()
                                continue

                        # Store the example
                        extracted_examples[filename] = payload
                        print(f"   ✅ Found new example: {filename}")
                        
                        await msg.ack()
                        
                    except json.JSONDecodeError:
                        print(f"   ⚠️  Skipping invalid JSON message")
                        await msg.ack()
                        continue
                    except Exception as e:
                        print(f"   ⚠️  Error processing message: {e}")
                        await msg.ack()
                        continue
                
            except Exception as e:
                if "timeout" in str(e).lower():
                    print("ℹ️  No more messages available (timeout)")
                    break
                else:
                    print(f"⚠️  Error fetching messages: {e}")
                    break
        
        # Write all extracted examples to files
        for filename, payload in extracted_examples.items():
            filepath = examples_dir / filename
            try:
                with open(filepath, 'w') as f:
                    json.dump(payload, f, indent=2)
            except Exception as e:
                print(f"❌ Failed to write {filename}: {e}")
        
        print(f"\n🎉 Extraction complete!")
        print(f"📊 Processed: {processed_count} messages")
        if event_filters:
            print(f"📊 Matched filter: {matched_count} messages")
        print(f"📊 Extracted: {len(extracted_examples)} new examples")
        print(f"📁 Total examples: {len(existing_examples) + len(extracted_examples)}")
        
        if extracted_examples:
            print(f"\n📝 New examples created:")
            for filename in sorted(extracted_examples.keys()):
                print(f"   - {filename}")
        
        return True
        
    finally:
        try:
            await psub.unsubscribe()
        except:
            pass
        await nc.close()


def parse_event_filters(raw_filters) -> Dict[str, Set[str]]:
    """Parse --event arguments into an event -> actions mapping."""
    filters: Dict[str, Set[str]] = {}
    for raw in raw_filters or []:
        parts = raw.split(":", 1)
        event = parts[0].strip().lower()
        if not event:
            continue
        action_set = filters.setdefault(event, set())
        if len(parts) == 2:
            action = parts[1].strip().lower()
            if action:
                action_set.add(action)
        else:
            # Empty set indicates all actions for this event
            filters[event] = action_set
    return filters

def parse_iso_datetime(value: Optional[str]) -> Optional[datetime]:
    """Parse ISO8601 string into timezone-aware datetime."""
    if not value:
        return None
    cleaned = value.strip()
    if not cleaned:
        return None
    if cleaned.endswith("Z"):
        cleaned = cleaned[:-1] + "+00:00"
    dt = datetime.fromisoformat(cleaned)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def main():
    """Main entry point with argument parsing"""
    parser = argparse.ArgumentParser(
        description="Extract GitHub webhook examples from NATS stream"
    )
    parser.add_argument(
        "--nats-server",
        default=DEFAULT_NATS_SERVER,
        help=f"NATS server URL (default: {DEFAULT_NATS_SERVER})"
    )
    parser.add_argument(
        "--examples-dir",
        type=Path,
        default=DEFAULT_EXAMPLES_DIR,
        help=f"Output directory for examples (default: {DEFAULT_EXAMPLES_DIR})"
    )
    parser.add_argument(
        "--subject",
        default=DEFAULT_NATS_SUBJECT,
        help=f"NATS subject pattern (default: {DEFAULT_NATS_SUBJECT})"
    )
    parser.add_argument(
        "--stream",
        default=DEFAULT_NATS_STREAM,
        help=f"NATS stream name (default: {DEFAULT_NATS_STREAM})"
    )
    parser.add_argument(
        "--event",
        action="append",
        dest="events",
        help=(
            "Filter to specific event/action pairs. "
            "Format: event[:action]. Use multiple --event flags for more pairs."
        )
    )
    parser.add_argument(
        "--since",
        help=(
            "Only include messages published at or after this ISO8601 timestamp "
            "(e.g. 2025-10-28T22:00:00+01:00)"
        )
    )
    parser.add_argument(
        "--until",
        help=(
            "Only include messages published at or before this ISO8601 timestamp"
        )
    )
    parser.add_argument(
        "--allow-duplicates",
        action="store_true",
        help=(
            "Allow multiple payloads per event/action and append numeric suffixes "
            "to filenames when necessary"
        )
    )
    
    args = parser.parse_args()
    event_filters = parse_event_filters(args.events)
    since = parse_iso_datetime(args.since)
    until = parse_iso_datetime(args.until)
    
    try:
        success = asyncio.run(extract_webhook_examples(
            args.nats_server,
            args.examples_dir,
            args.subject,
            args.stream,
            event_filters,
            since,
            until,
            args.allow_duplicates
        ))
        
        if not success:
            sys.exit(1)
            
    except KeyboardInterrupt:
        print("\n👋 Extraction cancelled by user")
        sys.exit(0)
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()