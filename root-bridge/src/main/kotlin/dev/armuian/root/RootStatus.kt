package dev.armuian.root

enum class RootManager { KERNELSU, KERNELSU_NEXT, SUKISU, MAGISK, NOT_DETECTED }

sealed interface RootStatus {
    data object SuNotFound : RootStatus
    data class Denied(val details: String) : RootStatus
    data class Granted(val uid: Int, val manager: RootManager) : RootStatus
    data class Failed(val message: String) : RootStatus
}
