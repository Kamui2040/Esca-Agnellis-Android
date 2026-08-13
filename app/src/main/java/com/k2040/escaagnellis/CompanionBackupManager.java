package com.k2040.escaagnellis;

import android.content.Context;

import java.util.Map;

final class CompanionBackupManager {
    private final CompanionRepository repository;

    static CompanionBackupManager create(Context context) {
        return new CompanionBackupManager(CompanionRepository.create(context));
    }

    CompanionBackupManager(CompanionRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("Missing companion repository");
        }
        this.repository = repository;
    }

    ExportResult exportBackup(long nowEpochMillis) {
        if (nowEpochMillis < 0L) {
            throw new IllegalArgumentException("Epoch time must not be negative");
        }

        CompanionRepository.LoadResult loaded = repository.load(nowEpochMillis);
        if (!loaded.isUsable()) {
            return ExportResult.unavailable(loaded.status);
        }
        return ExportResult.exported(CompanionBackupCodec.encode(loaded.state));
    }

    RestoreResult restoreBackup(
            Map<String, ?> document,
            long nowEpochMillis) {
        if (nowEpochMillis < 0L) {
            throw new IllegalArgumentException("Epoch time must not be negative");
        }

        final CompanionState target;
        try {
            target = CompanionBackupCodec.decode(document);
        } catch (CompanionBackupCodec.WrongFormatException ex) {
            return RestoreResult.rejected(RestoreStatus.WRONG_FORMAT);
        } catch (CompanionBackupCodec.UnsupportedSchemaException
                | CompanionBackupCodec.UnsupportedStateSchemaException ex) {
            return RestoreResult.rejected(RestoreStatus.UNSUPPORTED_SCHEMA);
        } catch (RuntimeException ex) {
            return RestoreResult.rejected(RestoreStatus.INVALID);
        }

        CompanionRepository.RestoreResult restored =
                repository.restoreBackup(target, nowEpochMillis);
        switch (restored.status) {
            case APPLIED:
                return RestoreResult.completed(
                        RestoreStatus.APPLIED,
                        restored.state);
            case NO_CHANGE:
                return RestoreResult.completed(
                        RestoreStatus.NO_CHANGE,
                        restored.state);
            case STORAGE_UNAVAILABLE:
                return RestoreResult.rejected(RestoreStatus.STORAGE_UNAVAILABLE);
            case ORIGINAL_RESTORED:
                return RestoreResult.rejected(RestoreStatus.ORIGINAL_RESTORED);
            case INDETERMINATE:
            default:
                return RestoreResult.rejected(RestoreStatus.INDETERMINATE);
        }
    }

    enum ExportStatus {
        EXPORTED,
        UNAVAILABLE
    }

    enum RestoreStatus {
        APPLIED,
        NO_CHANGE,
        WRONG_FORMAT,
        UNSUPPORTED_SCHEMA,
        INVALID,
        STORAGE_UNAVAILABLE,
        ORIGINAL_RESTORED,
        INDETERMINATE
    }

    static final class ExportResult {
        final ExportStatus status;
        final Map<String, Object> document;
        final CompanionRepository.LoadStatus loadStatus;

        private ExportResult(
                ExportStatus status,
                Map<String, Object> document,
                CompanionRepository.LoadStatus loadStatus) {
            this.status = status;
            this.document = document;
            this.loadStatus = loadStatus;
        }

        static ExportResult exported(Map<String, Object> document) {
            return new ExportResult(ExportStatus.EXPORTED, document, null);
        }

        static ExportResult unavailable(
                CompanionRepository.LoadStatus loadStatus) {
            return new ExportResult(ExportStatus.UNAVAILABLE, null, loadStatus);
        }
    }

    static final class RestoreResult {
        final RestoreStatus status;
        final CompanionState state;

        private RestoreResult(RestoreStatus status, CompanionState state) {
            this.status = status;
            this.state = state;
        }

        static RestoreResult completed(
                RestoreStatus status,
                CompanionState state) {
            return new RestoreResult(status, state);
        }

        static RestoreResult rejected(RestoreStatus status) {
            return new RestoreResult(status, null);
        }
    }
}
