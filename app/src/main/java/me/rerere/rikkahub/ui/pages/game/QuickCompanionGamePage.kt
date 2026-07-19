package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventSource
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionLifeEventType
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCompanionGamePage(gameId: String) {
    val game = QuickCompanionGame.fromWireName(gameId)
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val companionRuntime = koinInject<CompanionRuntime>()
    val scope = rememberCoroutineScope()
    var selectedAssistantId by remember { mutableStateOf(settings.assistantId.toString()) }
    val selectedAssistant = settings.assistants.firstOrNull { it.id.toString() == selectedAssistantId }
        ?: settings.getCurrentAssistant()

    fun recordSharedGame(
        title: String,
        summary: String,
        detailsJson: String,
    ) {
        val assistantId = selectedAssistant.id.toString()
        val nowMillis = System.currentTimeMillis()
        scope.launch {
            companionRuntime.applyTurn(
                CompanionTurnMutation(
                    assistantId = assistantId,
                    lifeEvents = listOf(
                        CompanionLifeEvent(
                            id = "shared-game:${game.wireName}:$assistantId:$nowMillis",
                            assistantId = assistantId,
                            type = CompanionLifeEventType.GAME,
                            status = CompanionLifeEventStatus.COMPLETED,
                            title = title,
                            summary = summary,
                            source = CompanionLifeEventSource.CHAT,
                            evidenceReference = "shared-game:${game.wireName}:$nowMillis",
                            detailsJson = detailsJson,
                            importance = 3,
                            startedAt = nowMillis,
                            endedAt = nowMillis,
                            createdAt = nowMillis,
                        ),
                    ),
                    nowMillis = nowMillis,
                ),
            )
        }
    }

    Scaffold(
        containerColor = GameColors.background,
        topBar = {
            TopAppBar(
                title = { Text(game.title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("选择一起玩的角色", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(settings.assistants, key = { it.id }) { assistant ->
                            FilterChip(
                                selected = assistant.id.toString() == selectedAssistantId,
                                onClick = { selectedAssistantId = assistant.id.toString() },
                                label = { Text(assistant.name.ifBlank { "未命名角色" }) },
                            )
                        }
                    }
                    Text(
                        "对局动作由真实游戏引擎执行并写入共同经历；系统结果不会冒充角色台词。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (game) {
                QuickCompanionGame.ROCK_PAPER_SCISSORS -> RockPaperScissorsGame(
                    assistantName = selectedAssistant.name.ifBlank { "角色" },
                    onCompleted = { userMove, roleMove, outcome ->
                        recordSharedGame(
                            title = "一起玩完一轮猜拳",
                            summary = "用户选择$userMove，角色选择$roleMove，结果：$outcome。",
                            detailsJson = buildJsonObject {
                                put("game", game.wireName)
                                put("user_move", userMove)
                                put("role_move", roleMove)
                                put("outcome", outcome)
                            }.toString(),
                        )
                    },
                )
                QuickCompanionGame.DICE_DUEL -> DiceDuelGame(
                    assistantName = selectedAssistant.name.ifBlank { "角色" },
                    onCompleted = { userRoll, roleRoll, outcome ->
                        recordSharedGame(
                            title = "一起玩完一轮骰子对决",
                            summary = "用户掷出$userRoll，角色掷出$roleRoll，结果：$outcome。",
                            detailsJson = buildJsonObject {
                                put("game", game.wireName)
                                put("user_roll", userRoll)
                                put("role_roll", roleRoll)
                                put("outcome", outcome)
                            }.toString(),
                        )
                    },
                )
                QuickCompanionGame.TIC_TAC_TOE -> TicTacToeGame(
                    assistantName = selectedAssistant.name.ifBlank { "角色" },
                    onCompleted = { outcome, board ->
                        recordSharedGame(
                            title = "一起玩完一局井字棋",
                            summary = "用户执 X，角色执 O，结果：$outcome。",
                            detailsJson = buildJsonObject {
                                put("game", game.wireName)
                                put("outcome", outcome)
                                put("board", board.joinToString("") { it ?: "_" })
                            }.toString(),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun RockPaperScissorsGame(
    assistantName: String,
    onCompleted: (String, String, String) -> Unit,
) {
    val moves = listOf("石头", "剪刀", "布")
    var roleMove by remember { mutableStateOf<String?>(null) }
    var userMove by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<String?>(null) }

    fun play(move: String) {
        val role = moves.random()
        val result = compareRockPaperScissors(move, role)
        userMove = move
        roleMove = role
        outcome = result
        onCompleted(move, role, result)
    }

    GameBody(title = "猜拳", subtitle = "你先出手，$assistantName 同一轮出手。") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            moves.forEach { move ->
                OutlinedButton(onClick = { play(move) }, modifier = Modifier.weight(1f)) {
                    Text(move)
                }
            }
        }
        if (outcome != null) {
            GameResultText("你出$userMove · $assistantName 出$roleMove · $outcome")
        }
    }
}

@Composable
private fun DiceDuelGame(
    assistantName: String,
    onCompleted: (Int, Int, String) -> Unit,
) {
    var userRoll by remember { mutableIntStateOf(0) }
    var roleRoll by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<String?>(null) }

    fun roll() {
        userRoll = Random.nextInt(1, 7)
        roleRoll = Random.nextInt(1, 7)
        outcome = when {
            userRoll > roleRoll -> "用户胜"
            userRoll < roleRoll -> "角色胜"
            else -> "平局"
        }
        onCompleted(userRoll, roleRoll, outcome.orEmpty())
    }

    GameBody(title = "骰子对决", subtitle = "你和 $assistantName 各掷一次，点数高的一方获胜。") {
        if (outcome != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DiceFace(label = "你", value = userRoll)
                DiceFace(label = assistantName, value = roleRoll)
            }
            GameResultText(outcome.orEmpty())
        }
        Button(onClick = ::roll, modifier = Modifier.fillMaxWidth()) {
            Icon(if (outcome == null) HugeIcons.Play else HugeIcons.Refresh03, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (outcome == null) "掷骰子" else "再来一轮")
        }
    }
}

@Composable
private fun DiceFace(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(value.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TicTacToeGame(
    assistantName: String,
    onCompleted: (String, List<String?>) -> Unit,
) {
    var board by remember { mutableStateOf(List<String?>(9) { null }) }
    var result by remember { mutableStateOf<String?>(null) }

    fun reset() {
        board = List(9) { null }
        result = null
    }

    fun choose(cell: Int) {
        if (result != null || board[cell] != null) return
        var next = board.toMutableList().also { it[cell] = "X" }
        var winner = quickTicTacToeWinner(next)
        if (winner == null && next.any { it == null }) {
            val roleCell = chooseTicTacToeMove(next, "O")
            next = next.toMutableList().also { it[roleCell] = "O" }
            winner = quickTicTacToeWinner(next)
        }
        board = next
        if (winner != null || next.none { it == null }) {
            result = when (winner) {
                "X" -> "用户胜"
                "O" -> "角色胜"
                else -> "平局"
            }
            onCompleted(result.orEmpty(), next)
        }
    }

    GameBody(title = "井字棋", subtitle = "你执 X，$assistantName 执 O。角色会尝试取胜并阻止你的连线。") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { column ->
                        val cell = row * 3 + column
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(84.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = result == null && board[cell] == null) { choose(cell) },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    board[cell].orEmpty(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (result != null) {
            GameResultText(result.orEmpty())
            Button(onClick = ::reset, modifier = Modifier.fillMaxWidth()) {
                Icon(HugeIcons.Refresh03, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("再来一局")
            }
        }
    }
}

@Composable
private fun GameBody(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun GameResultText(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun compareRockPaperScissors(userMove: String, roleMove: String): String = when {
    userMove == roleMove -> "平局"
    (userMove == "石头" && roleMove == "剪刀") ||
        (userMove == "剪刀" && roleMove == "布") ||
        (userMove == "布" && roleMove == "石头") -> "用户胜"
    else -> "角色胜"
}

internal fun chooseTicTacToeMove(board: List<String?>, roleMark: String): Int {
    val userMark = if (roleMark == "X") "O" else "X"
    val open = board.indices.filter { board[it] == null }
    require(open.isNotEmpty()) { "No open tic-tac-toe cells" }
    return open.firstOrNull { cell ->
        board.toMutableList().also { it[cell] = roleMark }.let(::quickTicTacToeWinner) == roleMark
    } ?: open.firstOrNull { cell ->
        board.toMutableList().also { it[cell] = userMark }.let(::quickTicTacToeWinner) == userMark
    } ?: 4.takeIf { it in open }
    ?: listOf(0, 2, 6, 8).firstOrNull { it in open }
    ?: open.first()
}

internal fun quickTicTacToeWinner(board: List<String?>): String? =
    QUICK_TIC_TAC_TOE_LINES.firstNotNullOfOrNull { line ->
        board[line[0]]?.takeIf { mark -> line.all { board[it] == mark } }
    }

private val QUICK_TIC_TAC_TOE_LINES = listOf(
    listOf(0, 1, 2),
    listOf(3, 4, 5),
    listOf(6, 7, 8),
    listOf(0, 3, 6),
    listOf(1, 4, 7),
    listOf(2, 5, 8),
    listOf(0, 4, 8),
    listOf(2, 4, 6),
)

internal enum class QuickCompanionGame(
    val wireName: String,
    val title: String,
) {
    ROCK_PAPER_SCISSORS("rock_paper_scissors", "一起玩：猜拳"),
    DICE_DUEL("dice_duel", "一起玩：骰子对决"),
    TIC_TAC_TOE("tic_tac_toe", "一起玩：井字棋");

    companion object {
        fun fromWireName(value: String): QuickCompanionGame =
            entries.firstOrNull { it.wireName == value } ?: ROCK_PAPER_SCISSORS
    }
}
