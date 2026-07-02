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
    val activePlanDate: String? = null,
    val generatedSchedules: Map<String, List<StudyScheduleBlock>> = emptyMap(),
    val selectedAssistantId: String? = null,
    val internalTestGrantVersion: Int = 0,
)

@Serializable
data class StudyWallet(
    val kudos: Int = 0,
    val totalKudosEarned: Int = 0,
    val singleDrawTickets: Int = 0,
    val tenDrawTickets: Int = 0,
)

@Serializable
data class StudyTask(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
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
    val epicFragments: Int = 0,
    val specialStoryFragments: Int = 0,
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
}

@Serializable
data class StudyReward(
    val kudos: Int = 0,
    val mysteryBoxKudos: Int = 0,
    val singleDrawTickets: Int = 0,
    val tenDrawTickets: Int = 0,
    val universalNormalFragments: Int = 0,
    val universalRareFragments: Int = 0,
    val universalEpicFragments: Int = 0,
    val specialStoryFragments: Int = 0,
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

data class StudyDrawActionResult(
    val state: StudyState,
    val results: List<StudyDrawResult>,
)

@Serializable
data class StudyDrawResult(
    val rarity: StudyRarity,
    val fragmentKey: String,
    val title: String,
)

@Serializable
enum class StudyRarity(val label: String) {
    Normal("普通"),
    Rare("稀有"),
    Epic("史诗"),
    Rainbow("彩色"),
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
    UniversalRareFragment,
    UniversalEpicFragment,
    SingleDrawTicket,
}
