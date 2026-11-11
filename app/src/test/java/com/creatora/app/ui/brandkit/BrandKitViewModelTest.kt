package com.creatora.app.ui.brandkit

import com.creatora.app.domain.model.FontFile
import com.creatora.app.domain.model.PickedFile
import com.creatora.app.testutil.FakeBrandKitRepository
import com.creatora.app.testutil.FakeUserFiles
import com.creatora.app.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrandKitViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeBrandKitRepository()
    private val files = FakeUserFiles()
    private val viewModel = BrandKitViewModel(repository, files)

    @Test
    fun addColor_normalizesAndIgnoresDuplicates() {
        assertTrue(viewModel.addColor("5b47f0"))
        assertTrue(viewModel.addColor(" #5B47F0 "))
        assertTrue(viewModel.addColor("#14151a"))

        assertEquals(listOf("#5B47F0", "#14151A"), repository.brandKit.value.colors)
    }

    @Test
    fun addColor_rejectsInvalidText() {
        assertFalse(viewModel.addColor("red"))
        assertFalse(viewModel.addColor("#12345"))
        assertFalse(viewModel.addColor("#GGGGGG"))
        assertTrue(repository.brandKit.value.colors.isEmpty())
    }

    @Test
    fun removeColor_removesIt() {
        viewModel.addColor("#111111")
        viewModel.addColor("#222222")

        viewModel.removeColor("#111111")

        assertEquals(listOf("#222222"), repository.brandKit.value.colors)
    }

    @Test
    fun setLogo_savesAnImageAndDiscardsTheOldOne() {
        files.readable["content://a"] = PickedFile("file:///a.png", "a.png", "image/png")
        files.readable["content://b"] = PickedFile("file:///b.png", "b.png", "image/png")

        viewModel.setLogo("content://a")
        viewModel.setLogo("content://b")

        assertEquals("file:///b.png", repository.brandKit.value.logoUri)
        assertEquals(listOf("file:///a.png"), files.discarded)
    }

    @Test
    fun setLogo_rejectsNonImagesAndReportsIt() {
        files.readable["content://doc"] = PickedFile("file:///doc.pdf", "doc.pdf", "application/pdf")

        viewModel.setLogo("content://doc")

        assertNull(repository.brandKit.value.logoUri)
        assertEquals(listOf("file:///doc.pdf"), files.discarded)
        assertEquals(BrandKitMessage.INVALID_LOGO, viewModel.message.value)

        viewModel.messageShown()
        assertNull(viewModel.message.value)
    }

    @Test
    fun setLogo_reportsAnUnreadableFile() {
        viewModel.setLogo("content://missing")
        assertEquals(BrandKitMessage.IMPORT_FAILED, viewModel.message.value)
    }

    @Test
    fun removeLogo_clearsItAndDeletesTheFile() {
        files.readable["content://a"] = PickedFile("file:///a.png", "a.png", "image/png")
        viewModel.setLogo("content://a")

        viewModel.removeLogo()

        assertNull(repository.brandKit.value.logoUri)
        assertEquals(listOf("file:///a.png"), files.discarded)
    }

    @Test
    fun addFont_acceptsTtfAndOtfOnly() {
        files.readable["content://1"] = PickedFile("file:///Brand.TTF", "Brand.TTF", "font/ttf")
        files.readable["content://2"] = PickedFile("file:///Sans.otf", "Sans.otf", "font/otf")
        files.readable["content://3"] = PickedFile("file:///notes.txt", "notes.txt", "text/plain")

        viewModel.addFont("content://1")
        viewModel.addFont("content://2")
        viewModel.addFont("content://3")

        assertEquals(listOf("Brand.TTF", "Sans.otf"), repository.brandKit.value.fonts.map { it.name })
        assertEquals(listOf("file:///notes.txt"), files.discarded)
        assertEquals(BrandKitMessage.INVALID_FONT, viewModel.message.value)
    }

    @Test
    fun removeFont_removesItAndDeletesTheFile() {
        files.readable["content://1"] = PickedFile("file:///Brand.ttf", "Brand.ttf", "font/ttf")
        viewModel.addFont("content://1")
        val font: FontFile = repository.brandKit.value.fonts.single()

        viewModel.removeFont(font)

        assertTrue(repository.brandKit.value.fonts.isEmpty())
        assertEquals(listOf("file:///Brand.ttf"), files.discarded)
    }
}
