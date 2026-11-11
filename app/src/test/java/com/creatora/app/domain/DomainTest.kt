package com.creatora.app.domain

import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.normalizeHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainTest {
    @Test
    fun normalizeHexColor_acceptsSixDigitsWithOrWithoutHash() {
        assertEquals("#5B47F0", normalizeHexColor("5b47f0"))
        assertEquals("#5B47F0", normalizeHexColor(" #5b47F0 "))
    }

    @Test
    fun normalizeHexColor_rejectsEverythingElse() {
        listOf("", "#", "#FFF", "#1234567", "#12345G", "blue", "##123456").forEach {
            assertNull(it, normalizeHexColor(it))
        }
    }

    @Test
    fun assetType_comesFromTheMimeType() {
        assertEquals(AssetType.IMAGE, AssetType.fromMimeType("image/webp"))
        assertEquals(AssetType.VIDEO, AssetType.fromMimeType("video/mp4"))
        assertEquals(AssetType.AUDIO, AssetType.fromMimeType("audio/ogg"))
        assertNull(AssetType.fromMimeType("application/pdf"))
        assertNull(AssetType.fromMimeType(""))
    }
}
