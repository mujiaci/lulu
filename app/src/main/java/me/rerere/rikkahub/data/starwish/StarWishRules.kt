package me.rerere.rikkahub.data.starwish

import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyState

object StarWishRules {
    val scrolls: List<StarWishScroll> = listOf(
        StarWishScroll(
            title = "星穹图书馆",
            soloPrompt = "他穿着深蓝与银白交织的学士短袍，领口别着星轨胸针。微卷短发在零重力中柔软飘浮。盘腿坐在悬浮书页上，膝盖摊开发光的书，书页的光映在他专注的侧脸上。银河自穹顶倾泻。他微微歪头，嘴角带着“我懂了”的得意小表情，手指轻轻点在书页某一行上。长睫毛投下细碎的光影。",
            interactionPrompt = "画面是过肩视角。前景虚化的是你的肩膀和一点发梢。他正从书里抬起头来，视线越过书页看向镜头，也就是看向你。他的眼睛微微眯起，带着一种“你偷偷看我多久了”的笑意。你的手从画面右下方伸入，食指轻轻戳在他的脸颊上，他的脸颊被戳出一个浅浅的小窝。他没躲，只是眼睛弯成了月牙。书页的光从下方照亮他的脸，而你的手背上有银河的倒影。张力点在指尖与脸颊的接触。",
        ),
        StarWishScroll(
            title = "樱吹雪剑道场",
            soloPrompt = "纯白剑道服，袖口天蓝细带，腰间系带随风微扬。短发利落，后脑勺翘着一小撮呆毛。持木刀立于樱花雨中，刀尖点地，身姿挺拔如松。花瓣落在他肩头、发顶。闭眼，深呼吸，表情是极致的专注与平静。晨光穿透花隙，在他身后拉出长长的影子。",
            interactionPrompt = "他正握着木刀示范一个动作，一只手握着刀柄，另一只手向前伸出，而这只手的手腕被你的手轻轻握住。画面是你的第一人称视角：你站在他对面，你的手扣在他露出的手腕上，能感受到他皮肤的温度。他被你突然的接触弄得愣了一下，木刀差点脱手。他低头看着你握他的手，耳根微微泛红，嘴唇微张，表情介于“你在干嘛”和“不要松开”之间。樱花瓣停在你们交握的手上。张力点在手腕的脉动与樱花落下的瞬间静止。",
        ),
        StarWishScroll(
            title = "深海回廊",
            soloPrompt = "水蓝渐变短袍，衣摆如海葵飘动。短发在水中散开，发梢缀夜光珠。悬浮在沉没回廊中央，双手插兜，一条腿微曲，像在无重力地发呆。头顶鲸影滑过，气泡慢悠悠上升。他闭着一只眼，睁开的那只眼瞳映出幽蓝光点，神情放空而宁静。",
            interactionPrompt = "画面是仰视角度，仿佛你正从水中向上看他。他悬浮着，正低下头，伸出食指，指尖即将碰到你的手。你的手从画面底部伸入，五指张开，朝向他的方向，隔着最后一点点距离没有触碰。气泡在你们的手之间上升。他的头发飘散，你的衣袖在水中浮动。张力点就在那最后一点距离，是谁先碰到谁。",
        ),
        StarWishScroll(
            title = "永夜花庭",
            soloPrompt = "黑色短款斗篷，内搭白色立领衬衫，领口系细丝带领结。短发梳得整齐，刘海微遮一只眼。坐在古老庭院石阶上，双手托腮看着膝盖上一朵发光的白色昙花。四周全暗，只有他和花是亮的。神情是小心翼翼的珍视。",
            interactionPrompt = "画面里你的手和他一起捧着那朵发光的昙花。他托着花的下方，你的手覆在他的手背上，手指微微交叠。他低着头看花，而你坐在他对面，只露出从手腕到指尖的那一小截。光从花心照亮你们交叠的手指，也照亮他低垂的睫毛。他的呼吸很轻，怕惊到花，也怕惊到你。张力点在两只手共同守护的脆弱光。",
        ),
        StarWishScroll(
            title = "云上列车",
            soloPrompt = "复古飞行员短夹克，过大格子围巾尾巴在风中飘。短发被风吹乱，贝雷帽有点歪。一手提旧皮箱，另一只手伸出去接天空飘下的光絮。站在云海月台上，身后蒸汽列车进站。他回头看车，眼睛亮亮的，露出虎牙笑。",
            interactionPrompt = "他正要上列车，车厢门开着。他站在车门台阶上，身体一半在车里一半在外面，回头朝你伸出手，掌心朝上，等着你把手放上去。画面是你的第一人称视角：你的手刚抬起来，指尖离他的掌心还有几厘米。他的围巾被风吹起，飘向你的方向，几乎要碰到你的手腕。他的表情是“快上来”，带着一点着急和很多的期待。张力点在那只等待的手。",
        ),
        StarWishScroll(
            title = "琉璃沙漠",
            soloPrompt = "宽松沙色旅人短袍，腰间系琉璃珠串。短发用头巾松松包住，几缕碎发逃出。蹲在琉璃碎片般的沙漠上，用指尖碰一块折射虹彩的碎片。落日把他的侧脸染成暖橘色，他眯眼看向远方琉璃沙丘，表情安静而倔强。",
            interactionPrompt = "他蹲在琉璃沙漠里，你站在他身后，弯下腰，把下巴搁在他头顶。画面里看不到你的脸，只看到你的双手从他的肩膀上方垂下来，在他胸前比了一个歪歪扭扭的心形。他低头看着你手指比的心，嘴角忍不住翘起来，但又在努力忍住，表情是“好蠢但是好开心”。落日在你们身后拉出叠在一起的影子。张力点在忍住的笑意和头顶的重量。",
        ),
        StarWishScroll(
            title = "机械蝴蝶",
            soloPrompt = "复古铜色短马甲配白衬衫，袖口卷到手肘。肩上停着巨大机械蝴蝶，彩色玻璃翅膀轻轻振翅。短发有一缕铜金色挑染。他偏头看着蝴蝶，伸出一根手指想碰又不敢碰，表情是小心翼翼的惊喜。",
            interactionPrompt = "机械蝴蝶飞起来了，停在你伸出的食指上。画面里你和他面对面，你的手指上停着蝴蝶，他的手指从对面伸过来，轻轻碰了碰蝴蝶的翅膀边缘。你们的手指在蝴蝶两侧，隔着一对翅膀的距离，指尖对指尖。他透过蝴蝶翅膀的彩色玻璃看向你，眼神被染成斑斓的颜色。张力点在蝶翼两侧即将触碰的指尖。",
        ),
        StarWishScroll(
            title = "月光浴场",
            soloPrompt = "宽松月白浴衣，衣襟微敞露出锁骨。短发半湿贴在额前。坐在温泉边岩石上，赤脚浸在水中，脚尖点水荡开发光的涟漪。抬头看满月，表情是洗完澡后的放松餍足，眼睛半眯，嘴角弯弯。",
            interactionPrompt = "水面倒映出两个人的影子。他坐在温泉边，你坐在他旁边靠着他肩膀睡着了。画面只拍水中的倒影：他的倒影偏着头，看着靠在自己肩上的你的倒影，一动不敢动，表情是僵住的、耳朵红透的可爱慌张。他的手指在水下悄悄揪着自己的浴衣下摆。天上的满月在水面碎成千万片银光。张力点在他不敢动的那份小心。",
        ),
        StarWishScroll(
            title = "废墟花园",
            soloPrompt = "做旧灰绿色工装外套，口袋里插着小花铲。短发有些乱，脸上蹭了一道泥印。蹲在废墟断壁下的野花丛前，小心翼翼地给一株小苗浇水。阳光从断壁裂缝照下，落在他和小苗上。他看着小苗的眼神像在看最珍贵的东西。",
            interactionPrompt = "你和他一起蹲在花丛前，他正握着你的手，教你怎么把种子放进土里。画面是你低头看到的视角：你的手心里躺着几颗种子，他的手指轻轻拨开你蜷起的手指，像在拆一份很珍贵的礼物。他的手指上有泥土，你的手心里有种子。他低着头很认真地操作，睫毛几乎要扫到你的手心。阳光照在你们交叠的手上。张力点在他手指在你掌心的触感。",
        ),
        StarWishScroll(
            title = "倒悬都市",
            soloPrompt = "深灰与荧光蓝的机能风短外套。短发有静电飘起效果。倒立悬浮在空中城市的天桥上，双手插兜，身体微仰，从下往上看镜头。头顶是颠倒的摩天楼灯火，脚下是翻涌云海。露出狡黠笑脸，眼睛弯成月牙，比一个小小的 V 字。",
            interactionPrompt = "重力颠倒，你们都倒悬着。他悬浮在你对面，他的手紧紧抓着你的手腕，防止你飘走。画面里他的手臂是唯一的锚点。他的头发倒竖着飘，你的发梢从画面底部侵入视野。他抓着你的手腕，表情却是笑着的，像在说“别怕我抓住你了”。你们身后是倒悬的城市灯光，所有的光都在往下，其实是往上流淌。张力点在他抓着你手腕的力量和他脸上的笑之间的反差。",
        ),
    )

    val theaters: List<String> = StudyRules.theaterNames

    fun scrollForOutfit(outfit: String): StarWishScroll {
        val index = StudyRules.outfitNames.indexOf(outfit).takeIf { it >= 0 } ?: 0
        return scrolls[index.coerceIn(scrolls.indices)]
    }

    fun isScrollUnlocked(studyState: StudyState, scroll: StarWishScroll): Boolean {
        val index = scrolls.indexOf(scroll)
        val outfit = StudyRules.outfitNames.getOrNull(index) ?: return false
        return outfit in studyState.inventory.unlockedOutfits
    }

    fun isTheaterUnlocked(studyState: StudyState, theater: String): Boolean {
        return theater in studyState.inventory.unlockedTheaters
    }

    fun chapterCredits(studyState: StudyState, theater: String): Int {
        return (studyState.inventory.rareFragments["rare:$theater"] ?: 0) / 5
    }

    fun defaultTheaterChapter(theater: String, chapter: Int): String {
        return "第 $chapter 章 · $theater\n\n这里会保存你为「$theater」生成的小剧场。当前版本先为你建立章节记录；后续接入剧场生成后，会按已有章节继续往下写。"
    }
}

data class StarWishScroll(
    val title: String,
    val soloPrompt: String,
    val interactionPrompt: String,
)
