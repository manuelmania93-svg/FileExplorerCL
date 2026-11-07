package com.stockfileexplorer.app

import com.stockfileexplorer.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files

class ReadOnlyFtpServerTest {
    @Test fun authenticationFixtureDownloadAndStop() {
        val root = Files.createTempDirectory("cl-ftp-fixture").toFile()
        val bytes = ByteArray(20000) { (it % 251).toByte() }
        File(root, "fixture.bin").writeBytes(bytes)
        val server = ReadOnlyFtpServer(ReadOnlyExport(root), InetAddress.getLoopbackAddress(), "fixture", "fixture-test-only-password")
        val port = server.start()
        try {
            Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                socket.soTimeout = 3000
                val reader = socket.getInputStream().bufferedReader()
                val writer = socket.getOutputStream().bufferedWriter()
                fun command(text: String): String { writer.write(text + "\r\n"); writer.flush(); return reader.readLine() }
                assertTrue(reader.readLine().startsWith("220"))
                assertTrue(command("LIST").startsWith("530"))
                command("USER fixture")
                assertTrue(command("PASS incorrect").startsWith("530"))
                assertTrue(command("PASS fixture-test-only-password").startsWith("230"))
                assertTrue(command("CWD ..").startsWith("550"))
                assertTrue(command("DELE fixture.bin").startsWith("502"))
                val passive = command("EPSV")
                val dataPort = passive.substringAfter("|||").substringBefore('|').toInt()
                Socket(InetAddress.getLoopbackAddress(), dataPort).use { data ->
                    assertTrue(command("RETR fixture.bin").startsWith("150"))
                    assertArrayEquals(bytes, data.getInputStream().readBytes())
                    assertTrue(reader.readLine().startsWith("226"))
                }
                assertArrayEquals(bytes, File(root, "fixture.bin").readBytes())
                server.close()
                assertEquals(-1, reader.read())
            }
            assertTrue(runCatching { Socket(InetAddress.getLoopbackAddress(), port).close() }.isFailure)
        } finally { server.close(); root.deleteRecursively() }
    }
}
