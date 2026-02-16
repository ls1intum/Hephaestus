# Changelog

## [0.17.0](https://github.com/ls1intum/Hephaestus/compare/v0.16.0...v0.17.0) (2026-02-16)

### 🚀 Features

* **server:** add GitHub Projects V2 synchronization support ([#692](https://github.com/ls1intum/Hephaestus/issues/692)) ([fa602b3](https://github.com/ls1intum/Hephaestus/commit/fa602b387ecae0bdbd322c2f51ff785fc6a8077e))

## [0.16.0](https://github.com/ls1intum/Hephaestus/compare/v0.15.0...v0.16.0) (2026-02-15)

### 🚀 Features

* **workspace:** auto-elevate super admins with membership to workspace ADMIN ([#759](https://github.com/ls1intum/Hephaestus/issues/759)) ([ca82899](https://github.com/ls1intum/Hephaestus/commit/ca82899ac39e046fb3b918ac842035ea609d5e77))
* **config:** configurable host ports for local development ([#758](https://github.com/ls1intum/Hephaestus/issues/758)) ([19e7393](https://github.com/ls1intum/Hephaestus/commit/19e7393816fce4ca2fe5d997f8ff40123baf2ab1))

## [0.15.0](https://github.com/ls1intum/Hephaestus/compare/v0.14.0...v0.15.0) (2026-02-01)

### 🚀 Features

* **scripts:** add GitLab GraphQL schema and update script ([#685](https://github.com/ls1intum/Hephaestus/issues/685)) ([298f4c4](https://github.com/ls1intum/Hephaestus/commit/298f4c497f84b227aee3b7745ab8c08283dbabfe))
* **config:** add verified agent skills and workflow commands ([#654](https://github.com/ls1intum/Hephaestus/issues/654)) ([08ae20f](https://github.com/ls1intum/Hephaestus/commit/08ae20f4f042bbaf07a003c4c0d0e940e3a93963))
* **webapp:** migrate from Radix UI to Base UI ([#688](https://github.com/ls1intum/Hephaestus/issues/688)) ([8c38f31](https://github.com/ls1intum/Hephaestus/commit/8c38f3160ff69ddc82e4116d147df2a12b208eb8))

### 📚 Documentation

* **config:** use mvn instead of ./mvnw in agent-facing docs ([#691](https://github.com/ls1intum/Hephaestus/issues/691)) ([75ed97d](https://github.com/ls1intum/Hephaestus/commit/75ed97dcc27ace6c295fa105ee7ffa3aa12c9777))

## [0.14.0](https://github.com/ls1intum/Hephaestus/compare/v0.13.8...v0.14.0) (2026-01-31)

### 🚀 Features

* **profile:** xp profile ui and backend total user xp aggregation ([#660](https://github.com/ls1intum/Hephaestus/issues/660)) ([2ba8d98](https://github.com/ls1intum/Hephaestus/commit/2ba8d98649dcf5c428aafa5675b7a4490578de98))

## [0.13.8](https://github.com/ls1intum/Hephaestus/compare/v0.13.7...v0.13.8) (2026-01-31)

### 🐛 Bug Fixes

* **server:** accept hour-only schedule time format for backward compatibility ([#682](https://github.com/ls1intum/Hephaestus/issues/682)) ([64b62ea](https://github.com/ls1intum/Hephaestus/commit/64b62ea4427ce203ca48178d96a1a078a7663bff))

### ♻️ Code Refactoring

* **server:** consolidate @Value annotations into type-safe ConfigurationProperties ([#679](https://github.com/ls1intum/Hephaestus/issues/679)) ([cdcabda](https://github.com/ls1intum/Hephaestus/commit/cdcabda76dfb6cb67e9f256a18486d4fb77350d6))

## [0.13.7](https://github.com/ls1intum/Hephaestus/compare/v0.13.6...v0.13.7) (2026-01-31)

### 🐛 Bug Fixes

* **server:** use consistent APPLICATION_HOST_URL env var for CORS ([#677](https://github.com/ls1intum/Hephaestus/issues/677)) ([a90e4f0](https://github.com/ls1intum/Hephaestus/commit/a90e4f06e9e5e99af70c32d5de12f34daaa7deb3))

### ⚡ Performance Improvements

* add BuildKit cache mounts to all Dockerfiles ([#676](https://github.com/ls1intum/Hephaestus/issues/676)) ([6a74892](https://github.com/ls1intum/Hephaestus/commit/6a748929fa21b51bb1ecf260673dc8a8b96e1ecb))

## [0.13.6](https://github.com/ls1intum/Hephaestus/compare/v0.13.5...v0.13.6) (2026-01-31)

### 🐛 Bug Fixes

* **server:** permit OPTIONS requests for CORS preflight ([#673](https://github.com/ls1intum/Hephaestus/issues/673)) ([8057dfe](https://github.com/ls1intum/Hephaestus/commit/8057dfe6e63271caaa191a09c2f65ebad96ef276))

## [0.13.5](https://github.com/ls1intum/Hephaestus/compare/v0.13.4...v0.13.5) (2026-01-31)

### 🐛 Bug Fixes

* **server:** add cors config to production profile ([#670](https://github.com/ls1intum/Hephaestus/issues/670)) ([a163588](https://github.com/ls1intum/Hephaestus/commit/a1635887ebea61f8ccccc24c17988b1f285e8f1f))

## [0.13.4](https://github.com/ls1intum/Hephaestus/compare/v0.13.3...v0.13.4) (2026-01-31)

### 🐛 Bug Fixes

* **server:** use internal url for keycloak admin client ([#668](https://github.com/ls1intum/Hephaestus/issues/668)) ([35a4869](https://github.com/ls1intum/Hephaestus/commit/35a4869f15c4ff27d6b54356fcad01bcf2146d10))

## [0.13.3](https://github.com/ls1intum/Hephaestus/compare/v0.13.2...v0.13.3) (2026-01-31)

### 🐛 Bug Fixes

* **server:** improve database cleanup retry logic for flaky tests ([#666](https://github.com/ls1intum/Hephaestus/issues/666)) ([5ca8804](https://github.com/ls1intum/Hephaestus/commit/5ca880487d6c24a062354b585c663e2b0a0986f9))
* **config:** resolve prettier and biome formatter conflicts in ide ([#665](https://github.com/ls1intum/Hephaestus/issues/665)) ([ddfd9bd](https://github.com/ls1intum/Hephaestus/commit/ddfd9bd76251af320a9a9afaccef36e131018080))

## [0.13.2](https://github.com/ls1intum/Hephaestus/compare/v0.13.1...v0.13.2) (2026-01-30)

### 🐛 Bug Fixes

* allow deps, security, db, docker scopes to trigger releases ([#663](https://github.com/ls1intum/Hephaestus/issues/663)) ([485ccab](https://github.com/ls1intum/Hephaestus/commit/485ccab23e0d2605bbc085bc9b36269c722ea602))
* **security:** allow unauthenticated access to actuator health and info endpoints ([#662](https://github.com/ls1intum/Hephaestus/issues/662)) ([a35cf94](https://github.com/ls1intum/Hephaestus/commit/a35cf94ef6561fcfd1ae4e45a76abd5f6fd26d7e))

## [0.13.1](https://github.com/ls1intum/Hephaestus/compare/v0.13.0...v0.13.1) (2026-01-30)

### 🐛 Bug Fixes

* **gitprovider:** atomic upsert for activity events, issues, pull requests, labels, and milestones ([#659](https://github.com/ls1intum/Hephaestus/issues/659)) ([0f616a9](https://github.com/ls1intum/Hephaestus/commit/0f616a9fcf523bdb3e2465e17ead4d5cc90edea4))

## [0.13.0](https://github.com/ls1intum/Hephaestus/compare/v0.12.4...v0.13.0) (2026-01-28)

### 🐛 Bug Fixes

* **ci:** use TUM Docker mirror (standard ls1intum approach) ([f01f49b](https://github.com/ls1intum/Hephaestus/commit/f01f49bae669ac28b91a10cd9d5e01c85a80636e))

### 🚀 Features

* remove hub4j dependency and refactor GitHub sync infrastructure ([4bce1ee](https://github.com/ls1intum/Hephaestus/commit/4bce1ee4418c7b6d1c60dd31475e671030421a91))

## [0.12.4](https://github.com/ls1intum/Hephaestus/compare/v0.12.3...v0.12.4) (2025-12-31)

### 🐛 Bug Fixes

* **webapp:** hide resolve button for already-resolved bad practices ([#628](https://github.com/ls1intum/Hephaestus/issues/628)) ([e044486](https://github.com/ls1intum/Hephaestus/commit/e044486f5152eddd84093fce6957ffb98d87cf5d))
* **webapp:** preserve survey on navigation and fix clear show signal ([#607](https://github.com/ls1intum/Hephaestus/issues/607)) ([1a1c7b8](https://github.com/ls1intum/Hephaestus/commit/1a1c7b8bf20c4f446932c54b8e8f4fa68b9888f8))

## [0.12.3](https://github.com/ls1intum/Hephaestus/compare/v0.12.2...v0.12.3) (2025-12-28)

### 🐛 Bug Fixes

* **webhooks:** correct entrypoint path in dockerfile ([#624](https://github.com/ls1intum/Hephaestus/issues/624)) ([c458f3d](https://github.com/ls1intum/Hephaestus/commit/c458f3d615226c176cf5bf69fff76508cd5c3f0f))

## [0.12.2](https://github.com/ls1intum/Hephaestus/compare/v0.12.1...v0.12.2) (2025-12-27)

### 🐛 Bug Fixes

* **webapp:** content-hashed runtime config with nginx best practices ([#622](https://github.com/ls1intum/Hephaestus/issues/622)) ([7f402e5](https://github.com/ls1intum/Hephaestus/commit/7f402e56e131c9305773e80943d41072cf407183))

## [0.12.1](https://github.com/ls1intum/Hephaestus/compare/v0.12.0...v0.12.1) (2025-12-23)

### 🐛 Bug Fixes

* trigger patch release ([7d29081](https://github.com/ls1intum/Hephaestus/commit/7d2908120935bab94b11c03759609beba5fb8eed))

## [0.12.0](https://github.com/ls1intum/Hephaestus/compare/v0.11.1...v0.12.0) (2025-12-16)

### 🐛 Bug Fixes

* **webapp:** only show deployment time in preview environments ([#604](https://github.com/ls1intum/Hephaestus/issues/604)) ([67182c1](https://github.com/ls1intum/Hephaestus/commit/67182c18b012ea4eafdd0a420b41ff425b6c5b79))

### 🚀 Features

* **webapp:** add survey notification button with morph animation ([#603](https://github.com/ls1intum/Hephaestus/issues/603)) ([d6d993f](https://github.com/ls1intum/Hephaestus/commit/d6d993f64d74559f7b4dc0605fc176bd13f79ff9))

## [0.11.1](https://github.com/ls1intum/Hephaestus/compare/v0.11.0...v0.11.1) (2025-12-16)

### 🐛 Bug Fixes

* **ci:** fix copilot-environment.yml bd installation for CI environments ([#598](https://github.com/ls1intum/Hephaestus/issues/598)) ([8dd7cf8](https://github.com/ls1intum/Hephaestus/commit/8dd7cf8dfeb8699a82f144f7d015e4f07ce018d8))
* **docs:** invert docs navbar logo for dark mode visibility ([#597](https://github.com/ls1intum/Hephaestus/issues/597)) ([f205124](https://github.com/ls1intum/Hephaestus/commit/f20512421ba5dc88924ca98a49b5550f4298da87))
* replace unset WEB_ENV placeholders with empty strings to prevent production footer leak ([#600](https://github.com/ls1intum/Hephaestus/issues/600)) ([81fc4be](https://github.com/ls1intum/Hephaestus/commit/81fc4be46a329fb544da40ede930492144b19070))

## [0.11.0](https://github.com/ls1intum/Hephaestus/compare/v0.10.10...v0.11.0) (2025-12-15)

### 🚀 Features

* **docs:** add ai agent workflows and beads integration ([#589](https://github.com/ls1intum/Hephaestus/issues/589)) ([61d09ef](https://github.com/ls1intum/Hephaestus/commit/61d09efcbd5dc245d7b5458cd5e8d0d09527520b))

## [0.10.10](https://github.com/ls1intum/Hephaestus/compare/v0.10.9...v0.10.10) (2025-12-14)

### 🐛 Bug Fixes

* **webapp:** display a thank you screen upon survey completion ([#587](https://github.com/ls1intum/Hephaestus/issues/587)) ([e417711](https://github.com/ls1intum/Hephaestus/commit/e41771112560a61b7609ec6d2a3c4267b8591bf3))

## [0.10.9](https://github.com/ls1intum/Hephaestus/compare/v0.10.8...v0.10.9) (2025-12-14)

### 🐛 Bug Fixes

* **docs:** configure dynamic base url for surge.sh previews ([#585](https://github.com/ls1intum/Hephaestus/issues/585)) ([8767566](https://github.com/ls1intum/Hephaestus/commit/876756656d8b98c3f59b447206091217c9e60044))

## [0.10.8](https://github.com/ls1intum/Hephaestus/compare/v0.10.7...v0.10.8) (2025-12-14)

### 🐛 Bug Fixes

* **ci:** remove redundant production approval gate ([ccc4541](https://github.com/ls1intum/Hephaestus/commit/ccc45416759d39d3822ad9e20b1e0f8d8ca048fd))
* **ci:** strictly enforce commit scopes and refine release overrides ([41de9cb](https://github.com/ls1intum/Hephaestus/commit/41de9cbb0cebcb7097978652bd69437b6efdb99b))

## [0.10.7](https://github.com/ls1intum/Hephaestus/compare/v0.10.6...v0.10.7) (2025-12-14)

### 🐛 Bug Fixes

* release management and improve header and footer ([ec46a44](https://github.com/ls1intum/Hephaestus/commit/ec46a44ff226d0f562b732c8852c90ff7c1ceedc))

### 📚 Documentation

* **ci:** overhaul documentation system, strict validation & perf upgrade ([deb760f](https://github.com/ls1intum/Hephaestus/commit/deb760fcfbf962d06b611869dd66d8c09d8dbabc))
* **ci:** overhaul documentation system, strict validation & perf upgrade ([9abec47](https://github.com/ls1intum/Hephaestus/commit/9abec47f7710a12d45e117a5160cac5f753c65f8))

### 🛠 Build System

* **deps:** resolve mermaid theme peer dependency conflict ([02fb7b4](https://github.com/ls1intum/Hephaestus/commit/02fb7b46f011e63ddb2043a44e5660db77fb3718))

## [0.10.6](https://github.com/ls1intum/Hephaestus/compare/v0.10.5...v0.10.6) (2025-12-14)

### 🐛 Bug Fixes

* ignore self-assigned copilot reviews ([#576](https://github.com/ls1intum/Hephaestus/issues/576)) ([8d99fbd](https://github.com/ls1intum/Hephaestus/commit/8d99fbd9f5d250e18f35b0fe69caf3ba24d008bd))

### ♻️ Code Refactoring

* separate Keycloak relative paths ([#579](https://github.com/ls1intum/Hephaestus/issues/579)) ([4e63ce4](https://github.com/ls1intum/Hephaestus/commit/4e63ce4dd34080b9814fd32ac86183f3f1cedb31))

## [0.10.6-rc.2](https://github.com/ls1intum/Hephaestus/compare/v0.10.6-rc.1...v0.10.6-rc.2) (2025-12-09)

## [0.10.6-rc.1](https://github.com/ls1intum/Hephaestus/compare/v0.10.5...v0.10.6-rc.1) (2025-12-09)

## [0.10.5](https://github.com/ls1intum/Hephaestus/compare/v0.10.4...v0.10.5) (2025-12-09)

### 🐛 Bug Fixes

* reverse calculation and exclude correctly from league points recalculation ([b8a894a](https://github.com/ls1intum/Hephaestus/commit/b8a894a35aa6c71e4dc327bc36c587150a75ef6e))

## [0.10.5-rc.1](https://github.com/ls1intum/Hephaestus/compare/v0.10.4...v0.10.5-rc.1) (2025-12-09)

### 🐛 Bug Fixes

* reverse calculation and exclude correctly from league points recalculation ([b8a894a](https://github.com/ls1intum/Hephaestus/commit/b8a894a35aa6c71e4dc327bc36c587150a75ef6e))

## [0.10.4](https://github.com/ls1intum/Hephaestus/compare/v0.10.3...v0.10.4) (2025-12-09)

### 🐛 Bug Fixes

* league points calculation actually ([3246b9d](https://github.com/ls1intum/Hephaestus/commit/3246b9d81296032be3a7970bbac77b85ee68e5c3))

## [0.10.4-rc.1](https://github.com/ls1intum/Hephaestus/compare/v0.10.3...v0.10.4-rc.1) (2025-12-09)

### 🐛 Bug Fixes

* league points calculation actually ([3246b9d](https://github.com/ls1intum/Hephaestus/commit/3246b9d81296032be3a7970bbac77b85ee68e5c3))

## [0.10.3](https://github.com/ls1intum/Hephaestus/compare/v0.10.2...v0.10.3) (2025-12-09)

### 🐛 Bug Fixes

* improve league points recalculation ([eb3fe96](https://github.com/ls1intum/Hephaestus/commit/eb3fe96392149f98d2dfda10298c88baefb8c2e4))

## [0.10.3-rc.1](https://github.com/ls1intum/Hephaestus/compare/v0.10.2...v0.10.3-rc.1) (2025-12-09)

### 🐛 Bug Fixes

* improve league points recalculation ([eb3fe96](https://github.com/ls1intum/Hephaestus/commit/eb3fe96392149f98d2dfda10298c88baefb8c2e4))

## [0.10.2](https://github.com/ls1intum/Hephaestus/compare/v0.10.1...v0.10.2) (2025-12-08)

### 🐛 Bug Fixes

* prevent league points calculation to go beyond first contribution of user ([#568](https://github.com/ls1intum/Hephaestus/issues/568)) ([d2e99e7](https://github.com/ls1intum/Hephaestus/commit/d2e99e7e8b8bec2acef31d72da5087f4908387de))

## [0.10.2-rc.1](https://github.com/ls1intum/Hephaestus/compare/v0.10.1...v0.10.2-rc.1) (2025-12-08)

### 🐛 Bug Fixes

* prevent league points calculation to go beyond first contribution of user ([#568](https://github.com/ls1intum/Hephaestus/issues/568)) ([d2e99e7](https://github.com/ls1intum/Hephaestus/commit/d2e99e7e8b8bec2acef31d72da5087f4908387de))

## [0.10.1](https://github.com/ls1intum/Hephaestus/compare/v0.10.0...v0.10.1) (2025-12-08)

### 🐛 Bug Fixes

* automatic conversion from pat to github app workspace ([ceb6422](https://github.com/ls1intum/Hephaestus/commit/ceb6422f007fcfe18999f5198986e13c7cba1541))

## [0.10.1-rc.1](https://github.com/ls1intum/Hephaestus/compare/v0.10.0...v0.10.1-rc.1) (2025-12-08)

### 🐛 Bug Fixes

* automatic conversion from pat to github app workspace ([ceb6422](https://github.com/ls1intum/Hephaestus/commit/ceb6422f007fcfe18999f5198986e13c7cba1541))

## [0.10.0](https://github.com/ls1intum/Hephaestus/compare/v0.9.2...v0.10.0) (2025-12-08)

### 🐛 Bug Fixes

* fix app compose environment ([dc2be4b](https://github.com/ls1intum/Hephaestus/commit/dc2be4b014c8763fec4b075177c871e51dc42189))
* implement atomic workspace membership creation and ensure proper label relationship cleanup before deletion. ([#566](https://github.com/ls1intum/Hephaestus/issues/566)) ([34cd33e](https://github.com/ls1intum/Hephaestus/commit/34cd33e2828fe52a87b1e9d5f494eb2f5ab84524))
* **webapp:** invert colors on ReviewsPopover hover in dark mode ([#562](https://github.com/ls1intum/Hephaestus/issues/562)) ([4c9423b](https://github.com/ls1intum/Hephaestus/commit/4c9423b718da1b9547a0e1275151429913ca1c20))
* **webapp:** standardize loading spinners across codebase ([#561](https://github.com/ls1intum/Hephaestus/issues/561)) ([392c955](https://github.com/ls1intum/Hephaestus/commit/392c955b1f72e0e314c319fe4530b42b9c9cd302))
* **webapp:** standardize skeleton loading states to eliminate color inconsistencies ([#559](https://github.com/ls1intum/Hephaestus/issues/559)) ([2ac9491](https://github.com/ls1intum/Hephaestus/commit/2ac9491e1ee4be58fcd9e51d55e03e2c67751ca9))

### 🚀 Features

* add activity badges with filter on profile ([#564](https://github.com/ls1intum/Hephaestus/issues/564)) ([af2aa9f](https://github.com/ls1intum/Hephaestus/commit/af2aa9f1fbf25cc979b3ea0785e901b139650521))
* **application-server:** add contribution event entity ([#557](https://github.com/ls1intum/Hephaestus/issues/557)) ([f1d4de5](https://github.com/ls1intum/Hephaestus/commit/f1d4de5b839cbd0f6b7825c2aadafabc38d4dc87))
* add PostHog survey ([#515](https://github.com/ls1intum/Hephaestus/issues/515)) ([a9ee3b2](https://github.com/ls1intum/Hephaestus/commit/a9ee3b26ba9294b97237e0903dc4f5de49228462))
* add workspace switching and viewing with backfill and improved GitHub installation management ([#550](https://github.com/ls1intum/Hephaestus/issues/550)) ([75d4998](https://github.com/ls1intum/Hephaestus/commit/75d4998aebaad1267b4f5b215633771852104421))
* enforce research participation consent ([#524](https://github.com/ls1intum/Hephaestus/issues/524)) ([6531868](https://github.com/ls1intum/Hephaestus/commit/65318683a7a8182c13fe13f2cd32817925d4fcaa))
* **application-server:** env file support for GitHub-PAT authentication ([#547](https://github.com/ls1intum/Hephaestus/issues/547)) ([1f410bd](https://github.com/ls1intum/Hephaestus/commit/1f410bd0aa55a5d4e7f9c951fb0e4169e9bd7653))
* **application-server:** implement workspace and organization domain ([#401](https://github.com/ls1intum/Hephaestus/issues/401)) ([cb5b2d4](https://github.com/ls1intum/Hephaestus/commit/cb5b2d4636c1862a2da0c3be90f213a67e55ae2a))
* **application-server:** implement workspace entity extensions and CRUD API ([#408](https://github.com/ls1intum/Hephaestus/issues/408), [#416](https://github.com/ls1intum/Hephaestus/issues/416)) ([#541](https://github.com/ls1intum/Hephaestus/issues/541)) ([93d0c7d](https://github.com/ls1intum/Hephaestus/commit/93d0c7d99a85f63baa27d3815754d9a42fb8f684))
* skip Keycloak login page and directly login with Github ([#526](https://github.com/ls1intum/Hephaestus/issues/526)) ([cc83957](https://github.com/ls1intum/Hephaestus/commit/cc83957dfe89cfa55fce3271e617e1fe0c712657))
* support dockerless postgres workflow ([#517](https://github.com/ls1intum/Hephaestus/issues/517)) ([15acfe2](https://github.com/ls1intum/Hephaestus/commit/15acfe2626dbb28745ff5cb7884dfd270772e374))
* **webhook-ingest:** support GitLab webhook ingestion ([#402](https://github.com/ls1intum/Hephaestus/issues/402)) ([6d09a0f](https://github.com/ls1intum/Hephaestus/commit/6d09a0f5da3be1b760c73a9fcea73233711c088a))
* **application-server:** sync pull request review threads ([#522](https://github.com/ls1intum/Hephaestus/issues/522)) ([e5f00d0](https://github.com/ls1intum/Hephaestus/commit/e5f00d057ab74930b2bcd8c97fb870c66592dd97))
* **application-server:** workspace slug rename with redirect support ([#553](https://github.com/ls1intum/Hephaestus/issues/553)) ([1fd2cef](https://github.com/ls1intum/Hephaestus/commit/1fd2cefe88d315f48b356e46c26b7a95624f527f))

### 📚 Documentation

* add repository agent handbook ([#512](https://github.com/ls1intum/Hephaestus/issues/512)) ([7e21d34](https://github.com/ls1intum/Hephaestus/commit/7e21d3449ea1a0320d22ed3ef4061dc7481ae69d))

### ♻️ Code Refactoring

* **application-server:** improve PostgreSQL connection error diagnostics for tests ([#563](https://github.com/ls1intum/Hephaestus/issues/563)) ([b01aa32](https://github.com/ls1intum/Hephaestus/commit/b01aa32061a06a6a8c4541241d266ae2a7307a73))
* **application-server:** remove Kotlin Support / Conversion back to Java ([#509](https://github.com/ls1intum/Hephaestus/issues/509)) ([5cf784e](https://github.com/ls1intum/Hephaestus/commit/5cf784e24d7b00c5f7948e0b719bc2c4ec26afe0))

## [0.10.0-rc.53](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.52...v0.10.0-rc.53) (2025-12-08)

### 🐛 Bug Fixes

* implement atomic workspace membership creation and ensure proper label relationship cleanup before deletion. ([#566](https://github.com/ls1intum/Hephaestus/issues/566)) ([34cd33e](https://github.com/ls1intum/Hephaestus/commit/34cd33e2828fe52a87b1e9d5f494eb2f5ab84524))

## [0.10.0-rc.52](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.51...v0.10.0-rc.52) (2025-12-08)

### 🚀 Features

* add activity badges with filter on profile ([#564](https://github.com/ls1intum/Hephaestus/issues/564)) ([af2aa9f](https://github.com/ls1intum/Hephaestus/commit/af2aa9f1fbf25cc979b3ea0785e901b139650521))

## [0.10.0-rc.51](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.50...v0.10.0-rc.51) (2025-12-08)

## [0.10.0-rc.50](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.49...v0.10.0-rc.50) (2025-12-06)

### ♻️ Code Refactoring

* **application-server:** improve PostgreSQL connection error diagnostics for tests ([#563](https://github.com/ls1intum/Hephaestus/issues/563)) ([b01aa32](https://github.com/ls1intum/Hephaestus/commit/b01aa32061a06a6a8c4541241d266ae2a7307a73))

## [0.10.0-rc.49](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.48...v0.10.0-rc.49) (2025-12-06)

### 🚀 Features

* **application-server:** add contribution event entity ([#557](https://github.com/ls1intum/Hephaestus/issues/557)) ([f1d4de5](https://github.com/ls1intum/Hephaestus/commit/f1d4de5b839cbd0f6b7825c2aadafabc38d4dc87))

## [0.10.0-rc.48](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.47...v0.10.0-rc.48) (2025-12-06)

### 🚀 Features

* **webhook-ingest:** support GitLab webhook ingestion ([#402](https://github.com/ls1intum/Hephaestus/issues/402)) ([6d09a0f](https://github.com/ls1intum/Hephaestus/commit/6d09a0f5da3be1b760c73a9fcea73233711c088a))

## [0.10.0-rc.47](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.46...v0.10.0-rc.47) (2025-12-06)

### 🚀 Features

* **application-server:** workspace slug rename with redirect support ([#553](https://github.com/ls1intum/Hephaestus/issues/553)) ([1fd2cef](https://github.com/ls1intum/Hephaestus/commit/1fd2cefe88d315f48b356e46c26b7a95624f527f))

## [0.10.0-rc.46](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.45...v0.10.0-rc.46) (2025-12-06)

### 🐛 Bug Fixes

* **webapp:** standardize loading spinners across codebase ([#561](https://github.com/ls1intum/Hephaestus/issues/561)) ([392c955](https://github.com/ls1intum/Hephaestus/commit/392c955b1f72e0e314c319fe4530b42b9c9cd302))

## [0.10.0-rc.45](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.44...v0.10.0-rc.45) (2025-12-06)

### 🐛 Bug Fixes

* **webapp:** invert colors on ReviewsPopover hover in dark mode ([#562](https://github.com/ls1intum/Hephaestus/issues/562)) ([4c9423b](https://github.com/ls1intum/Hephaestus/commit/4c9423b718da1b9547a0e1275151429913ca1c20))

## [0.10.0-rc.44](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.43...v0.10.0-rc.44) (2025-12-06)

### 🐛 Bug Fixes

* **webapp:** standardize skeleton loading states to eliminate color inconsistencies ([#559](https://github.com/ls1intum/Hephaestus/issues/559)) ([2ac9491](https://github.com/ls1intum/Hephaestus/commit/2ac9491e1ee4be58fcd9e51d55e03e2c67751ca9))

## [0.10.0-rc.43](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.42...v0.10.0-rc.43) (2025-12-05)

## [0.10.0-rc.42](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.41...v0.10.0-rc.42) (2025-12-05)

## [0.10.0-rc.41](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.40...v0.10.0-rc.41) (2025-12-05)

## [0.10.0-rc.40](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.39...v0.10.0-rc.40) (2025-12-04)

## [0.10.0-rc.39](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.38...v0.10.0-rc.39) (2025-12-04)

## [0.10.0-rc.38](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.37...v0.10.0-rc.38) (2025-12-04)

## [0.10.0-rc.37](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.36...v0.10.0-rc.37) (2025-12-04)

## [0.10.0-rc.36](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.35...v0.10.0-rc.36) (2025-12-04)

## [0.10.0-rc.35](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.34...v0.10.0-rc.35) (2025-12-04)

## [0.10.0-rc.34](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.33...v0.10.0-rc.34) (2025-12-04)

## [0.10.0-rc.33](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.32...v0.10.0-rc.33) (2025-12-03)

## [0.10.0-rc.32](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.31...v0.10.0-rc.32) (2025-12-03)

## [0.10.0-rc.31](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.30...v0.10.0-rc.31) (2025-12-03)

## [0.10.0-rc.30](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.29...v0.10.0-rc.30) (2025-12-02)

## [0.10.0-rc.29](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.28...v0.10.0-rc.29) (2025-12-02)

## [0.10.0-rc.28](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.27...v0.10.0-rc.28) (2025-12-02)

## [0.10.0-rc.27](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.26...v0.10.0-rc.27) (2025-12-02)

## [0.10.0-rc.26](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.25...v0.10.0-rc.26) (2025-12-02)

## [0.10.0-rc.25](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.24...v0.10.0-rc.25) (2025-12-02)

### 🚀 Features

* add workspace switching and viewing with backfill and improved GitHub installation management ([#550](https://github.com/ls1intum/Hephaestus/issues/550)) ([75d4998](https://github.com/ls1intum/Hephaestus/commit/75d4998aebaad1267b4f5b215633771852104421))

## [0.10.0-rc.24](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.23...v0.10.0-rc.24) (2025-11-29)

### 🚀 Features

* **application-server:** env file support for GitHub-PAT authentication ([#547](https://github.com/ls1intum/Hephaestus/issues/547)) ([1f410bd](https://github.com/ls1intum/Hephaestus/commit/1f410bd0aa55a5d4e7f9c951fb0e4169e9bd7653))

## [0.10.0-rc.23](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.22...v0.10.0-rc.23) (2025-11-29)

## [0.10.0-rc.22](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.21...v0.10.0-rc.22) (2025-11-21)

### 🚀 Features

* **application-server:** implement workspace entity extensions and CRUD API ([#408](https://github.com/ls1intum/Hephaestus/issues/408), [#416](https://github.com/ls1intum/Hephaestus/issues/416)) ([#541](https://github.com/ls1intum/Hephaestus/issues/541)) ([93d0c7d](https://github.com/ls1intum/Hephaestus/commit/93d0c7d99a85f63baa27d3815754d9a42fb8f684))

## [0.10.0-rc.21](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.20...v0.10.0-rc.21) (2025-11-09)

## [0.10.0-rc.20](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.19...v0.10.0-rc.20) (2025-11-07)

## [0.10.0-rc.19](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.18...v0.10.0-rc.19) (2025-11-06)

## [0.10.0-rc.18](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.17...v0.10.0-rc.18) (2025-11-06)

## [0.10.0-rc.17](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.16...v0.10.0-rc.17) (2025-11-06)

## [0.10.0-rc.16](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.15...v0.10.0-rc.16) (2025-11-05)

## [0.10.0-rc.15](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.14...v0.10.0-rc.15) (2025-11-05)

## [0.10.0-rc.14](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.13...v0.10.0-rc.14) (2025-11-03)

## [0.10.0-rc.13](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.12...v0.10.0-rc.13) (2025-11-02)

## [0.10.0-rc.12](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.11...v0.10.0-rc.12) (2025-11-02)

## [0.10.0-rc.11](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.10...v0.10.0-rc.11) (2025-11-01)

### 🚀 Features

* skip Keycloak login page and directly login with Github ([#526](https://github.com/ls1intum/Hephaestus/issues/526)) ([cc83957](https://github.com/ls1intum/Hephaestus/commit/cc83957dfe89cfa55fce3271e617e1fe0c712657))

## [0.10.0-rc.10](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.9...v0.10.0-rc.10) (2025-10-31)

### 🚀 Features

* enforce research participation consent ([#524](https://github.com/ls1intum/Hephaestus/issues/524)) ([6531868](https://github.com/ls1intum/Hephaestus/commit/65318683a7a8182c13fe13f2cd32817925d4fcaa))

## [0.10.0-rc.9](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.8...v0.10.0-rc.9) (2025-10-30)

### 🚀 Features

* **application-server:** sync pull request review threads ([#522](https://github.com/ls1intum/Hephaestus/issues/522)) ([e5f00d0](https://github.com/ls1intum/Hephaestus/commit/e5f00d057ab74930b2bcd8c97fb870c66592dd97))

## [0.10.0-rc.8](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.7...v0.10.0-rc.8) (2025-10-29)

### 🚀 Features

* add PostHog survey ([#515](https://github.com/ls1intum/Hephaestus/issues/515)) ([a9ee3b2](https://github.com/ls1intum/Hephaestus/commit/a9ee3b26ba9294b97237e0903dc4f5de49228462))

## [0.10.0-rc.7](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.6...v0.10.0-rc.7) (2025-10-28)

## [0.10.0-rc.6](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.5...v0.10.0-rc.6) (2025-10-28)

### 🚀 Features

* support dockerless postgres workflow ([#517](https://github.com/ls1intum/Hephaestus/issues/517)) ([15acfe2](https://github.com/ls1intum/Hephaestus/commit/15acfe2626dbb28745ff5cb7884dfd270772e374))

## [0.10.0-rc.5](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.4...v0.10.0-rc.5) (2025-10-24)

## [0.10.0-rc.4](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.3...v0.10.0-rc.4) (2025-10-24)

## [0.10.0-rc.3](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.2...v0.10.0-rc.3) (2025-10-23)

### 📚 Documentation

* add repository agent handbook ([#512](https://github.com/ls1intum/Hephaestus/issues/512)) ([7e21d34](https://github.com/ls1intum/Hephaestus/commit/7e21d3449ea1a0320d22ed3ef4061dc7481ae69d))

## [0.10.0-rc.2](https://github.com/ls1intum/Hephaestus/compare/v0.10.0-rc.1...v0.10.0-rc.2) (2025-10-23)

### 🐛 Bug Fixes

* fix app compose environment ([dc2be4b](https://github.com/ls1intum/Hephaestus/commit/dc2be4b014c8763fec4b075177c871e51dc42189))

## [0.10.0-rc.1](https://github.com/ls1intum/Hephaestus/compare/v0.9.3-rc.1...v0.10.0-rc.1) (2025-10-23)

### 🚀 Features

* **application-server:** implement workspace and organization domain ([#401](https://github.com/ls1intum/Hephaestus/issues/401)) ([cb5b2d4](https://github.com/ls1intum/Hephaestus/commit/cb5b2d4636c1862a2da0c3be90f213a67e55ae2a))

## [0.9.3-rc.1](https://github.com/ls1intum/Hephaestus/compare/v0.9.2...v0.9.3-rc.1) (2025-10-23)

### ♻️ Code Refactoring

* **application-server:** remove Kotlin Support / Conversion back to Java ([#509](https://github.com/ls1intum/Hephaestus/issues/509)) ([5cf784e](https://github.com/ls1intum/Hephaestus/commit/5cf784e24d7b00c5f7948e0b719bc2c4ec26afe0))

## [0.9.2](https://github.com/ls1intum/Hephaestus/compare/v0.9.1...v0.9.2) (2025-10-22)

### 🐛 Bug Fixes

* GitHub user sync to refresh profile fields ([#505](https://github.com/ls1intum/Hephaestus/issues/505)) ([0ae6ceb](https://github.com/ls1intum/Hephaestus/commit/0ae6ceb75f494e45f985bff401b9dcf1e145549e))

## [0.9.2-rc.2](https://github.com/ls1intum/Hephaestus/compare/v0.9.2-rc.1...v0.9.2-rc.2) (2025-10-22)

## [0.9.2-rc.1](https://github.com/ls1intum/Hephaestus/compare/v0.9.1...v0.9.2-rc.1) (2025-10-22)

### 🐛 Bug Fixes

* GitHub user sync to refresh profile fields ([#505](https://github.com/ls1intum/Hephaestus/issues/505)) ([0ae6ceb](https://github.com/ls1intum/Hephaestus/commit/0ae6ceb75f494e45f985bff401b9dcf1e145549e))

## [0.9.1](https://github.com/ls1intum/Hephaestus/compare/v0.9.0...v0.9.1) (2025-10-12)

### 🐛 Bug Fixes

* correct team contribution attribution ([#500](https://github.com/ls1intum/Hephaestus/issues/500)) ([82b31c6](https://github.com/ls1intum/Hephaestus/commit/82b31c6437518c4000abeb96527cc35f37b45fc8))

## [0.9.1-rc.6](https://github.com/ls1intum/Hephaestus/compare/v0.9.1-rc.5...v0.9.1-rc.6) (2025-10-12)

## [0.9.1-rc.5](https://github.com/ls1intum/Hephaestus/compare/v0.9.1-rc.4...v0.9.1-rc.5) (2025-10-12)

## [0.9.1-rc.4](https://github.com/ls1intum/Hephaestus/compare/v0.9.1-rc.3...v0.9.1-rc.4) (2025-10-12)

## [0.9.1-rc.3](https://github.com/ls1intum/Hephaestus/compare/v0.9.1-rc.2...v0.9.1-rc.3) (2025-10-12)

## [0.9.1-rc.2](https://github.com/ls1intum/Hephaestus/compare/v0.9.1-rc.1...v0.9.1-rc.2) (2025-10-12)

## [0.9.1-rc.1](https://github.com/ls1intum/Hephaestus/compare/v0.9.0...v0.9.1-rc.1) (2025-10-12)

### 🐛 Bug Fixes

* correct team contribution attribution ([#500](https://github.com/ls1intum/Hephaestus/issues/500)) ([82b31c6](https://github.com/ls1intum/Hephaestus/commit/82b31c6437518c4000abeb96527cc35f37b45fc8))

## [0.9.0](https://github.com/ls1intum/Hephaestus/compare/v0.8.0...v0.9.0) (2025-10-12)

### 🐛 Bug Fixes

* about page mission wording ([b02f864](https://github.com/ls1intum/Hephaestus/commit/b02f8645400c9909bf80c07ebd468a7a1a7e8202))
* add resource limits and message retention policies to nats ([#373](https://github.com/ls1intum/Hephaestus/issues/373)) ([a10d295](https://github.com/ls1intum/Hephaestus/commit/a10d295bd82effc44c99e27fa8a3e60705615baa))
* openapi typescript client validation issues ([507f460](https://github.com/ls1intum/Hephaestus/commit/507f4601c8fd3f437d87287595e3d609c8afc1fd))
* rool level lockfile ([7f29437](https://github.com/ls1intum/Hephaestus/commit/7f29437e23b485b062b890ca721a53e0aebc5fa4))
* update deployment conditions to support tag-based deployments in CI/CD workflows ([170efb9](https://github.com/ls1intum/Hephaestus/commit/170efb93616117ca3f06221fba1668e24aac3362))
* update OpenAPI document version to 0.9.0-rc.3 across multiple files ([60b82aa](https://github.com/ls1intum/Hephaestus/commit/60b82aa8463838a4513146b6ab7e4e8ba5f304a4))
* update version script for webapp rename ([#391](https://github.com/ls1intum/Hephaestus/issues/391)) ([d96a052](https://github.com/ls1intum/Hephaestus/commit/d96a052b50d3fdf947a0b9088a61dd8183c7958a))
* version bump for top-level package.json ([e46490e](https://github.com/ls1intum/Hephaestus/commit/e46490ed86ad6c516d848377d5fcdb022843ec11))

### 🚀 Features

* **application-server:** add GitHub team synchronization ([#342](https://github.com/ls1intum/Hephaestus/issues/342)) ([42bfc6f](https://github.com/ls1intum/Hephaestus/commit/42bfc6f65acb9ca193fec0b89ca9d8ff52d53f85))
* add mentor framework v2 ([#364](https://github.com/ls1intum/Hephaestus/issues/364)) ([1a02674](https://github.com/ls1intum/Hephaestus/commit/1a02674938dac448293edefc4cd512c5c646656f))
* add new bug report template with improved structure and details ([c2cafc5](https://github.com/ls1intum/Hephaestus/commit/c2cafc546cb552f9cbb17e5123fbea4a6d557781))
* add team leaderboard ([#496](https://github.com/ls1intum/Hephaestus/issues/496)) ([c93da39](https://github.com/ls1intum/Hephaestus/commit/c93da39fcdcbdbe7d40521570f7259a985072cba))
* change teams to stay automatically up-to-date  ([#398](https://github.com/ls1intum/Hephaestus/issues/398)) ([1667069](https://github.com/ls1intum/Hephaestus/commit/1667069091d43df290f0eb40782ecee83e1387b6))
* handle installation and installation repositories events ([#396](https://github.com/ls1intum/Hephaestus/issues/396)) ([dfc1a1c](https://github.com/ls1intum/Hephaestus/commit/dfc1a1c01965d68ce7856b8a09599754d1b74ef9))
* handle organization events ([#397](https://github.com/ls1intum/Hephaestus/issues/397)) ([59f1f41](https://github.com/ls1intum/Hephaestus/commit/59f1f418d1b78d53712de6360f180cfc175e50bd))
* **application-server:** prevent team filtering mismatches  ([#400](https://github.com/ls1intum/Hephaestus/issues/400)) ([145666f](https://github.com/ls1intum/Hephaestus/commit/145666fe1ddf9337128af4e403dcf59e59aa3d81))

### 📚 Documentation

* add 0.9.0 announcement update ([#499](https://github.com/ls1intum/Hephaestus/issues/499)) ([8974066](https://github.com/ls1intum/Hephaestus/commit/8974066d0e52a22daa0cb9a62f8c6aeb947dd560))
* create docs for best practices ([#370](https://github.com/ls1intum/Hephaestus/issues/370)) ([cfcb580](https://github.com/ls1intum/Hephaestus/commit/cfcb5808c65e1ab7137760313737cc5954306f2d))
* improve database documentation and automatic ERD generation ([#354](https://github.com/ls1intum/Hephaestus/issues/354)) ([1eeede0](https://github.com/ls1intum/Hephaestus/commit/1eeede0b88a515b8648eaac4e7392503e7c353ad))
* migrate to docusaurus ([#498](https://github.com/ls1intum/Hephaestus/issues/498)) ([4e3d37d](https://github.com/ls1intum/Hephaestus/commit/4e3d37d8620573bb1671e0c09997a38503edf7b1))

### ♻️ Code Refactoring

* eliminate manual memoization across client ([#392](https://github.com/ls1intum/Hephaestus/issues/392)) ([6e8f01f](https://github.com/ls1intum/Hephaestus/commit/6e8f01f119c07a84c24137f7083844aa2e9261e3))
* refine mentor suggested actions ([#395](https://github.com/ls1intum/Hephaestus/issues/395)) ([28faee2](https://github.com/ls1intum/Hephaestus/commit/28faee28241df248deb4e39f463f4d79156b7136))
* simplify markdown rendering with streamdown ([#390](https://github.com/ls1intum/Hephaestus/issues/390)) ([42673d4](https://github.com/ls1intum/Hephaestus/commit/42673d4acba4f0eb099aeb9826d08f94ff3dd600))

## [0.9.0-rc.32](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.31...v0.9.0-rc.32) (2025-10-12)

### 📚 Documentation

* add 0.9.0 announcement update ([#499](https://github.com/ls1intum/Hephaestus/issues/499)) ([8974066](https://github.com/ls1intum/Hephaestus/commit/8974066d0e52a22daa0cb9a62f8c6aeb947dd560))

## [0.9.0-rc.31](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.30...v0.9.0-rc.31) (2025-10-12)

### 🚀 Features

* add team leaderboard ([#496](https://github.com/ls1intum/Hephaestus/issues/496)) ([c93da39](https://github.com/ls1intum/Hephaestus/commit/c93da39fcdcbdbe7d40521570f7259a985072cba))

## [0.9.0-rc.30](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.29...v0.9.0-rc.30) (2025-10-11)

### 🚀 Features

* **application-server:** prevent team filtering mismatches  ([#400](https://github.com/ls1intum/Hephaestus/issues/400)) ([145666f](https://github.com/ls1intum/Hephaestus/commit/145666fe1ddf9337128af4e403dcf59e59aa3d81))

## [0.9.0-rc.29](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.28...v0.9.0-rc.29) (2025-10-11)

### 📚 Documentation

* migrate to docusaurus ([#498](https://github.com/ls1intum/Hephaestus/issues/498)) ([4e3d37d](https://github.com/ls1intum/Hephaestus/commit/4e3d37d8620573bb1671e0c09997a38503edf7b1))

## [0.9.0-rc.28](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.27...v0.9.0-rc.28) (2025-10-02)

## [0.9.0-rc.27](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.26...v0.9.0-rc.27) (2025-09-13)

## [0.9.0-rc.26](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.25...v0.9.0-rc.26) (2025-09-13)

### 🚀 Features

* change teams to stay automatically up-to-date  ([#398](https://github.com/ls1intum/Hephaestus/issues/398)) ([1667069](https://github.com/ls1intum/Hephaestus/commit/1667069091d43df290f0eb40782ecee83e1387b6))

## [0.9.0-rc.25](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.24...v0.9.0-rc.25) (2025-09-13)

### 🚀 Features

* handle organization events ([#397](https://github.com/ls1intum/Hephaestus/issues/397)) ([59f1f41](https://github.com/ls1intum/Hephaestus/commit/59f1f418d1b78d53712de6360f180cfc175e50bd))

## [0.9.0-rc.24](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.23...v0.9.0-rc.24) (2025-09-13)

### 🚀 Features

* handle installation and installation repositories events ([#396](https://github.com/ls1intum/Hephaestus/issues/396)) ([dfc1a1c](https://github.com/ls1intum/Hephaestus/commit/dfc1a1c01965d68ce7856b8a09599754d1b74ef9))

## [0.9.0-rc.23](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.22...v0.9.0-rc.23) (2025-09-10)

### ♻️ Code Refactoring

* refine mentor suggested actions ([#395](https://github.com/ls1intum/Hephaestus/issues/395)) ([28faee2](https://github.com/ls1intum/Hephaestus/commit/28faee28241df248deb4e39f463f4d79156b7136))

## [0.9.0-rc.22](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.21...v0.9.0-rc.22) (2025-09-09)

### ♻️ Code Refactoring

* eliminate manual memoization across client ([#392](https://github.com/ls1intum/Hephaestus/issues/392)) ([6e8f01f](https://github.com/ls1intum/Hephaestus/commit/6e8f01f119c07a84c24137f7083844aa2e9261e3))

## [0.9.0-rc.21](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.20...v0.9.0-rc.21) (2025-09-09)

### 🐛 Bug Fixes

* update version script for webapp rename ([#391](https://github.com/ls1intum/Hephaestus/issues/391)) ([d96a052](https://github.com/ls1intum/Hephaestus/commit/d96a052b50d3fdf947a0b9088a61dd8183c7958a))

### ♻️ Code Refactoring

* simplify markdown rendering with streamdown ([#390](https://github.com/ls1intum/Hephaestus/issues/390)) ([42673d4](https://github.com/ls1intum/Hephaestus/commit/42673d4acba4f0eb099aeb9826d08f94ff3dd600))

## [0.9.0-rc.20](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.19...v0.9.0-rc.20) (2025-08-25)

## [0.9.0-rc.19](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.18...v0.9.0-rc.19) (2025-08-25)

## [0.9.0-rc.18](https://github.com/ls1intum/Hephaestus/compare/v0.9.0-rc.17...v0.9.0-rc.18) (2025-08-25)
