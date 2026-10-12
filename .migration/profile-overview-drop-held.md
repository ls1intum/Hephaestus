#### 🔴 Update custom Practice profile overview clients

The Practice profile overview response no longer includes its top-level `holdingUp` or `reviewedWork` fields. Update custom clients that read these fields before upgrading. Remove use of the retired summary fields; do not substitute `trendSupport.reviewedWork` as though it were the same overview data.

The separate `trendSupport.reviewedWork`, `standingWork` and `reviewedWorkKind` values in standing and practice-group responses describe the work behind their standings. The bundled web app already uses the revised contract. Installations using only the bundled web app need no client change.
