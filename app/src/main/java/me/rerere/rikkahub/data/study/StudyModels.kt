package me.rerere.rikkahub.data.study

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StudyState(
    val today: String = "",
    val wallet: StudyWallet = StudyWallet(),
    val tasks: List<StudyTask> = emptyList(),
    val inventory: StudyInventory = StudyInventory(),
    val stats: StudyStats = StudyStats(),
    /** Minutes studied since the last five-minute kudos settlement. */
    val pendingRewardMinutes: Int = 0,
    val dailyPurpleDrawDate: String? = null,
    val dailyPurpleDrawCount: Int = 0,
    val dailyDrawCount: Int = 0,
    /** Number of consecutive normal results in the regular pool. */
    val drawsSinceNonNormal: Int = 0,
    val purpleSafetyGrantedDate: String? = null,
    val dailyStudyRecords: Map<String, StudyDailyRecord> = emptyMap(),
    val signInStreak: Int = 0,
    val lastSignInDate: String? = null,
    val lastStudyDate: String? = null,
    val inactiveStudyDays: Int = 0,
    val perfectStreak: Int = 0,
    val longestPerfectStreak: Int = 0,
    val lastPerfectDate: String? = null,
    val superMomentAvailable: Boolean = false,
    val superMomentClaimedDate: String? = null,
    val claimedLevelRewards: Set<Int> = emptySet(),
    val claimedAchievementIds: Set<String> = emptySet(),
    val shopDate: String? = null,
    val shopItems: List<StudyShopItem> = emptyList(),
    val purchasedShopItemIds: Set<String> = emptySet(),
    val manualShopRefreshDate: String? = null,
    val recentEvents: List<StudyEvent> = emptyList(),
    /** Daily role-confirmed sleep rewards, keyed as yyyy-MM-dd:habit. */
    val sleepHabitRewardClaims: Set<String> = emptySet(),
    val activePlanDate: String? = null,
    val generatedSchedules: Map<String, List<StudyScheduleBlock>> = emptyMap(),
    val selectedAssistantId: String? = null,
    val internalTestGrantVersion: Int = 0,
    /** One-time compensation marker for the private build's interrupted Pomodoro update. */
    val pomodoroInterruptionCompensationVersion: Int = 0,
    /** One-time compensation for the reported forty-draw all-normal experience. */
    val gachaBadLuckCompensationVersion: Int = 0,
)

@Serializable
data class StudyWallet(
    val kudos: Int = 0,
    val totalKudosEarned: Int = 0,
    val singleDrawTickets: Int = 0,
    val tenDrawTickets: Int = 0,
    val purpleDrawTickets: Int = 0,
)

@Serializable
data class StudyTask(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
    val completionRewardClaimed: Boolean = false,
    val source: StudyTaskSource = StudyTaskSource.Manual,
)

@Serializable
enum class StudyTaskSource {
    Manual,
    Plan,
}

@Serializable
data class StudyInventory(
    val normalFragments: Map<String, Int> = emptyMap(),
    val rareFragments: Map<String, Int> = emptyMap(),
    val douyinFragments: Int = 0,
    val theaterFragments: Int = 0,
    val gameFragments: Int = 0,
    val videoFragments: Int = 0,
    val animeFragments: Int = 0,
    val epicFragments: Int = 0,
    val rainbowFragments: Int = 0,
    @SerialName("specialStoryFragments")
    val legacySpecialStoryFragments: Int = 0,
    val universalNormalFragments: Int = 0,
    val universalRareFragments: Int = 0,
    val universalEpicFragments: Int = 0,
    val unlockedOutfits: Set<String> = emptySet(),
    val unlockedTheaters: Set<String> = emptySet(),
    val unopenedMysteryBoxes: List<StudyMysteryBoxReward> = emptyList(),
)

@Serializable
data class StudyStats(
    val totalPomodoros: Int = 0,
    val totalTasksCompleted: Int = 0,
    val totalStudyMinutes: Int = 0,
    val unlockedOutfitSets: Int = 0,
    val unlockedTheaters: Int = 0,
    @SerialName("mcdonaldsRedeemed")
    val videoRewardsRedeemed: Int = 0,
)

@Serializable
data class StudyDailyRecord(
    val pomodoros: Int = 0,
    val studyMinutes: Int = 0,
)

@Serializable
data class StudyEvent(
    val id: String,
    val type: StudyEventType,
    val title: String,
    val detail: String = "",
    val createdAt: Long = 0L,
)

@Serializable
enum class StudyEventType {
    SignIn,
    Habit,
    Task,
    Pomodoro,
    MysteryBox,
    Draw,
    SuperMoment,
    Level,
    Achievement,
    Shop,
    Penalty,
    Video,
    McDonalds,
    Fragment,
    Entertainment,
}

@Serializable
enum class StudySleepHabit {
    EarlySleep,
    EarlyRise,
}

@Serializable
data class StudyReward(
    val kudos: Int = 0,
    val mysteryBoxKudos: Int = 0,
    val singleDrawTickets: Int = 0,
    val tenDrawTickets: Int = 0,
    val purpleDrawTickets: Int = 0,
    val universalNormalFragments: Int = 0,
    val douyinFragments: Int = 0,
    val theaterFragments: Int = 0,
    val gameFragments: Int = 0,
    val videoFragments: Int = 0,
    val animeFragments: Int = 0,
    val universalRareFragments: Int = 0,
    val universalEpicFragments: Int = 0,
    val title: String = "",
)

@Serializable
data class StudyMysteryBoxReward(
    val kudos: Int,
    val universalNormalFragments: Int = 0,
)

data class StudyActionResult(
    val state: StudyState,
    val reward: StudyReward = StudyReward(),
)

data class StudySleepRewardResult(
    val state: StudyState,
    val granted: Boolean,
    val reason: String,
    val reward: StudyReward = StudyReward(),
    val alreadyClaimed: Boolean = false,
)

data class StudyTimeOverview(
    val todayMinutes: Int = 0,
    val todayPomodoros: Int = 0,
    val weekMinutes: Int = 0,
    val weekPomodoros: Int = 0,
)

data class StudyDrawActionResult(
    val state: StudyState,
    val results: List<StudyDrawResult>,
)

@Serializable
data class StudyDrawResult(
    val rarity: StudyRarity,
    val fragmentKey: String,
    val title: String,
    val fragmentType: StudyFragmentType? = null,
    /** This draw is shown, but its blue fragment collection was already full. */
    val alreadyFull: Boolean = false,
)

@Serializable
enum class StudyRarity(val label: String) {
    Normal("普通"),
    Rare("紫色"),
    Epic("金色"),
    Rainbow("彩色"),
}

@Serializable
enum class StudyFragmentType(val label: String, val rarity: StudyRarity) {
    Douyin("抖音时长券 · 20分钟", StudyRarity.Rare),
    Theater("剧场碎片", StudyRarity.Rare),
    Game("游戏畅玩券 · 120分钟", StudyRarity.Epic),
    Video("视频解锁卡", StudyRarity.Epic),
    Anime("番剧兑换券 · 3小时", StudyRarity.Rainbow),
}

enum class StudyEntertainmentReward(val label: String) {
    Douyin("抖音时长券"),
    Theater("小剧场"),
    Game("游戏畅玩券"),
    Video("视频"),
    Anime("番剧兑换券"),
}

@Serializable
enum class SuperMomentChoice {
    NormalFragments,
    RareFragment,
}

@Serializable
data class StudyLevel(
    val level: Int,
    val threshold: Int,
    val title: String,
    val reward: StudyReward,
)

@Serializable
data class StudyAchievement(
    val id: String,
    val title: String,
    val condition: String,
    val reward: StudyReward,
)

@Serializable
data class StudyShopItem(
    val id: String,
    val type: StudyShopItemType,
    val title: String,
    val price: Int,
)

@Serializable
enum class StudyShopItemType {
    UniversalNormalFragment,
    DouyinFragment,
    TheaterFragment,
    GameFragment,
    VideoFragment,
    AnimeFragment,
    SingleDrawTicket,
}
