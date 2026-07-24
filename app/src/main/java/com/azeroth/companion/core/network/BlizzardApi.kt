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

    @GET("/data/wow/token/index")
    suspend fun tokenIndex(@Query("namespace") namespace: String): TokenIndexDto
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
