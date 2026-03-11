import fs from 'fs';
import path from 'path';

/**
 * Hephaestus Achievement Formatter
 *
 * This script enforces a consistent, human-readable property order for achievements.yml.
 * Preferred order: id, parent, rarity, isHidden, category, triggerEvents, evaluatorClass, requirements
 */

const ACHIEVEMENTS_FILE = path.join(process.cwd(), 'server/application-server/src/main/resources/achievements/achievements.yml');
const PREFERRED_ORDER = ['id', 'parent', 'rarity', 'isHidden', 'category', 'triggerEvents', 'evaluatorClass', 'requirements'];

function formatYaml() {
    if (!fs.existsSync(ACHIEVEMENTS_FILE)) {
        console.error(`Error: Could not find ${ACHIEVEMENTS_FILE}`);
        process.exit(1);
    }

    const content = fs.readFileSync(ACHIEVEMENTS_FILE, 'utf8');
    const lines = content.split('\n');
    const result = [];

    let currentAchievement = null;
    let inRequirements = false;

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const trimmed = line.trim();

        // Detect start of achievement
        if (trimmed.startsWith('- id:')) {
            if (currentAchievement) flushAchievement(currentAchievement, result);
            currentAchievement = { properties: {}, comments: [] };
            parseProperty(line, currentAchievement);
            continue;
        }

        // Detect properties inside achievement
        if (currentAchievement && line.startsWith('      ') && trimmed.includes(':') && !inRequirements) {
            if (trimmed.startsWith('requirements:')) {
                inRequirements = true;
                currentAchievement.properties['requirements'] = [line];
            } else {
                parseProperty(line, currentAchievement);
            }
            continue;
        }

        // Collect requirements content
        if (inRequirements) {
            if (line.startsWith('          ')) {
                currentAchievement.properties['requirements'].push(line);
                continue;
            } else {
                inRequirements = false;
            }
        }

        // Collect triggerEvents content (arrays)
        if (currentAchievement && currentAchievement.lastProperty === 'triggerEvents' && trimmed.startsWith('- ')) {
             currentAchievement.properties['triggerEvents'].push(line);
             continue;
        }

        // Collect comments or empty lines for the next achievement or header
        if (currentAchievement) {
            // If it's a comment right after or inside an achievement, we might want to keep it.
            // But simple approach: if we hit a non-achievement line, flush current one.
            if (trimmed === '' || trimmed.startsWith('#')) {
                // If we haven't started an achievement yet, or it's a major section break
                if (trimmed.startsWith('# ===') || trimmed.startsWith('# ---')) {
                    flushAchievement(currentAchievement, result);
                    currentAchievement = null;
                    result.push(line);
                } else {
                     // Minor comment, keep it for the next achievement?
                     // Or just treat it as a result line.
                     flushAchievement(currentAchievement, result);
                     currentAchievement = null;
                     result.push(line);
                }
            } else {
                 flushAchievement(currentAchievement, result);
                 currentAchievement = null;
                 result.push(line);
            }
        } else {
            result.push(line);
        }
    }

    if (currentAchievement) flushAchievement(currentAchievement, result);

    fs.writeFileSync(ACHIEVEMENTS_FILE, result.join('\n'));
    console.log('Successfully formatted achievements.yml');
}

function parseProperty(line, achievement) {
    const match = line.match(/^\s+([^:]+):/);
    if (match) {
        const key = match[1].trim();
        achievement.properties[key] = [line];
        achievement.lastProperty = key;
    }
}

function flushAchievement(achievement, result) {
    if (!achievement) return;

    // Write properties in preferred order
    PREFERRED_ORDER.forEach(key => {
        if (achievement.properties[key]) {
            result.push(...achievement.properties[key]);
        }
    });

    // Write any extra properties
    Object.keys(achievement.properties).forEach(key => {
        if (!PREFERRED_ORDER.includes(key)) {
            result.push(...achievement.properties[key]);
        }
    });
}

formatYaml();
