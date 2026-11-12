package dev.armuian.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.armuian.app.R
import dev.armuian.core.ErrorCode
import dev.armuian.core.ErrorInfo

/** Локализованный текст ошибки по коду; технический message остаётся только в журнале. */
@Composable
fun errorText(f: dev.armuian.core.ArmResult.Fail): String = errorText(f.toInfo())

@Composable
fun errorText(e: ErrorInfo): String {
    val a0 = e.args.getOrElse(0) { "" }
    val a1 = e.args.getOrElse(1) { "" }
    return when (e.code) {
        ErrorCode.STORAGE_PATH_EMPTY -> stringResource(R.string.err_storage_path_empty)
        ErrorCode.STORAGE_PATH_NOT_ABSOLUTE -> stringResource(R.string.err_storage_not_absolute, a0)
        ErrorCode.STORAGE_PATH_DOTDOT -> stringResource(R.string.err_storage_dotdot, a0)
        ErrorCode.STORAGE_NOT_DIRECTORY -> stringResource(R.string.err_storage_not_dir, a0)
        ErrorCode.STORAGE_MKDIR_FAILED -> stringResource(R.string.err_storage_mkdir, a0)
        ErrorCode.STORAGE_DELETE_FAILED -> stringResource(R.string.err_storage_delete, a0)
        ErrorCode.STORAGE_BAD_IMAGE_NAME -> stringResource(R.string.err_storage_bad_name, a0)
        ErrorCode.HTTP_STATUS -> stringResource(R.string.err_http_status, a0, a1)
        ErrorCode.RANGE_REJECTED -> stringResource(R.string.err_range_rejected)
        ErrorCode.CHECKSUM_MISMATCH -> stringResource(R.string.err_checksum, a0, a1)
        ErrorCode.RATE_LIMIT -> stringResource(R.string.err_rate_limit)
        ErrorCode.NO_ARCH_VARIANT -> stringResource(R.string.err_no_arch, a0)
        ErrorCode.MANIFEST_NO_CONFIG -> stringResource(R.string.err_manifest_no_config)
        ErrorCode.MANIFEST_NO_LAYERS -> stringResource(R.string.err_manifest_no_layers)
        ErrorCode.EMPTY_RESPONSE -> stringResource(R.string.err_empty_response)
        ErrorCode.INCOMPLETE_DOWNLOAD -> stringResource(R.string.err_incomplete, a0, a1)
        ErrorCode.RENAME_FAILED -> stringResource(R.string.err_rename, a0)
        ErrorCode.REGISTRY_TOKEN -> stringResource(R.string.err_registry_token)
        ErrorCode.UNSAFE_ARCHIVE_PATH -> stringResource(R.string.err_unsafe_path, a0)
        ErrorCode.ZSTD_UNSUPPORTED -> stringResource(R.string.err_zstd)
        ErrorCode.ROOTFS_EXISTS -> stringResource(R.string.err_rootfs_exists, a0)
        ErrorCode.BAD_SYSTEM_NAME -> stringResource(R.string.err_bad_system_name, a0)
        ErrorCode.EMPTY_IMAGE_REF -> stringResource(R.string.err_empty_ref)
        ErrorCode.NO_REPOSITORY -> stringResource(R.string.err_no_repo, a0)
        ErrorCode.ONLY_SHA256 -> stringResource(R.string.err_only_sha256, a0)
        ErrorCode.NOT_INSTALLED -> stringResource(R.string.err_not_installed)
        ErrorCode.IMAGE_NOT_INSTALLED -> stringResource(R.string.err_image_not_installed, a0)
        ErrorCode.UNSUPPORTED_CONFIG -> stringResource(R.string.err_unsupported_config, a0)
        ErrorCode.NOT_IMPLEMENTED -> stringResource(R.string.err_not_implemented)
        ErrorCode.NOT_RUNNING -> stringResource(R.string.err_not_running)
        ErrorCode.ROOT_REQUIRED -> stringResource(R.string.err_root_required)
        ErrorCode.ROOT_LOST -> stringResource(R.string.err_root_lost)
        ErrorCode.GUEST_START_FAILED -> stringResource(R.string.err_guest_start_failed, a0)
        ErrorCode.GUEST_ALREADY_RUNNING -> stringResource(R.string.err_guest_running)
        ErrorCode.GUEST_STOP_FAILED -> stringResource(R.string.err_guest_stop_failed, a0)
        ErrorCode.IMAGE_INVALID -> stringResource(R.string.err_image_invalid)
        ErrorCode.BACKEND_NOT_AVAILABLE -> stringResource(R.string.err_backend_unavailable)
        ErrorCode.IMAGE_IN_USE -> stringResource(R.string.err_image_in_use, a0)
        ErrorCode.UNKNOWN -> stringResource(R.string.err_unknown, e.message)
    }
}
