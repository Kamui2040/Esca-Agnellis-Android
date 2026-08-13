# Esca Agnellis — Linux Build Migration Note

Esca Agnellis has been validated on a POSIX/Linux development environment. This note records contributor-facing consequences of that migration and intentionally excludes private workstation paths, device identifiers, internal QA archives, and maintainer-only recovery procedures.

## Contributor impact

- The repository supports POSIX builds through `./gradlew`.
- JDK 17 is the required Java baseline for the current Gradle/Android configuration.
- Android SDK Platform 35 is required; current repository build configuration defines the authoritative Android tooling requirements.
- The Unix Gradle wrapper launcher must remain executable in Git.
- Build instructions should use platform-neutral paths and avoid assumptions about Windows drive letters or one maintainer's local layout.
- Windows remains a valid contributor environment where the repository and toolchain support it; use `gradlew.bat` there.

## Validation

Representative Linux validation for runtime-affecting work uses the applicable combination of:

```sh
./gradlew \
  :app:testDebugUnitTest \
  :app:testFdroidUnitTest \
  :app:lintDebug \
  :app:lintFdroid \
  :app:assembleDebug \
  :app:assembleFdroid \
  --no-daemon
```

Select only the tasks applicable to the change, and evaluate explicit task outcomes. Use `docs/BUILDING.md` for the current canonical build guidance.

## Repository hygiene

Do not commit local SDK paths, local JDK paths, generated build outputs, private QA records, device identifiers, or machine-specific migration notes. Contributor documentation must remain usable without private infrastructure.

## Historical status

The original workstation migration handoff served a one-time private recovery purpose and is no longer an active repository workflow. Maintainer-only migration/recovery evidence is kept outside Git.
