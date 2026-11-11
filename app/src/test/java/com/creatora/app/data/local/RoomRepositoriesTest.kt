package com.creatora.app.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.ChannelSnapshot
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.model.VoicePreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomRepositoriesTest {
    private lateinit var db: CreatoraDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            CreatoraDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun newProject(title: String = "Video", updatedAt: Long = 1) =
        RoomProjectRepository(db).save(Project(title = title, createdAt = 1, updatedAt = updatedAt))

    @Test
    fun projects_saveUpdateOrderAndDelete() = runTest {
        val repository = RoomProjectRepository(db)
        val oldId = repository.save(Project(title = "Old", createdAt = 1, updatedAt = 1))
        val newId = repository.save(Project(title = "New", createdAt = 2, updatedAt = 2))
        assertEquals(listOf("New", "Old"), repository.observeAll().first().map { it.title })

        // Updating returns the same id and moves the project to the top.
        val renamed = repository.observe(oldId).first()!!.copy(title = "Renamed", updatedAt = 3)
        assertEquals(oldId, repository.save(renamed))
        assertEquals(listOf("Renamed", "New"), repository.observeAll().first().map { it.title })

        repository.delete(newId)
        assertNull(repository.observe(newId).first())
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun ideas_keepIdeaWhenItsProjectIsDeleted() = runTest {
        val projectId = newProject()
        val repository = RoomIdeaRepository(db)
        val ideaId = repository.save(Idea(title = "Hook idea", projectId = projectId))

        RoomProjectRepository(db).delete(projectId)

        val idea = repository.observe(ideaId).first()!!
        assertEquals("Hook idea", idea.title)
        assertNull(idea.projectId)
    }

    @Test
    fun assets_deletingAnAssetDeletesItsLicenceEntries() = runTest {
        val assets = RoomAssetRepository(db)
        val licences = RoomLicenceRepository(db)
        val assetId = assets.save(
            Asset(uri = "content://media/1", type = AssetType.IMAGE, name = "cover.jpg", mimeType = "image/jpeg"),
        )
        val otherId = assets.save(
            Asset(uri = "content://media/2", type = AssetType.AUDIO, name = "music.mp3", mimeType = "audio/mpeg"),
        )
        licences.save(LicenceEntry(assetId = assetId, source = "Own photo", licence = "Owned"))
        licences.save(LicenceEntry(assetId = otherId, source = "Free Music Archive", licence = "CC BY 4.0"))
        assertEquals(1, licences.observeForAsset(assetId).first().size)

        assets.delete(assetId)

        assertTrue(licences.observeForAsset(assetId).first().isEmpty())
        assertEquals(listOf("CC BY 4.0"), licences.observeAll().first().map { it.licence })
    }

    @Test
    fun voicePresets_updateKeepsId() = runTest {
        val repository = RoomVoicePresetRepository(db)
        val id = repository.save(VoicePreset(name = "Narrator"))
        repository.save(repository.observe(id).first()!!.copy(pitch = 1.2f, speechRate = 0.9f))

        val preset = repository.observeAll().first().single()
        assertEquals(id, preset.id)
        assertEquals(1.2f, preset.pitch)
        assertEquals(0.9f, preset.speechRate)
    }

    @Test
    fun characters_sortedByNameAndUnlinkedWhenVoiceIsDeleted() = runTest {
        val voices = RoomVoicePresetRepository(db)
        val characters = RoomCharacterRepository(db)
        val voiceId = voices.save(VoicePreset(name = "Deep"))
        characters.save(Character(name = "zara", voicePresetId = voiceId))
        characters.save(Character(name = "Arjun"))
        assertEquals(listOf("Arjun", "zara"), characters.observeAll().first().map { it.name })

        voices.delete(voiceId)

        assertNull(characters.observeAll().first().single { it.name == "zara" }.voicePresetId)
    }

    @Test
    fun scheduledPosts_soonestFirstAndDeletedWithProject() = runTest {
        val projectId = newProject()
        val repository = RoomScheduledPostRepository(db)
        repository.save(ScheduledPost(projectId = projectId, platform = Platform.YOUTUBE, scheduledAt = 200))
        repository.save(ScheduledPost(projectId = projectId, platform = Platform.INSTAGRAM, scheduledAt = 100))
        assertEquals(listOf(100L, 200L), repository.observeAll().first().map { it.scheduledAt })

        RoomProjectRepository(db).delete(projectId)

        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun channelSnapshots_filteredByChannelOldestFirst() = runTest {
        val repository = RoomChannelSnapshotRepository(db)
        repository.save(ChannelSnapshot(channelId = "a", subscriberCount = 20, viewCount = 0, videoCount = 0, capturedAt = 2))
        repository.save(ChannelSnapshot(channelId = "a", subscriberCount = 10, viewCount = 0, videoCount = 0, capturedAt = 1))
        repository.save(ChannelSnapshot(channelId = "b", subscriberCount = 99, viewCount = 0, videoCount = 0, capturedAt = 3))

        assertEquals(listOf(10L, 20L), repository.observeForChannel("a").first().map { it.subscriberCount })
        assertEquals(3, repository.observeAll().first().size)
    }
}
