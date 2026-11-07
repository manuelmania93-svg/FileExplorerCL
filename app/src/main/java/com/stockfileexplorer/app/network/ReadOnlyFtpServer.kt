package com.stockfileexplorer.app.network

import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit-start, authenticated, read-only FTP core. Never started by constructing it.
 * No logging of credentials, no anonymous mode, no port forwarding, no write commands.
 * Plain FTP is unencrypted; the eventual UI must warn before opening a LAN listener.
 */
internal class ReadOnlyFtpServer(
    private val export: ReadOnlyExport,
    private val bindAddress: InetAddress,
    private val username: String,
    private val password: String
) : Closeable {
    private val running = AtomicBoolean(false)
    private val sockets = java.util.Collections.synchronizedSet(mutableSetOf<Closeable>())
    private val workers = Executors.newFixedThreadPool(3)
    private var listener: ServerSocket? = null
    private var acceptor: Thread? = null

    init {
        require(!bindAddress.isAnyLocalAddress && (bindAddress.isLoopbackAddress || bindAddress.isSiteLocalAddress))
        require(username.isNotBlank() && password.length >= 12)
        require(!username.contains('\r') && !username.contains('\n'))
    }

    fun start(): Int {
        check(running.compareAndSet(false, true))
        val server = try { ServerSocket(0, 2, bindAddress) } catch (e: Exception) { running.set(false); throw e }
        listener = server
        acceptor = Thread({
            while (running.get()) {
                try {
                    val socket = server.accept()
                    // Local peers only. The chosen bind interface is not a wildcard.
                    if (!(socket.inetAddress.isLoopbackAddress || socket.inetAddress.isSiteLocalAddress)) { socket.close(); continue }
                    if (sockets.size >= 3) { socket.close(); continue }
                    sockets.add(socket)
                    workers.submit { try { session(socket) } finally { sockets.remove(socket); socket.close() } }
                } catch (_: java.net.SocketException) { if (running.get()) close() }
            }
        }, "cl-readonly-ftp").apply { isDaemon = true; start() }
        return server.localPort
    }

    private fun session(socket: Socket) {
        socket.soTimeout = 60_000
        val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
        val output = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
        fun reply(code: Int, message: String) { output.write("$code $message\r\n"); output.flush() }
        var userAccepted = false
        var authenticated = false
        var failures = 0
        var cwd = ""
        var dataListener: ServerSocket? = null
        fun path(arg: String) = if (arg.startsWith('/')) arg.removePrefix("/") else if (cwd.isEmpty()) arg else "$cwd/$arg"
        fun closeData() { dataListener?.let { sockets.remove(it); runCatching { it.close() } }; dataListener = null }
        reply(220, "CL read-only file access")
        try {
            while (running.get()) {
                // Bounded command line prevents an unauthenticated peer growing memory indefinitely.
                val chars = StringBuilder()
                while (true) {
                    val next = input.read()
                    if (next == -1) return
                    if (next == 10) break
                    if (chars.length >= 4096) { reply(500, "Command too long"); return }
                    if (next != 13) chars.append(next.toChar())
                }
                val line = chars.toString()
                val command = line.substringBefore(' ').uppercase(java.util.Locale.ROOT)
                val arg = line.substringAfter(' ', "")
                if (command == "QUIT") { reply(221, "Closed"); return }
                if (command == "USER") { userAccepted = arg == username; authenticated = false; reply(331, "Password required"); continue }
                if (command == "PASS") {
                    authenticated = userAccepted && java.security.MessageDigest.isEqual(arg.toByteArray(), password.toByteArray())
                    if (authenticated) reply(230, "Read-only session") else { failures++; reply(530, "Authentication failed"); if (failures >= 3) return }
                    continue
                }
                if (!authenticated) { reply(530, "Login required"); continue }
                when (command) {
                    "NOOP" -> reply(200, "OK")
                    "SYST" -> reply(215, "UNIX Type: L8")
                    "TYPE", "OPTS" -> reply(200, "OK")
                    "PWD" -> reply(257, "\"/${cwd.replace("\"", "\"\"")}\"")
                    "CWD", "CDUP" -> {
                        val next = if (command == "CDUP") path("..") else path(arg)
                        val directory = runCatching { export.resolve(next) }.getOrNull()
                        if (directory?.isDirectory == true) { cwd = export.relative(directory); reply(250, "Directory changed") } else reply(550, "Unavailable")
                    }
                    "SIZE" -> { val file = runCatching { export.resolve(path(arg)) }.getOrNull(); if (file?.isFile == true) reply(213, file.length().toString()) else reply(550, "Unavailable") }
                    "PASV", "EPSV" -> {
                        closeData()
                        dataListener = ServerSocket(0, 1, bindAddress).also { it.soTimeout = 10_000; sockets.add(it) }
                        val port = dataListener!!.localPort
                        if (command == "EPSV") reply(229, "Entering Extended Passive Mode (|||$port|)")
                        else {
                            val address = bindAddress.address
                            if (address.size != 4) { closeData(); reply(522, "Use EPSV") }
                            else reply(227, "Entering Passive Mode (${address.joinToString(",") { (it.toInt() and 255).toString() }},${port / 256},${port % 256})")
                        }
                    }
                    "LIST", "NLST", "RETR" -> {
                        val target = runCatching { export.resolve(path(arg)) }.getOrNull()
                        if (target == null || (command == "RETR" && !target.isFile)) { closeData(); reply(550, "Unavailable"); continue }
                        val passive = dataListener
                        if (passive == null) { reply(425, "Use passive mode"); continue }
                        reply(150, "Opening data connection")
                        try {
                            passive.accept().use { data ->
                                require(data.inetAddress == socket.inetAddress) { "Data peer mismatch" }
                                data.soTimeout = 10_000; sockets.add(data)
                                try {
                                    if (command == "RETR") export.read(path(arg), data.getOutputStream())
                                    else {
                                        val listing = if (target.isDirectory) export.list(path(arg)) else listOf(target)
                                        data.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                                            listing.forEach { file ->
                                                // Never inject FTP listing lines from hostile filenames.
                                                val name = file.name.replace('\r', '_').replace('\n', '_')
                                                val value = if (command == "NLST") name else "${if (file.isDirectory) 'd' else '-'}r--r--r-- 1 owner group ${file.length()} Jan 01 00:00 $name"
                                                writer.write(value + "\r\n")
                                            }
                                        }
                                    }
                                } finally { sockets.remove(data) }
                            }
                            reply(226, "Transfer complete")
                        } catch (_: Exception) { reply(426, "Transfer failed") }
                        finally { closeData() }
                    }
                    else -> reply(502, "Read-only server: command unsupported")
                }
            }
        } catch (_: java.io.IOException) { /* Session closed or timed out. No credential logs. */ }
        finally { closeData() }
    }

    override fun close() {
        running.set(false)
        runCatching { listener?.close() }
        synchronized(sockets) { sockets.toList().forEach { runCatching { it.close() } }; sockets.clear() }
        workers.shutdownNow()
        acceptor?.interrupt()
    }
}
