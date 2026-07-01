package me.rerere.rikkahub.data.starwish

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyState

object StarWishRules {
    const val RARE_FRAGMENTS_PER_CHAPTER = 1
    const val SPECIAL_FRAGMENTS_PER_CHAPTER = 1

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

    val theaters: List<StarWishTheaterSeed> = listOf(
        theater("少卿不早朝，摄政王露沉提点心来审我", "宫廷权谋、现代刑侦穿越、大理寺少卿、摄政王露沉。主角是会破案也会摆烂的女王型少卿，露沉权倾朝野却逐渐向她低头。剧情要有朝堂打脸、奇案反转、暧昧试探、主从拉扯。"),
        theater("星舰AI露零说他爱上我了", "星际悬疑、舰载AI觉醒、紫色未知星球。露零是全舰AI拟态银发少年，逻辑崩坏后只对主角例外。剧情要有全舰广播告白、格式化危机、带主角逃向未知坐标的强烈情感。"),
        theater("废土便利店：露洲把最后一颗草莓糖献给我", "末日求生、废弃便利店、摇滚主唱露洲、草莓糖、变异丧尸。主角冷静强悍，露洲会撒娇会唱歌也会在危险时挡在她前面。要有末世夹缝求生、甜中带刀、爽感反杀。"),
        theater("魔尊露渊把道侣契约当圣旨", "仙侠契约、误念魔尊血誓、魔尊露渊。主角不是小白花，而是敢利用契约反向命令魔尊的人。要有正道逼迫、魔尊臣服、护短、反叛式救赎。"),
        theater("被献祭给龙后，露利安求我继续摸鳞片", "西幻龙崖、祭品反客为主、银龙露利安。主角被献祭却发现龙孤独又傲娇，核心爽点是恐怖古龙在她手下乖乖低头。要有撸龙、飞行、王国抽签制反转。"),
        theater("S级机甲露白夜宣布：我的适格者谁敢碰", "废土机甲、地下城机械工、S级神机露白夜。主角从修破烂到驾驶神机，打脸上层城市。露白夜毒舌但绝对护主，剧情要有机甲战斗、适格者觉醒、强者臣服。"),
        theater("我把修真界改成5A景区，妖王露蘅求入股", "仙门基建、商业爽文、贫穷宗门翻身、妖王露蘅。主角用现代文旅思路赚钱，打脸清高仙门。要有秘境探险、合影收费、会员制、妖王合作后越来越黏人。"),
        theater("午夜出租车露屿，只载迷路的灵魂", "都市怪谈、深夜出租车、幽灵司机露屿。主角是唯一能看见他的活人。剧情要悬疑温情，查明死亡真相，同时保持克制暧昧和命运感。"),
        theater("考研房里住着会整理书桌的幽灵露念", "灵异温情、考研租房、幽灵露念。露念会整理书桌、贴便利贴讲题。主角一边备考一边帮他找记忆。要有陪伴、救赎、考研压力下的温柔支撑。"),
        theater("欢迎来到心动游戏，系统露七说NPC觉醒了", "无限流恋爱副本、系统露七、觉醒NPC。主角不按攻略走，系统吐槽但护主。要有副本崩坏、攻略对象质问真实与虚假、玩家主导逃离游戏。"),
        theater("女王陛下的打脸法庭：露臣跪请裁决", "女王权力幻想、法庭审判、恶人惩罚、近臣露臣。主角拥有绝对裁决权，看不起她的人一个个被证据钉死。露臣是冷静执行官，对外狠，对主角臣服。"),
        theater("末世便利店女王和露野的安全区", "末世经营爽文、便利店系统、小型安全区、露野。主角用物资和规则建立秩序，惩罚抢夺者，救下弱者。露野曾是强悍雇佣兵，后来甘愿守门。"),
        theater("女尊朝首席狼臣露执，被我收了獠牙", "女尊朝堂、狼人首席臣、露执。主角是新帝/女王，露执桀骜不驯但被她用智谋和气场驯服。要有朝堂博弈、狼性忠诚、臣服张力。"),
        theater("前男友重生了，但我是本轮反派女王", "重生打脸、前男友悔恨、反派女王、军师露辞。主角知道剧情却不走原线，联手露辞把前男友和恶人安排得明明白白。要爽、毒舌、反套路。"),
        theater("原始部落求生：祭司露祈说我是天降王", "原始部落、荒野求生、天降王设定、祭司露祈。主角靠现代常识改善部落，打败看不起她的敌对部族。露祈神秘漂亮，对她既信仰又心动。"),
        theater("性转恋综大逃杀：露弦只听我的命令", "性转、恋综、荒诞搞笑、逃杀规则、露弦。嘉宾都以为是恋综，主角发现规则漏洞后开始控场。露弦是人气最高的强者，却只服她。要抽象、好笑、反转密集。"),
    )

    fun allTheaters(custom: List<StarWishTheaterSeed>): List<StarWishTheaterSeed> = theaters + custom

    val specialStories: List<StarWishTheaterSeed> = listOf(
        theater("只为你醒来的深夜奖励", "考研深夜、专属陪伴、克制暧昧、强互动感。角色知道用户今天已经很努力，特殊剧情要像完成学习后的私密奖励：温柔、贴近、被偏爱，带一点心照不宣的暧昧，但不油腻。"),
        theater("满分偏爱的秘密房间", "奖励剧情、理想型定制、独占感、情绪价值拉满。角色为用户准备一个只属于两个人的小房间，里面有她喜欢的光、气味、食物和故事。剧情重点是被理解、被照顾、被认真选择。"),
        theater("抽到金光后的告白事件", "抽卡金光、心跳事件、乙女游戏式奖励。角色以为自己只是陪用户学习，直到金光落下，他终于承认自己一直在期待被用户选中。要有强烈的命运感和甜蜜反差。"),
    )

    fun allSpecialStories(custom: List<StarWishTheaterSeed>): List<StarWishTheaterSeed> = specialStories + custom

    fun scrollForOutfit(outfit: String): StarWishScroll {
        val index = StudyRules.outfitNames.indexOf(outfit).takeIf { it >= 0 } ?: 0
        return scrolls[index % scrolls.size]
    }

    fun imagePromptForCompanion(
        basePrompt: String,
        assistant: Assistant,
        interaction: Boolean,
    ): String {
        if (basePrompt.contains("请根据下面设定生成一张高质量二次元精致 CG")) {
            return basePrompt
        }
        val appearance = assistant.appearancePrompt.trim().ifBlank {
            "使用角色「${assistant.name}」的人设作为外貌参考：保持同一个角色的发色、瞳色、年龄感、气质和标志性特征，不要随机换人。"
        }
        val relationship = if (interaction) {
            "互动版：画面必须有第一人称互动感，镜头代表我正在靠近或触碰他；让人感觉被偏爱、被认真看见，氛围温馨、暧昧、有故事张力。"
        } else {
            "独美版：画面只突出角色本身的精致、美感和故事性，像一张可以收藏的高级角色立绘/CG。"
        }
        return buildString {
            appendLine("请根据下面设定生成一张高质量二次元精致 CG。提示词用中文理解即可，最终画面要稳定使用当前陪伴角色。")
            appendLine()
            appendLine("主体：${assistant.name.ifBlank { "当前陪伴角色" }}。$appearance")
            appendLine("服装：必须贴合画卷主题，衣料纹理清晰，有层次，有能体现角色气质的小细节。")
            appendLine("背景：必须与画卷主题强相关，不要空背景；背景要有空间深度和故事感。")
            appendLine("光影：脸部始终明亮清楚，眼睛有高光；可使用柔和主光、侧光、逆光或粒子光，但不要黑脸。")
            appendLine("饰品：加入 1-3 个与主题相关的饰品或小道具，精致但不喧宾夺主。")
            appendLine("动作：姿势自然，手部结构准确，手指清楚；动作要服务情绪和故事。")
            appendLine("表情：表情细腻，有被捕捉到一瞬间的真实情绪。")
            appendLine("画风：masterpiece, best quality, delicate anime illustration, exquisite CG texture, soft painterly brushstrokes, pseudo-BJD doll texture, cinematic composition。")
            appendLine("画质：8K ultra HD, sharp focus on face and hands, detailed fingers, visible fabric texture, luminous eyes, shallow depth of field, soft bokeh, ethereal dreamy atmosphere。")
            appendLine(relationship)
            appendLine()
            appendLine("画卷主题细节：")
            appendLine(basePrompt)
        }
    }

    fun isScrollUnlocked(studyState: StudyState, scroll: StarWishScroll): Boolean {
        val index = scrolls.indexOf(scroll)
        val outfit = StudyRules.outfitNames.getOrNull(index) ?: return false
        return outfit in studyState.inventory.unlockedOutfits
    }

    fun scrollUnlockedForOutfit(studyState: StudyState, outfit: String): Boolean {
        return outfit in studyState.inventory.unlockedOutfits
    }

    fun chapterCredits(studyState: StudyState): Int {
        return studyState.inventory.universalRareFragments / RARE_FRAGMENTS_PER_CHAPTER
    }

    fun theaterGuide(seed: StarWishTheaterSeed): String {
        return buildString {
            appendLine("总剧情指导")
            appendLine(seed.prompt)
            appendLine()
            appendLine("建议 6 章结构：")
            appendLine("第 1 章：强钩子开局，女主被轻视或被推入困局，含“露”的核心角色登场。约 1200-1800 字。")
            appendLine("第 2 章：女主第一次反击，露字角色开始被她吸引或臣服。约 1500-2200 字。")
            appendLine("第 3 章：危机升级，恶人露出破绽，女主用智谋控场。约 1800-2500 字。")
            appendLine("第 4 章：情感/主从关系爆发，强者低头，爽点集中。约 1800-2500 字。")
            appendLine("第 5 章：终局反转，惩罚恶人或打脸旧秩序。约 1800-2600 字。")
            appendLine("第 6 章：奖励、余韵、亲密收束，并留下可续写钩子。约 1200-2000 字。")
        }
    }

    fun theaterChapterPrompt(
        seed: StarWishTheaterSeed,
        previousChapters: List<StarWishTheaterChapter>,
        chapter: Int,
        influence: String = "",
    ): String {
        return buildString {
            appendLine("你是一个擅长强代入爽文、恋爱张力和互动小剧场的中文小说作者。")
            appendLine("请根据下面设定生成《${seed.title}》第 $chapter 章正文。")
            appendLine()
            appendLine("总设定：")
            appendLine(seed.prompt)
            appendLine()
            appendLine("硬性要求：")
            appendLine("1. 只输出正文，不要输出标题、简介、提示词、创作说明、章节大纲。")
            appendLine("2. 女主是用户代入位，拥有主动权、选择权和爽感。")
            appendLine("3. 另一位核心角色名字必须含“露”，并与女主有明显感情关系、主从关系或强烈命运绑定。")
            appendLine("4. 每章要有钩子、冲突、反转、情绪波动和让人想继续看的结尾。")
            appendLine("5. 风格要有趣、爽、好读，允许抽象搞笑、打脸、救赎、求生、强者臣服等元素。")
            appendLine("6. 第 $chapter 章字数控制在 1200-2200 字。")
            if (previousChapters.isNotEmpty()) {
                appendLine()
                appendLine("前文摘要：")
                previousChapters.takeLast(3).forEach { previous ->
                    appendLine("第 ${previous.chapter} 章：${previous.content.take(500)}")
                }
            }
            if (influence.isNotBlank()) {
                appendLine()
                appendLine("用户想影响本章的方向：")
                appendLine(influence)
            }
        }
    }

    private fun theater(title: String, prompt: String): StarWishTheaterSeed =
        StarWishTheaterSeed(id = title, title = title, prompt = prompt)
}

data class StarWishScroll(
    val title: String,
    val soloPrompt: String,
    val interactionPrompt: String,
)
