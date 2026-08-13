# Esca Agnellis issue #30 release hardening.
#
# Current inspection found no reflection, dynamic resource lookup, JNI, Java
# serialization, or annotation-driven runtime entry points that require
# app-specific keep rules. Manifest components and Android framework callbacks
# are handled by the Android Gradle plugin and optimized default Android rules.
#
# Keep this file narrow: add app rules only when a concrete R8 warning or
# runtime entry point requires one.
