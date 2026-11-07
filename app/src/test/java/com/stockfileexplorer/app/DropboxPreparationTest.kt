package com.stockfileexplorer.app

import com.stockfileexplorer.app.network.*
import org.junit.Assert.*
import org.junit.Test

class DropboxPreparationTest {
    @Test fun pkceUsesKnownRfcVectorAndSingleUseState() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", DropboxPkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val flow = DropboxPkce("fixture-app-key", "clfixture://oauth")
        val request = flow.begin()
        assertEquals(43, request.verifier.length)
        assertTrue(request.url.contains("code_challenge_method=S256"))
        assertFalse(request.url.contains(request.verifier))
        assertEquals(request, flow.consume(request.state))
        assertTrue(runCatching { flow.consume(request.state) }.isFailure)
        flow.begin()
        assertTrue(runCatching { flow.consume("wrong-state") }.isFailure)
        assertTrue(runCatching { flow.consume(request.state) }.isFailure)
    }
    @Test fun parsesPaginationAndFolderVersusFile() {
        val page = DropboxReadClient.parsePage("""{"entries":[{".tag":"folder","id":"id:folder","name":"Fixture","path_lower":"/fixture"},{".tag":"file","id":"id:file","name":"notes.txt","path_lower":"/notes.txt","size":42}],"cursor":"fixture-cursor","has_more":true}""")
        assertTrue(page.hasMore); assertEquals("fixture-cursor", page.cursor)
        assertTrue(page.entries[0].folder); assertFalse(page.entries[1].folder); assertEquals(42L, page.entries[1].size)
    }
}
