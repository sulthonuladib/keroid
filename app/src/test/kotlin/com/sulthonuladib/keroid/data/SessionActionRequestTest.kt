package com.sulthonuladib.keroid.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionActionRequestTest {
    @Test fun closeOnlySendsIdAndNeverRequestsWorktreeRemovalOrForce() {
        assertEquals(Json.parseToJsonElement("""{"id":"session"}"""),
            Json.parseToJsonElement(wireJson.encodeToString(IdRequest("session"))))
    }

    @Test fun renameUsesSessionNameAndDoesNotImpersonateAnAgentPane() {
        assertEquals(Json.parseToJsonElement("""{"id":"session","name":"Review"}"""),
            Json.parseToJsonElement(wireJson.encodeToString(RenameSessionRequest("session", "Review"))))
    }

    @Test fun emptyNameIsSentExplicitlyToRestoreAutomaticNaming() {
        assertEquals(Json.parseToJsonElement("""{"id":"session","name":""}"""),
            Json.parseToJsonElement(wireJson.encodeToString(RenameSessionRequest("session", ""))))
    }
}
