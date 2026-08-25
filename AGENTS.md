# Esca Agnellis Repository Instructions

## Repository role

Esca Agnellis is a local-first Android food-pyramid tracking app. Keep repository governance contributor-facing and suitable for open-source publication.

- Repository: `Kamui2040/Esca-Agnellis-Android`
- Package: `com.k2040.escaagnellis`
- Code and project build scripts: `GPL-3.0-only`
- Cleared documentation/artwork: `CC-BY-4.0` where explicitly identified
- Gradle wrapper components retain their upstream `Apache-2.0` licensing
- Branding and official-status rules are documented separately in `TRADEMARKS.md`

Build requirements belong in `docs/BUILDING.md`; the public release/signing model belongs in `docs/RELEASES.md`; user-visible release history belongs in `CHANGELOG.md`.

## Public-repository hygiene

- Treat every tracked file as public.
- Never commit credentials, signing material, personal data, raw device identifiers, machine-specific paths, private storage links/IDs, maintainer-only workflow, internal assistant/tool policy, private QA archives, or sensitive diagnostics.
- Public source must remain independently buildable, testable, auditable, translatable, and understandable without private infrastructure.
- Use synthetic or deliberately sanitized fixtures and examples.
- Preserve required licences, notices, attribution, and exact asset provenance.

## Product and privacy invariants

- Keep the app German-first, friendly, low-pressure, accessible, and usable on narrow screens.
- Preserve supported languages, themes, touch/scroll behavior, local PDF output, and reachable licence/use information.
- The core app must not require Internet permission.
- Do not add telemetry, analytics, tracking, advertising, accounts, login, mandatory cloud sync, automatic crash reporting, or hidden background collection.
- Optional companion behavior remains opt-in, disabled by default, local-only, and isolated from core tracking behavior.
- Resting and idling are free states; optional companion interactions must not create hidden monetization or external-service dependencies.

## Data, backup, and migrations

- Preserve every data/backup version still declared supported.
- Never silently rename, remove, reorder, reinterpret, or destructively rewrite stored fields.
- Restore/import must fully parse and validate before writing; failure leaves supported existing data unchanged.
- Migrations must be deterministic and all-or-nothing.
- Keep primary tracking state, UI-language state, companion state, and their backup boundaries separate.
- Primary and companion backup imports must reject one another when their formats are intentionally distinct.
- Never automatically uninstall the app or clear app data after signing, connectivity, downgrade, or package mismatch.

## Assets and provenance

- Use only project-owned, licensed, public-domain, or explicitly authorized assets.
- Keep `ASSET_PROVENANCE.yml` synchronized with tracked assets where required by the publication model.
- Do not publish pending-rights or restricted-reference assets.
- Review visual assets at actual in-app size for crop, transparency, spacing, readability, alignment, frame stability, and animation behavior as applicable.
- Preserve the approved Esca visual direction unless an intentional product change says otherwise.

## Build and validation

- Use JDK 17 and the Android SDK/Gradle versions declared by the repository.
- Use the repository Gradle wrapper rather than a globally installed Gradle.
- POSIX contributors use `./gradlew`; Windows contributors may use `gradlew.bat`.
- Repository verification tools used by the maintainer workflow must run with Python 3 on Linux and must not require PowerShell.
- Select validation according to affected scope and run `git diff --check`.
- Runtime-affecting work normally requires applicable unit tests, lint, debug assembly, and focused behavior checks.
- The standard `release` build must remain buildable without private signing material. F-Droid uses that normal release variant and applies its own signing; maintainer signing may be configured locally but must not be required for public source builds.
- Parse explicit Gradle task outcomes; do not infer an individual task PASS solely from overall build success.
- Documentation-only changes do not require an Android build when runtime behavior/configuration is unchanged.
- Distinguish source/static validation, build success, signing, installation, physical-device QA, reproducibility, and release evidence.

## Dependencies and open-source readiness

- Prefer maintained FLOSS dependencies and open formats.
- Do not introduce private dependencies, unexplained binaries, mandatory proprietary services, or cloud-only build requirements.
- Preserve source completeness, dependency provenance, notices, translations, privacy/security/support documentation, and F-Droid-compatible build requirements.
- Public builds must not depend on private storage, credentials, or maintainer-only tooling.

## Contributions and releases

- Keep `main` stable and prefer focused, reviewable changes.
- Use GitHub Issues as the durable tracker for rejected QA findings, reproducible bugs, deferred/follow-up fixes, unresolved implementation work, and other work that must survive beyond one pull request or conversation.
- Keep pull requests focused on their specific implementation and validation; link relevant issues instead of turning PR descriptions into a backlog.
- Preserve unrelated work and avoid broad formatting/generated-file churn.
- Do not commit APK/AAB files, build outputs, R8 mapping/retrace data, local backups, private test data, or signing material.
- Review complete changed-file scope, encoding, licences/provenance, and applicable tests before merge.
- Repository visibility changes, production signing, public releases, store/F-Droid submissions, Pages/deployments, announcements, and other official publication actions are maintainer-controlled.
