---
description: Format the achievements.yml file with the preferred property order
---

This workflow enforces the standard property order in `server/application-server/src/main/resources/achievements/achievements.yml` for better readability.

Preferred Order:
1. `id`
2. `parent`
3. `rarity`
4. `isHidden`
5. `category`
6. `triggerEvents`
7. `evaluatorClass`
8. `requirements`

### Steps:

// turbo
1. Run the achievement formatter script:
```bash
node scripts/format-achievements.mjs
```

2. Review the changes in `achievements.yml`.
