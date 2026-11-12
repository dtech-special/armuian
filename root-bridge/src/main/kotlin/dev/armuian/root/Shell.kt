package dev.armuian.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String, val timedOut: Boolean = false) {
    val ok: Boolean get() = exitCode == 0 && !timedOut
}

/** Абстракция запуска процессов — чтобы Root Bridge можно было тестировать без реального su. */
interface ShellRunner {
    suspend fun run(argv: List<String>, timeoutMs: Long = 15_000): ShellResult
}

class ProcessShellRunner : ShellRunner {
    override suspend fun run(argv: List<String>, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder(argv).start()
            p.outputStream.close()
            coroutineScope {
                val out = async { p.inputStream.bufferedReader().readText() }
                val err = async { p.errorStream.bufferedReader().readText() }
                if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly()
                    ShellResult(-1, "", "timeout", timedOut = true)
                } else {
                    ShellResult(p.exitValue(), out.await(), err.await())
                }
            }
        } catch (e: IOException) {
            // Программа su не найдена / не может быть запущена
            ShellResult(127, "", e.message ?: "exec failed")
        }
    }
}
