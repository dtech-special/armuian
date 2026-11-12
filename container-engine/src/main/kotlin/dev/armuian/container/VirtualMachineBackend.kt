package dev.armuian.container

import dev.armuian.core.ArmResult
import dev.armuian.core.ErrorCode
import dev.armuian.core.armFail

/**
 * Виртуальная машина с собственным ядром (KVM / AVF / crosvm).
 * ЧЕСТНО: запуск ВМ НЕ реализован — для него нужны ядро и диск гостя, которых нет в образах redroid.
 * Реализованы только реальные проверки: /dev/kvm, наличие AVF и crosvm. Недоступность запуска здесь —
 * «Не реализован», а не аппаратная несовместимость.
 */
class VirtualMachineBackend : ContainerBackend {
    override val kind = BackendKind.VIRTUAL_MACHINE
    override val implemented = false
    override val experimental = false

    override fun evaluate(ctx: EvalContext) = CompatibilityChecker.evaluate(kind, implemented, experimental, ctx)
    override suspend fun prepare(imageName: String): ArmResult<Unit> = armFail(ErrorCode.NOT_IMPLEMENTED, "VM backend is not implemented")
    override suspend fun start(imageName: String): ArmResult<Unit> = armFail(ErrorCode.NOT_IMPLEMENTED, "VM backend is not implemented")
    override suspend fun stop(): ArmResult<Unit> = armFail(ErrorCode.NOT_RUNNING, "VM is not running")
    override suspend fun restart(imageName: String): ArmResult<Unit> = armFail(ErrorCode.NOT_IMPLEMENTED, "VM backend is not implemented")
    override suspend fun status() = ContainerStatus(running = false)
    override fun hasSession() = false
    override fun currentImage(): String? = null
    override suspend fun logs(): List<String> = emptyList()
}
