package com.creatora.app.testutil

import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.BrandKit
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.model.PickedFile
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.BrandKitRepository
import com.creatora.app.domain.repository.CharacterRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.Repository
import com.creatora.app.domain.repository.ScheduledPostRepository
import com.creatora.app.domain.repository.UserFiles
import com.creatora.app.domain.repository.VoicePresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [Repository]: ids start at 1, [items] holds the current rows in insertion order. */
open class FakeRepository<T>(
    private val idOf: (T) -> Long,
    private val withId: (T, Long) -> T,
) : Repository<T> {
    val items = MutableStateFlow<List<T>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<T>> = items
    override fun observe(id: Long): Flow<T?> = items.map { list -> list.firstOrNull { idOf(it) == id } }

    override suspend fun save(item: T): Long {
        val id = if (idOf(item) == 0L) nextId++ else idOf(item)
        val saved = withId(item, id)
        items.value = if (items.value.any { idOf(it) == id }) {
            items.value.map { if (idOf(it) == id) saved else it }
        } else {
            items.value + saved
        }
        return id
    }

    override suspend fun delete(id: Long) {
        items.value = items.value.filterNot { idOf(it) == id }
    }
}

class FakeProjectRepository : FakeRepository<Project>({ it.id }, { p, id -> p.copy(id = id) }), ProjectRepository
class FakeAssetRepository : FakeRepository<Asset>({ it.id }, { a, id -> a.copy(id = id) }), AssetRepository
class FakeCharacterRepository : FakeRepository<Character>({ it.id }, { c, id -> c.copy(id = id) }), CharacterRepository
class FakeVoicePresetRepository : FakeRepository<VoicePreset>({ it.id }, { v, id -> v.copy(id = id) }), VoicePresetRepository
class FakeScheduledPostRepository : FakeRepository<ScheduledPost>({ it.id }, { p, id -> p.copy(id = id) }), ScheduledPostRepository

class FakeLicenceRepository : FakeRepository<LicenceEntry>({ it.id }, { e, id -> e.copy(id = id) }), LicenceRepository {
    override fun observeForAsset(assetId: Long): Flow<List<LicenceEntry>> =
        items.map { list -> list.filter { it.assetId == assetId } }
}

class FakeBrandKitRepository : BrandKitRepository {
    override val brandKit = MutableStateFlow(BrandKit())
    override suspend fun update(transform: (BrandKit) -> BrandKit) {
        brandKit.value = transform(brandKit.value)
    }
}

/** Files that can be imported are listed in [readable] by their source URI. */
class FakeUserFiles : UserFiles {
    val readable = mutableMapOf<String, PickedFile>()
    val discarded = mutableListOf<String>()
    override suspend fun import(uri: String): PickedFile? = readable[uri]
    override suspend fun discard(uri: String) {
        discarded += uri
    }
}

class FakeSpeechPlayer : com.creatora.app.domain.repository.SpeechPlayer {
    data class Spoken(val text: String, val pitch: Float, val rate: Float)

    val spoken = mutableListOf<Spoken>()
    var available = true
    var stops = 0
    var released = false

    override suspend fun speak(text: String, pitch: Float, speechRate: Float): Boolean {
        if (available) spoken += Spoken(text, pitch, speechRate)
        return available
    }

    override fun stop() {
        stops++
    }

    override fun release() {
        released = true
    }
}
