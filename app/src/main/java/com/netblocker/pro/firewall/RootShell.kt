package com.netblocker.pro.firewall

import java.util.concurrent.TimeUnit

/**
 * Minimal su shell runner. Spawns `su`, streams the commands in, collects
 * stdout/stderr and reports whether the batch actually executed.
 */
object RootShell {

    data class Result(val executed: Boolean, val exitCode: Int, val output: String, val error: String)

    @Volatile
    private var cachedRoot: Boolean? = null

    fun isRootAvailable(force: Boolean = false, timeoutSec: Long = 60): Boolean {
        val cached = cachedRoot
        if (cached != null && !force) return cached
        val r = run(listOf("id -u"), timeoutSec = timeoutSec)
        val ok = r.executed && r.output.trim().startsWith("0")
        cachedRoot = ok
        return ok
    }

    fun invalidateRoot() {
        cachedRoot = null
    }

    fun run(commands: List<String>, timeoutSec: Long = 60): Result {
        var process: Process? = null
        return try {
            val pb = ProcessBuilder("su")
            pb.redirectErrorStream(false)
            process = pb.start()
            val proc = process

            val outBuilder = StringBuilder()
            val errBuilder = StringBuilder()
            val outThread = Thread {
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            outBuilder.append(line).append('\n')
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
            val errThread = Thread {
                try {
                    proc.errorStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            errBuilder.append(line).append('\n')
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
            outThread.isDaemon = true
            errThread.isDaemon = true
            outThread.start()
            errThread.start()

            proc.outputStream.use { os ->
                os.write("echo __NB_BEGIN__\n".toByteArray())
                for (cmd in commands) {
                    os.write(cmd.toByteArray())
                    os.write('\n'.code)
                }
                os.write("echo __NB_DONE__\n".toByteArray())
                os.write("exit\n".toByteArray())
                os.flush()
            }

            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                Result(false, -1, outBuilder.toString(), errBuilder.toString().ifBlank { "Timed out waiting for superuser" })
            } else {
                outThread.join(1500)
                errThread.join(1500)
                val out = outBuilder.toString()
                val ran = out.contains("__NB_DONE__")
                Result(
                    ran,
                    proc.exitValue(),
                    out.replace("__NB_BEGIN__", "").replace("__NB_DONE__", "").trim(),
                    errBuilder.toString().trim()
                )
            }
        } catch (e: Exception) {
            Result(false, -1, "", e.message ?: "su failed")
        } finally {
            runCatching { process?.destroy() }
        }
    }
}
