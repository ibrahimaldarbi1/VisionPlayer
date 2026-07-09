package com.example.data

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    type = type
)

fun LiveChannelEntity.toDomain() = LiveChannel(
    id = id,
    name = name,
    streamUrl = streamUrl,
    logoUrl = logoUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    epgId = epgId,
    channelNumber = channelNumber,
    isLocked = isLocked,
    isAdult = isAdult,
    hasCatchup = hasCatchup
)

fun MovieStreamEntity.toDomain() = Movie(
    id = id,
    title = title,
    streamUrl = streamUrl,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    description = description,
    year = year,
    duration = duration,
    genre = genre,
    rating = rating,
    cast = cast,
    director = director,
    isAdult = isAdult
)

fun SeriesStreamEntity.toDomain() = Series(
    id = id,
    title = title,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    description = description,
    year = year,
    genre = genre,
    rating = rating,
    cast = cast,
    director = director,
    isAdult = isAdult
)

fun SeriesSeasonEntity.toDomain() = Season(
    id = id,
    seriesId = seriesId,
    seasonNumber = seasonNumber,
    title = title
)

fun SeriesEpisodeEntity.toDomain() = Episode(
    id = id,
    seriesId = seriesId,
    seasonId = seasonId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title,
    streamUrl = streamUrl,
    description = description,
    duration = duration,
    posterUrl = posterUrl
)

// Domain/Xtream to Entity mapping helpers
fun Category.toEntity(sortOrder: Int = 0, hidden: Boolean = false, updatedAt: Long = System.currentTimeMillis()) = CategoryEntity(
    id = id,
    name = name,
    type = type,
    sortOrder = sortOrder,
    hidden = hidden,
    updatedAt = updatedAt
)

fun LiveChannel.toEntity(sortOrder: Int = 0, hidden: Boolean = false, updatedAt: Long = System.currentTimeMillis()) = LiveChannelEntity(
    id = id,
    name = name,
    streamUrl = streamUrl,
    logoUrl = logoUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    epgId = epgId,
    channelNumber = channelNumber,
    isLocked = isLocked,
    isAdult = isAdult,
    hasCatchup = hasCatchup,
    hidden = hidden,
    sortOrder = sortOrder,
    updatedAt = updatedAt
)

fun Movie.toEntity(normalizedTitle: String, hidden: Boolean = false, updatedAt: Long = System.currentTimeMillis()) = MovieStreamEntity(
    id = id,
    title = title,
    normalizedTitle = normalizedTitle,
    streamUrl = streamUrl,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    description = description,
    year = year,
    duration = duration,
    genre = genre,
    rating = rating,
    cast = cast,
    director = director,
    isAdult = isAdult,
    hidden = hidden,
    updatedAt = updatedAt
)

fun Series.toEntity(normalizedTitle: String, hidden: Boolean = false, updatedAt: Long = System.currentTimeMillis()) = SeriesStreamEntity(
    id = id,
    title = title,
    normalizedTitle = normalizedTitle,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    description = description,
    year = year,
    genre = genre,
    rating = rating,
    cast = cast,
    director = director,
    isAdult = isAdult,
    hidden = hidden,
    updatedAt = updatedAt
)

fun Season.toEntity(updatedAt: Long = System.currentTimeMillis()) = SeriesSeasonEntity(
    id = id,
    seriesId = seriesId,
    seasonNumber = seasonNumber,
    title = title,
    updatedAt = updatedAt
)

fun Episode.toEntity(normalizedTitle: String, updatedAt: Long = System.currentTimeMillis()) = SeriesEpisodeEntity(
    id = id,
    seriesId = seriesId,
    seasonId = seasonId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title,
    normalizedTitle = normalizedTitle,
    streamUrl = streamUrl,
    description = description,
    duration = duration,
    posterUrl = posterUrl,
    updatedAt = updatedAt
)
