package com.azeroth.companion.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoints de perfil y datos de la API pública de Blizzard (§2.3).
 * La app habla directo con Blizzard: cero backend propietario obligatorio (§0.2).
 */
interface BlizzardApi {

    @GET("/profile/user/wow")
    suspend fun userProfile(
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): UserProfileDto

    @GET("/profile/wow/character/{realm}/{name}")
    suspend fun characterProfile(
        @Path("realm") realmSlug: String,
        @Path("name") name: String,
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): CharacterProfileDto

    @GET("/profile/wow/character/{realm}/{name}/quests/completed")
    suspend fun completedQuests(
        @Path("realm") realmSlug: String,
        @Path("name") name: String,
        @Query("namespace") namespace: String,
    ): CompletedQuestsDto

    @GET("/profile/wow/character/{realm}/{name}/reputations")
    suspend fun reputations(
        @Path("realm") realmSlug: String,
        @Path("name") name: String,
        @Query("namespace") namespace: String,
    ): ReputationsDto

    @GET("/profile/wow/character/{realm}/{name}/mythic-keystone-profile")
    suspend fun mythicKeystoneProfile(
        @Path("realm") realmSlug: String,
        @Path("name") name: String,
        @Query("namespace") namespace: String,
    ): MythicKeystoneProfileDto

    @GET("/profile/wow/character/{realm}/{name}/encounters/raids")
    suspend fun raidEncounters(
        @Path("realm") realmSlug: String,
        @Path("name") name: String,
        @Query("namespace") namespace: String,
    ): RaidEncountersDto

    @GET("/profile/wow/character/{realm}/{name}/achievements")
    suspend fun achievements(
        @Path("realm") realmSlug: String,
        @Path("name") name: String,
        @Query("namespace") namespace: String,
    ): AchievementsDto

    @GET("/profile/wow/character/{realm}/{name}/collections/mounts")
    suspend fun mounts(
        @Path("realm") realmSlug: String,
        @Path("name") name: String,
        @Query("namespace") namespace: String,
    ): MountsDto

    @GET("/profile/wow/character/{realm}/{name}/equipment")
    suspend fun equipment(
        @Path("realm") realmSlug: String,
        @Path("name") name: String,
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): EquipmentDto

    @GET("/profile/wow/character/{realm}/{name}/collections/mounts")
    suspend fun mountsNamed(
        @Path("realm") realmSlug: String,
        @Path("name") name: String,
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): MountsDto

    @GET("/profile/wow/character/{realm}/{name}/mythic-keystone-profile/season/{seasonId}")
    suspend fun mythicSeason(
        @Path("realm") realmSlug: String,
        @Path("name") name: String,
        @Path("seasonId") seasonId: Int,
        @Query("namespace") namespace: String,
    ): MythicSeasonDto

    @GET("/data/wow/mythic-keystone/season/index")
    suspend fun mythicSeasonIndex(
        @Query("namespace") namespace: String,
    ): MythicSeasonIndexDto

    @GET("/data/wow/journal-expansion/index")
    suspend fun journalExpansions(
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): JournalExpansionIndexDto

    @GET("/data/wow/token/index")
    suspend fun tokenIndex(@Query("namespace") namespace: String): TokenIndexDto

    // ---- Journal: contenido de mazmorras y bandas (datos estáticos) ----

    @GET("/data/wow/journal-expansion/{id}")
    suspend fun journalExpansion(
        @Path("id") id: Int,
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): JournalExpansionDto

    @GET("/data/wow/journal-instance/{id}")
    suspend fun journalInstance(
        @Path("id") id: Int,
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): JournalInstanceDto

    @GET("/data/wow/journal-encounter/{id}")
    suspend fun journalEncounter(
        @Path("id") id: Int,
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): JournalEncounterDto

    @GET("/data/wow/media/item/{id}")
    suspend fun itemMedia(
        @Path("id") id: Int,
        @Query("namespace") namespace: String,
    ): MediaDto

    @GET("/data/wow/quest/area/index")
    suspend fun questAreaIndex(
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): QuestAreaIndexDto

    @GET("/data/wow/quest/area/{id}")
    suspend fun questArea(
        @Path("id") id: Int,
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): QuestAreaDto

    @GET("/data/wow/quest/{id}")
    suspend fun quest(
        @Path("id") id: Int,
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): QuestDto

    @GET("/data/wow/mount/{id}")
    suspend fun mount(
        @Path("id") id: Int,
        @Query("namespace") namespace: String,
        @Query("locale") locale: String = "es_MX",
    ): MountDto

    @GET("/data/wow/media/creature-display/{id}")
    suspend fun creatureDisplayMedia(
        @Path("id") id: Int,
        @Query("namespace") namespace: String,
    ): MediaDto
}

// ---- DTOs mínimos (solo los campos que la app consume) ----

@Serializable
data class UserProfileDto(val wow_accounts: List<WowAccountDto> = emptyList())

@Serializable
data class WowAccountDto(val id: Long = 0, val characters: List<AccountCharacterDto> = emptyList())

@Serializable
data class AccountCharacterDto(
    val id: Long,
    val name: String,
    val realm: RealmRefDto,
    val level: Int = 0,
    val faction: TypedNameDto? = null,
    val playable_class: KeyedNameDto? = null,
)

@Serializable
data class RealmRefDto(val slug: String, val name: String? = null)

@Serializable
data class TypedNameDto(val type: String? = null, val name: String? = null)

@Serializable
data class KeyedNameDto(val id: Int = 0, val name: String? = null)

@Serializable
data class CharacterProfileDto(
    val id: Long,
    val name: String,
    val level: Int = 0,
    val average_item_level: Int = 0,
    val equipped_item_level: Int = 0,
    val active_spec: KeyedNameDto? = null,
    val last_login_timestamp: Long? = null,
)

@Serializable
data class CompletedQuestsDto(val quests: List<KeyedNameDto> = emptyList())

@Serializable
data class ReputationsDto(val reputations: List<ReputationDto> = emptyList())

@Serializable
data class ReputationDto(val faction: KeyedNameDto, val standing: StandingDto)

@Serializable
data class StandingDto(val raw: Int = 0, val value: Int = 0, val max: Int = 0)

@Serializable
data class MythicKeystoneProfileDto(val current_period: CurrentPeriodDto? = null)

@Serializable
data class CurrentPeriodDto(val best_runs: List<MythicRunDto> = emptyList())

@Serializable
data class MythicRunDto(val completed_timestamp: Long = 0, val keystone_level: Int = 0)

@Serializable
data class RaidEncountersDto(val expansions: List<ExpansionProgressDto> = emptyList())

@Serializable
data class ExpansionProgressDto(val instances: List<InstanceProgressDto> = emptyList())

@Serializable
data class InstanceProgressDto(val instance: KeyedNameDto, val modes: List<ModeProgressDto> = emptyList())

@Serializable
data class ModeProgressDto(val progress: ProgressDto? = null)

@Serializable
data class ProgressDto(val completed_count: Int = 0, val total_count: Int = 0)

@Serializable
data class TokenIndexDto(val price: Long = 0, val last_updated_timestamp: Long = 0)

@Serializable
data class AchievementsDto(val achievements: List<AchievementEntryDto> = emptyList())

@Serializable
data class AchievementEntryDto(val id: Int, val completed_timestamp: Long? = null)

@Serializable
data class MountsDto(val mounts: List<MountEntryDto> = emptyList())

@Serializable
data class MountEntryDto(val mount: KeyedNameDto)

@Serializable
data class JournalExpansionDto(
    val id: Int = 0,
    val name: String = "",
    val dungeons: List<KeyedNameDto> = emptyList(),
    val raids: List<KeyedNameDto> = emptyList(),
)

@Serializable
data class JournalInstanceDto(
    val id: Int = 0,
    val name: String = "",
    val minimum_level: Int = 0,
    val encounters: List<KeyedNameDto> = emptyList(),
    val category: JournalCategoryDto? = null,
)

@Serializable
data class JournalCategoryDto(val type: String? = null)

@Serializable
data class JournalEncounterDto(
    val id: Int = 0,
    val name: String = "",
    val items: List<JournalLootDto> = emptyList(),
)

@Serializable
data class JournalLootDto(val item: KeyedNameDto? = null)

@Serializable
data class MediaDto(val assets: List<MediaAssetDto> = emptyList())

@Serializable
data class MediaAssetDto(val key: String = "", val value: String = "")

@Serializable
data class QuestAreaIndexDto(val areas: List<KeyedNameDto> = emptyList())

@Serializable
data class QuestAreaDto(
    val id: Int = 0,
    val name: String? = null,
    val quests: List<KeyedNameDto> = emptyList(),
)

@Serializable
data class QuestDto(
    val id: Int = 0,
    val title: String? = null,
    val area: KeyedNameDto? = null,
    val description: String? = null,
    val requirements: QuestRequirementsDto? = null,
    val rewards: QuestRewardsDto? = null,
)

@Serializable
data class QuestRequirementsDto(
    val min_character_level: Int = 0,
    val max_character_level: Int = 0,
)

@Serializable
data class QuestRewardsDto(val items: QuestRewardItemsDto? = null)

@Serializable
data class QuestRewardItemsDto(val items: List<QuestRewardItemDto> = emptyList())

@Serializable
data class QuestRewardItemDto(val item: KeyedNameDto? = null)

@Serializable
data class MountDto(
    val id: Int = 0,
    val name: String? = null,
    val creature_displays: List<CreatureDisplayRefDto> = emptyList(),
)

@Serializable
data class CreatureDisplayRefDto(val id: Int = 0)

@Serializable
data class JournalExpansionIndexDto(val tiers: List<KeyedNameDto> = emptyList())

@Serializable
data class EquipmentDto(val equipped_items: List<EquippedItemDto> = emptyList())

@Serializable
data class EquippedItemDto(
    val item: KeyedNameDto? = null,
    val slot: TypedNameDto? = null,
    val name: String? = null,
    val quality: TypedNameDto? = null,
    val level: ItemLevelDto? = null,
)

@Serializable
data class ItemLevelDto(val value: Int = 0)

@Serializable
data class MythicSeasonIndexDto(
    val seasons: List<SeasonRefDto> = emptyList(),
    val current_season: SeasonRefDto? = null,
)

@Serializable
data class SeasonRefDto(val id: Int = 0)

@Serializable
data class MythicSeasonDto(
    val best_runs: List<MythicRunDto> = emptyList(),
    val mythic_rating: MythicRatingDto? = null,
)

@Serializable
data class MythicRatingDto(val rating: Double = 0.0)
