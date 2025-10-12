# Changelog

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
