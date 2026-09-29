package com.rollercoin.mobile

data class GameDefinition(
    val number: Int,
    val name: String,
    val defaultTimeSeconds: Int,
    val winStatus: Int = 3,
)

data class GameState(
    val definition: GameDefinition,
    val cooldownSeconds: Int = 0,
    val level: Int = 1,
)

data class Profile(
    val name: String,
    val email: String,
    val id: String,
    val active: Boolean,
    val premium: Boolean?,
    val miners: Int?,
    val racks: Int? = null,
    val maxPower: Int?,
    val leagueId: String,
    val registration: String,
    val publicProfileLink: String,
    val avatarUrl: String = "",
    val rank: Long? = null,
    val bonusPowerPercent: Double? = null,
    val referrals: Int? = null,
    val currentLevel: Int? = null,
    val experience: Long? = null,
)

data class PowerInfo(
    val total: Long,
    val penalty: Long,
    val gamesPower: Long = 0L,
    val minersPower: Long = 0L,
    val bonusPower: Long = 0L,
    val racksPower: Long = 0L,
) {
    val net: Long get() = (total - penalty).coerceAtLeast(0L)
}

data class PcConfig(
    val name: String,
    val level: Int,
    val maxLevel: Int,
    val gamesToNextLevel: Int,
    val powerHoldingDays: Int,
    val multiplier: String?,
    val expireDate: String?,
)

data class WalletCurrency(
    val code: String,
    val name: String,
    val balance: Double = 0.0,
    val toSmall: Double = 1.0,
    val precision: Int = 8,
    val symbol: String = "",
    val iconUrl: String = "",
)

data class Dashboard(
    val profile: Profile?,
    val power: PowerInfo?,
    val pcConfig: PcConfig?,
    val currencies: List<WalletCurrency>,
    val games: List<GameState>,
)

data class ActiveGame(
    val userGameId: String,
    val game: GameState,
    val targetPower: Int,
    val totalDurationSeconds: Int,
    val startTimeMs: Long = System.currentTimeMillis(),
)

data class OtpRequest(
    val userId: String,
    val codeId: String,
)

data class FinishedGame(
    val gameNumber: Int,
    val gameName: String,
    val actualPower: Int,
    val status: String,
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class BotLog(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO,
)

object GameCatalog {
    val games = listOf(
        GameDefinition(1, "Coinclick", 40),
        GameDefinition(2, "Token Blaster", 40),
        GameDefinition(3, "Flappy Rocket", 30),
        GameDefinition(4, "Cryptonoid", 60),
        GameDefinition(5, "Coin-match", 70),
        GameDefinition(6, "Crypto Hamster", 40),
        GameDefinition(7, "2048 Coins", 40),
        GameDefinition(8, "Hash Rush", 60),
        GameDefinition(9, "Dr.Hamster", 60),
        GameDefinition(10, "Token Surfer: Snow Ride", 60),
        GameDefinition(11, "CryptoMania", 60),
        GameDefinition(12, "Hamster Climber", 60),
        GameDefinition(13, "Coin Fisher", 60),
        GameDefinition(14, "Mission Hamspossible", 60),
        GameDefinition(15, "Crypto Hex", 60),
    )

    // Accurate rewards from official RollerCoin JS bundle table `_e`
    private val rewards = mapOf(
        1 to intArrayOf(6000, 6000, 6000, 6000, 7200, 7200, 7200, 7200, 8400, 8400),
        2 to intArrayOf(32760, 36120, 38610, 42000, 44805, 47700, 50310, 53235, 56160, 70200),
        3 to intArrayOf(11880, 12960, 13650, 14700, 15750, 16800, 17850, 18900, 19950, 21000),
        4 to intArrayOf(33840, 34560, 36000, 37440, 38160, 39600, 54720, 55680, 57600, 58560),
        5 to intArrayOf(10800, 10980, 11160, 13230, 13440, 13650, 13860, 14070, 14280, 15630),
        6 to intArrayOf(30450, 32460, 34530, 36660, 38850, 41130, 43440, 45840, 48270, 50760),
        7 to intArrayOf(5040, 5355, 5670, 5985, 6300, 6615, 6930, 7245, 7560, 7875),
        8 to intArrayOf(5760, 5760, 5760, 7680, 8040, 8040, 8040, 10050, 10050, 10050),
        9 to intArrayOf(15060, 16470, 17790, 19020, 20130, 21150, 22050, 22920, 23670, 24330),
        10 to intArrayOf(17640, 19110, 20580, 22050, 23520, 24990, 26460, 27930, 29400, 30870),
        11 to intArrayOf(32640, 34875, 37125, 39360, 41595, 43845, 46080, 48315, 50565, 52800),
        12 to intArrayOf(12750, 14850, 16650, 18600, 20400, 22350, 24300, 26250, 28200, 30000),
        13 to intArrayOf(12750, 14850, 16650, 18600, 20400, 22350, 24300, 26250, 28200, 30000),
        14 to intArrayOf(35000, 40000, 45000, 50000, 55000, 60000, 65000, 70000, 75000, 80000),
        15 to intArrayOf(12750, 14850, 16650, 18600, 20400, 22350, 24300, 26250, 28200, 30000),
    )

    private val levelTimes = mapOf(
        13 to intArrayOf(40, 35, 35, 30, 25, 25, 25, 25, 25, 25),
        14 to intArrayOf(45, 45, 40, 40, 35, 35, 30, 30, 25, 25),
        15 to intArrayOf(30, 34, 38, 42, 46, 50, 54, 58, 62, 70),
    )

    fun level(state: GameState): Int = state.level.coerceIn(1, 10)

    fun reward(state: GameState): Int =
        rewards[state.definition.number]?.getOrNull(level(state) - 1) ?: 1000

    fun duration(state: GameState): Int =
        levelTimes[state.definition.number]?.getOrNull(level(state) - 1)
            ?: state.definition.defaultTimeSeconds
}
