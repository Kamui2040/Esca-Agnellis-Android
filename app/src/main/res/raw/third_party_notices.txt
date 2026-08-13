# Esca Agnellis Third-Party Notices

Copyright (c) 2026 K2040.

## Status

This is the reviewed dependency and notice inventory for the Esca Agnellis
`v0.16.0` public-source candidate.

The final private future-public source candidate was built independently from a
fresh clone using documented public inputs only. Its Debug and F-Droid runtime
classpaths resolved with no third-party application runtime dependency, and the
built F-Droid APK passed the repository hardening verifier, including archive,
permission, unsigned-state, minification/resource-shrinking, and generated-output
checks.

Upstream licence texts and notices remain controlling. Inclusion here does not
imply sponsorship, endorsement, or official status.

## Packaged application runtime

The application declares no third-party runtime library dependency. Its packaged
application code uses Android platform APIs plus Esca Agnellis source and
resources.

The final clean candidate audit reverified the resolved Debug and F-Droid runtime
classpaths and found no external runtime dependency. No third-party application
runtime component is intentionally packaged by the project.

## Build and test tooling

The components below are build/test inputs. They are not packaged as Esca
Agnellis application runtime libraries.

### Gradle 8.9 and Gradle wrapper

- Role: build-only.
- Distribution: `gradle-8.9-bin.zip`.
- Pinned SHA-256:
  `d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab`.
- Licence: `Apache-2.0`.
- Repository notice: `LICENSES/Apache-2.0.txt`.

The tracked Gradle wrapper components retain their upstream terms. The Gradle
binary distribution itself is fetched by the builder and is not stored in this
repository.

### Android Gradle Plugin 8.7.3

- Role: build-only.
- Source family: Android Open Source Project `platform/tools/base`.
- Primary upstream source licence: `Apache-2.0`.

The plugin and its transitive build-tool implementation dependencies are fetched
as build inputs and are not copied into the source snapshot or packaged as Esca
Agnellis application runtime libraries. Their upstream terms and notices remain
controlling.

### JUnit 4.13.2

- Role: test-only; declared directly by `app/build.gradle`.
- Licence: `EPL-1.0`.
- Upstream licence reference: `https://junit.org/junit4/license.html`.
- Upstream dependency inventory:
  `https://junit.org/junit4/dependencies.html`.

### Hamcrest Core 1.3

- Role: test-only transitive dependency of `junit:junit:4.13.2`.
- Licence: New BSD / BSD 3-Clause.
- Upstream project/licence reference: `https://hamcrest.org/JavaHamcrest/`.

Hamcrest Core is not an application runtime dependency and is not packaged in
the release or F-Droid APK.

### Android SDK platform and build-tools 35

- Role: build-only platform/toolchain inputs.
- They are obtained by the builder and are not copied into the public source
  snapshot or packaged as application runtime libraries.
- Their upstream terms and notices remain controlling.

## Final-candidate reconciliation

For the current `v0.16.0` candidate:

1. the project-declared build/test dependencies are recorded above;
2. the final candidate independently resolved no third-party Debug/F-Droid
   application runtime dependency;
3. JUnit's consumed transitive test dependency, Hamcrest Core 1.3, is now
   explicitly recorded;
4. the source snapshot includes the project GPL, cleared documentation/artwork
   licensing model, Gradle-wrapper Apache notice, and exact tracked visual-asset
   provenance inventory;
5. the final F-Droid candidate hardening check found no undeclared packaged
   signing/private-build artifact or Internet permission; and
6. fetched build/test tool implementation dependencies remain governed by their
   upstream distributions and are not redistributed as application runtime
   components by this repository.

If the declared dependency graph, wrapper/toolchain, packaged runtime contents,
or licensing model changes, this inventory must be reviewed again before a
later publication.

This file is not a REUSE-compliance declaration and is not, by itself, proof of
whole-APK reproducibility.
