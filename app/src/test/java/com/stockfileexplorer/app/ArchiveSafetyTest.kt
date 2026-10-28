package com.stockfileexplorer.app
import com.stockfileexplorer.app.data.Archives
import org.junit.Test
import org.junit.Assert.*
import java.io.File
class ArchiveSafetyTest {
 @Test fun pathsStayWithinNewRoot() {
  val root=File("/tmp/extracted")
  for (p in listOf("../escape", "/absolute", "C:/escape", "a/../../escape", "..\\escape", "")) {
   try { Archives.safeTarget(root,p);fail("Accepted $p") } catch (_: IllegalArgumentException) {}
  }
  assertEquals(File(root,"notes/file.txt").canonicalPath, Archives.safeTarget(root,"notes/file.txt").path)
 }
}
