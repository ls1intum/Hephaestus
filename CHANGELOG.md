# Changelog

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
