package com.theveloper.pixelplay.data.ai


import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.max

class AiPlaylistGenerator @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val aiOrchestrator: AiOrchestrator,
    private val digestGenerator: UserProfileDigestGenerator,
    private val preferencesRepo: AiPreferencesRepository,
    private val engagementDao: com.theveloper.pixelplay.data.database.EngagementDao,
    private val json: Json
) {

    suspend fun generate(
        userPrompt: String,
        allSongs: List<Song>,
        minLength: Int,
        maxLength: Int,
        candidateSongs: List<Song>? = null,
        type: AiSystemPromptType = AiSystemPromptType.PLAYLIST
    ): Result<List<Song>> {
        return try {
            val isExplicitDiscovery = userPrompt.contains("discovery", ignoreCase = true) || 
                                     userPrompt.contains("new", ignoreCase = true) || 
                                     userPrompt.contains("surprise", ignoreCase = true)

            val minScoreThreshold = if (isExplicitDiscovery) 0.05 else 0.20
            val engagements = runCatching { engagementDao.getAllEngagements() }.getOrDefault(emptyList()).associateBy { it.songId }
            val now = System.currentTimeMillis()
            val decayLambda = 0.04951

            // Pre-filter candidate songs using PersonalizationMLModel
            val scoredCandidates = (candidateSongs ?: allSongs).mapNotNull { song ->
                val engagement = engagements[song.id]
                val playCount = engagement?.playCount ?: 0
                val skipCount = engagement?.skipCount ?: 0
                val completionCount = engagement?.completionCount ?: 0
                val likeStatus = engagement?.likeStatus ?: 0
                val totalInteractions = playCount + skipCount
                val skipRate = if (totalInteractions > 0) skipCount.toDouble() / totalInteractions else 0.0

                // Exclude heavily skipped tracks unless explicitly requested
                if (!isExplicitDiscovery && skipRate > 0.7 && skipCount >= 3) {
                    return@mapNotNull null
                }

                val lastPlayed = engagement?.lastPlayedTimestamp ?: 0L
                val daysAgo = if (lastPlayed > 0) maxOf(0.0, (now - lastPlayed) / (1000.0 * 60 * 60 * 24)) else 30.0
                val decayWeight = kotlin.math.exp(-decayLambda * daysAgo)

                val features = doubleArrayOf(
                    playCount * decayWeight,
                    0.5, // artist_affinity
                    0.5, // album_affinity
                    0.5, // genre_affinity
                    skipRate,
                    if (playCount > 0) completionCount.toDouble() / playCount else 0.0,
                    likeStatus.toDouble(),
                    daysAgo,
                    0.5  // remote_rank_index
                )

                val score = PersonalizationMLModel.predictEngagementProbability(features)
                if (score >= minScoreThreshold || isExplicitDiscovery) {
                    Triple(song, score, features)
                } else null
            }.sortedByDescending { it.second }.take(50) // Strictly cap pool to 50 tracks

            val candidatePool = if (scoredCandidates.isNotEmpty()) scoredCandidates else allSongs.take(50).map { Triple(it, 0.5, doubleArrayOf()) }

            // Token Optimization: Compact JSON format with normalized "s" field (0.00 - 1.00)
            val availableSongsJson = buildString {
                candidatePool.forEachIndexed { index, (song, score, _) ->
                    val formattedScore = "%.2f".format(score)
                    val title = song.title.replace("\"", "'").take(40)
                    val artist = song.displayArtist.replace("\"", "'").take(25)
                    val genre = song.genre?.replace("\"", "'")?.take(15) ?: "?"
                    if (index > 0) append(",\n")
                    append("""{"id":"${song.id}","t":"$title","a":"$artist","g":"$genre","s":$formattedScore}""")
                }
            }

            // Bring in the telemetry digest
            val isSafe = preferencesRepo.isSafeTokenLimitEnabled.first()
            val userDigest = digestGenerator.generateDigest(allSongs, isSafe)

            val fullPrompt = """
            $userDigest
            <request>
            <query>$userPrompt</query>
            <target_length>$minLength-$maxLength tracks</target_length>
            </request>
            <candidate_pool>
            [$availableSongsJson]
            </candidate_pool>
            """.trimIndent()

            val responseText = aiOrchestrator.generateContent(fullPrompt, type)

            val songIds = extractPlaylistSongIds(responseText)

            val songMap = allSongs.associateBy { it.id }
            val candidateScoreMap = candidatePool.associate { it.first.id to it.second }

            val rawGeneratedPlaylist = songIds.mapNotNull { songId ->
                songMap[songId] 
                    ?: allSongs.find { song -> 
                        song.id.endsWith(songId, ignoreCase = true) || 
                        song.id.contains(songId, ignoreCase = true) ||
                        (songId.contains(":") && song.id.substringAfterLast(":") == songId.substringAfterLast(":"))
                    }
            }

            if (rawGeneratedPlaylist.isEmpty()) {
                Result.failure(IllegalArgumentException("AI returned song IDs that don't match your library. Try again or adjust your prompt."))
            } else {
                // Re-rank LLM Output using blended score: 0.7 * localScore + 0.3 * (1 / (llmIndex + 1))
                val rerankedPlaylist = rawGeneratedPlaylist.mapIndexed { index, song ->
                    val localScore = candidateScoreMap[song.id] ?: 0.5
                    val llmRankScore = 1.0 / (index + 1)
                    val finalBlendedScore = (0.7 * localScore) + (0.3 * llmRankScore)
                    song to finalBlendedScore
                }.sortedByDescending { it.second }.map { it.first }

                Result.success(rerankedPlaylist)
            }

        } catch (e: IllegalArgumentException) {
            Result.failure(Exception(e.message ?: "AI response did not contain a valid playlist."))
        } catch (e: Exception) {
            val errorDetails = buildDetailedErrorMessage(e)
            Result.failure(Exception(errorDetails, e))
        }
    }

    /**
     * Builds a user-friendly error message from the exception chain.
     * Walks the cause chain to find the most specific error detail.
     */
    private fun buildDetailedErrorMessage(e: Exception): String {
        val rootMessage = e.message?.takeIf { it.isNotBlank() }
        val causeMessage = e.cause?.message?.takeIf { it.isNotBlank() }
        val className = e::class.simpleName ?: "Unknown"

        // Check for common error patterns
        val combinedMessages = listOfNotNull(rootMessage, causeMessage).joinToString(" → ")
        
        return when {
            combinedMessages.contains("timeout", ignoreCase = true) ||
            combinedMessages.contains("timed out", ignoreCase = true) ->
                "Request timed out. The AI provider may be slow or overloaded. Try again."
            
            combinedMessages.contains("network", ignoreCase = true) ||
            combinedMessages.contains("connect", ignoreCase = true) ||
            combinedMessages.contains("SocketException", ignoreCase = true) ||
            combinedMessages.contains("no internet", ignoreCase = true) ||
            combinedMessages.contains("offline", ignoreCase = true) ||
            combinedMessages.contains("wifi", ignoreCase = true) ->
                "No Internet Connection. Check your WiFi or mobile data and try again."

            combinedMessages.contains("airplane", ignoreCase = true) ->
                "Airplane mode is active. Please turn it off to use AI."

            combinedMessages.contains("401", ignoreCase = true) ||
            combinedMessages.contains("unauthorized", ignoreCase = true) ->
                "Permission Denied. Your API key might be invalid or restricted."

            combinedMessages.contains("403", ignoreCase = true) ||
            combinedMessages.contains("permission", ignoreCase = true) ||
            combinedMessages.contains("denied", ignoreCase = true) ||
            combinedMessages.contains("forbidden", ignoreCase = true) ->
                "Permission denied by the AI provider. Check that this API key has access to the selected model and that the provider API is enabled."
            
            combinedMessages.contains("safety", ignoreCase = true) ||
            combinedMessages.contains("blocked", ignoreCase = true) ->
                "Content was blocked by safety filters. Try rephrasing your prompt."
            
            combinedMessages.contains("model", ignoreCase = true) &&
            (combinedMessages.contains("not found", ignoreCase = true) ||
             combinedMessages.contains("unavailable", ignoreCase = true)) ->
                "The selected AI model is unavailable. Try selecting a different model in AI Settings."
            
            rootMessage != null -> "AI Error: $rootMessage"
            causeMessage != null -> "AI Error: $causeMessage"
            else -> "AI Error ($className): An unexpected error occurred. Try again."
        }
    }

    private fun extractPlaylistSongIds(rawResponse: String): List<String> {
        val sanitized = rawResponse
            .replace("```json", "")
            .replace("```", "")
            .trim()

        for (startIndex in sanitized.indices) {
            if (sanitized[startIndex] != '[') continue

            var depth = 0
            var inString = false
            var isEscaped = false

            for (index in startIndex until sanitized.length) {
                val character = sanitized[index]

                if (inString) {
                    if (isEscaped) {
                        isEscaped = false
                        continue
                    }

                    when (character) {
                        '\\' -> isEscaped = true
                        '"' -> inString = false
                    }
                    continue
                }

                when (character) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) {
                            val candidate = sanitized.substring(startIndex, index + 1)
                            val decoded = runCatching { json.decodeFromString<List<String>>(candidate) }
                            if (decoded.isSuccess) {
                                return decoded.getOrThrow()
                            }
                            break
                        }
                    }
                }
            }
        }

        throw IllegalArgumentException(
            "AI returned an invalid response format. Expected a JSON array of song IDs but got something else. " +
            "This usually happens with smaller models. Try selecting a more capable model in AI Settings."
        )
    }
}
