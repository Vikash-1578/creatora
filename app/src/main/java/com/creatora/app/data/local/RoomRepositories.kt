package com.creatora.app.data.local

import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.ChannelSnapshot
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.CompetitorRepository
import com.creatora.app.domain.repository.SwipeRepository
import com.creatora.app.domain.research.Competitor
import com.creatora.app.domain.research.SwipeItem
import com.creatora.app.domain.repository.ChannelSnapshotRepository
import com.creatora.app.domain.repository.CharacterRepository
import com.creatora.app.domain.repository.IdeaRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.Repository
import com.creatora.app.domain.repository.ScheduledPostRepository
import com.creatora.app.domain.repository.VoicePresetRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Maps a [CrudDao] onto the domain [Repository] contract. */
abstract class RoomRepository<M, E>(
    private val dao: CrudDao<E>,
    private val toModel: (E) -> M,
    private val toEntity: (M) -> E,
    private val idOf: (M) -> Long,
) : Repository<M> {
    override fun observeAll(): Flow<List<M>> = dao.observeAll().map { rows -> rows.map(toModel) }
    override fun observe(id: Long): Flow<M?> = dao.observe(id).map { row -> row?.let(toModel) }

    override suspend fun save(item: M): Long {
        val rowId = dao.upsert(toEntity(item))
        return if (rowId == -1L) idOf(item) else rowId
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}

class RoomProjectRepository @Inject constructor(db: CreatoraDatabase) :
    RoomRepository<Project, ProjectEntity>(db.projectDao(), { it.toModel() }, { it.toEntity() }, { it.id }),
    ProjectRepository

class RoomIdeaRepository @Inject constructor(db: CreatoraDatabase) :
    RoomRepository<Idea, IdeaEntity>(db.ideaDao(), { it.toModel() }, { it.toEntity() }, { it.id }),
    IdeaRepository

class RoomAssetRepository @Inject constructor(db: CreatoraDatabase) :
    RoomRepository<Asset, AssetEntity>(db.assetDao(), { it.toModel() }, { it.toEntity() }, { it.id }),
    AssetRepository

class RoomLicenceRepository @Inject constructor(db: CreatoraDatabase) :
    RoomRepository<LicenceEntry, LicenceEntryEntity>(db.licenceEntryDao(), { it.toModel() }, { it.toEntity() }, { it.id }),
    LicenceRepository {
    private val dao = db.licenceEntryDao()
    override fun observeForAsset(assetId: Long): Flow<List<LicenceEntry>> =
        dao.observeForAsset(assetId).map { rows -> rows.map { it.toModel() } }
}

class RoomVoicePresetRepository @Inject constructor(db: CreatoraDatabase) :
    RoomRepository<VoicePreset, VoicePresetEntity>(db.voicePresetDao(), { it.toModel() }, { it.toEntity() }, { it.id }),
    VoicePresetRepository

class RoomCharacterRepository @Inject constructor(db: CreatoraDatabase) :
    RoomRepository<Character, CharacterEntity>(db.characterDao(), { it.toModel() }, { it.toEntity() }, { it.id }),
    CharacterRepository

class RoomScheduledPostRepository @Inject constructor(db: CreatoraDatabase) :
    RoomRepository<ScheduledPost, ScheduledPostEntity>(db.scheduledPostDao(), { it.toModel() }, { it.toEntity() }, { it.id }),
    ScheduledPostRepository

class RoomCompetitorRepository @Inject constructor(db: CreatoraDatabase) :
    RoomRepository<Competitor, CompetitorEntity>(db.competitorDao(), { it.toModel() }, { it.toEntity() }, { it.id }),
    CompetitorRepository

class RoomSwipeRepository @Inject constructor(db: CreatoraDatabase) :
    RoomRepository<SwipeItem, SwipeItemEntity>(db.swipeItemDao(), { it.toModel() }, { it.toEntity() }, { it.id }),
    SwipeRepository

class RoomChannelSnapshotRepository @Inject constructor(db: CreatoraDatabase) :
    RoomRepository<ChannelSnapshot, ChannelSnapshotEntity>(
        db.channelSnapshotDao(), { it.toModel() }, { it.toEntity() }, { it.id },
    ),
    ChannelSnapshotRepository {
    private val dao = db.channelSnapshotDao()
    override fun observeForChannel(channelId: String): Flow<List<ChannelSnapshot>> =
        dao.observeForChannel(channelId).map { rows -> rows.map { it.toModel() } }
}
