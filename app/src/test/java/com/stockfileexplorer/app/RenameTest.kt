package com.stockfileexplorer.app
import com.stockfileexplorer.app.data.Rename
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
class RenameTest {
 @Test fun entireNameCanChangeWithoutOverwriteOrTraversal() {
  val root=Files.createTempDirectory("rename-test").toFile()
  try {
   val src=java.io.File(root,"video.mp4").apply { writeText("same bytes") }
   assertTrue(Rename.move(src,"now.txt"))
   val result=java.io.File(root,"now.txt")
   assertEquals("same bytes",result.readText())
   java.io.File(root,"existing.bin").writeText("keep")
   assertFalse(Rename.move(result,"existing.bin"))
   assertFalse(Rename.move(result,"../outside"))
   assertFalse(Rename.move(result,"bad/name"))
   assertTrue(Rename.move(result,"no-extension"))
  } finally { root.deleteRecursively() }
 }
}
