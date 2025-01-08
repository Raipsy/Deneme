package eu.kanade.tachiyomi.extension.all.namicomi.dto

import eu.kanade.tachiyomi.extension.all.namicomi.NamiComiConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ChapterListDto = PaginatedResponseDto<ChapterDataDto>

@Serializable
@SerialName(NamiComiConstants.chapter)
data class ChapterDataDto(override val attributes: ChapterAttributesDto? = null) : EntityDto()

@Serializable
data class ChapterAttributesDto(
    val name: String?,
    val volume: String?,
    val chapter: String?,
    val pages: Int,
    val publishAt: String,
) : AttributesDto()
