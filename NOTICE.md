# Esca Agnellis Notices

Copyright (c) 2026 K2040.

## Release status

Esca Agnellis `v0.16.0` is the first open-source release. The public source
snapshot is published in `Kamui2040/Esca-Agnellis-Android`; official
developer-signed APKs are distributed through
`Kamui2040/K2040-Android-Releases`.

This publication does not imply an F-Droid submission or approval of any
third-party store listing.

Releases through `v0.15.0` remain historical proprietary releases under the
terms published with those releases. Adding open-source licence texts to the
`v0.16.0` preparation tree does not retroactively relicense earlier releases.

## Licence map

- Original Esca Agnellis source code and project build scripts are licensed
  under `GPL-3.0-only` together with the K2040 attribution-preservation
  additional term permitted by GPLv3 section 7(b). The specified notice is
  `Copyright (c) 2026 K2040.` and must be preserved in K2040-authored material
  or in the Appropriate Legal Notices displayed by works containing it. See
  `LICENSE`, `LICENSES/GPL-3.0-only.txt`, and
  `LICENSES/GPL-3.0-Section-7b-K2040.txt`.
- Documentation and artwork are licensed under `CC-BY-4.0` only when their
  exact material or asset entry is explicitly identified as cleared for that
  licence. `CC-BY-4.0` independently requires attribution when applicable. See
  `LICENSES/CC-BY-4.0.txt` and `ASSET_PROVENANCE.yml`.
- The Gradle wrapper scripts and wrapper components retain their upstream
  `Apache-2.0` terms. See `LICENSES/Apache-2.0.txt`.
- The current build/test dependency and packaged-runtime inventory is summarized
  in `THIRD_PARTY_NOTICES.md`; upstream terms remain controlling and must be
  retained as required by those respective licences/notices.
- Names, logos, branding, and indications of official status are governed
  separately by `TRADEMARKS.md`. Trademark rules do not reduce rights granted
  by the applicable copyright licence.

The standard GNU GPLv3 licence text remains unmodified. The K2040 section 7(b)
term is a separate applicable additional term for material for which K2040 has
or can give appropriate copyright permission.

All 31 tracked visual assets are documented in `ASSET_PROVENANCE.yml`. They
were generated with OpenAI ChatGPT specifically for Esca Agnellis at K2040's
request; no third-party source artwork or supplied third-party visual input is
declared. K2040 offers each listed asset under `CC-BY-4.0` to the extent
copyright or related licensable rights subsist under applicable law, with
attribution `Esca Agnellis artwork by K2040`. Where no such right subsists,
K2040 asserts no copyright restriction over that material. Asset-rights
publication blockers are zero. AI-generated output may not be unique, and no
ownership of third-party trademarks is claimed. Visual QA and physical-device
QA remain separate release gates. This repository does not yet claim REUSE
compliance.

## Factual orientation and non-affiliation

Publicly accessible food-pyramid and hand-measure guidance from the German
Federal Centre for Nutrition (BZfE) is used only as factual orientation.

Esca Agnellis is not an official product of, affiliated with, endorsed by, or
sponsored by BZfE or the German Federal Office for Agriculture and Food (BLE).
No ownership is claimed over public facts, general recommendations,
measurement units, or the hand-measure concept.

## Health and nutrition

Esca Agnellis provides general tracking and orientation features. It is not
medical, dietary, or individualized nutritional advice, diagnosis, treatment,
or a substitute for advice from a qualified professional.

## Official builds and signatures

Developer APKs remain hosted through
`Kamui2040/K2040-Android-Releases` and are signed with K2040's permanent
developer certificate. F-Droid may build independently from the public source repository and sign
with its own signing identity.

Both variants use package `com.k2040.escaagnellis`, but Android treats their
different certificates as different application identities. They cannot
update over one another. Switching normally requires an uninstall, which
deletes local app data. Users must first export the supported primary backup
and, when the companion is enabled, the separate companion backup; after
installing the other variant, each backup must be restored through its matching
flow. No workflow may automatically uninstall the app or clear its data.

## Contributions

The approved contribution model uses Developer Certificate of Origin (DCO)
sign-off. Public contribution guidance will be added in a later phase before
the source repository accepts contributions.
