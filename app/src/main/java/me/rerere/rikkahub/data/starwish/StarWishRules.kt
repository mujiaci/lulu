package me.rerere.rikkahub.data.starwish

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyState
import kotlin.random.Random

object StarWishRules {
    const val RARE_FRAGMENTS_PER_CHAPTER = 1
    const val SPECIAL_FRAGMENTS_PER_CHAPTER = 1
    const val VIDEO_FRAGMENTS_PER_UNLOCK = 1

    val scrolls: List<StarWishScroll> = listOf(
        starWishScroll(
            title = "星穹学士馆",
            solo = """
                发型是微卷短发，在零重力中轻轻飘起。服装是深蓝与银白交织的幻想学士短袍，衣摆绣星轨纹，白衬衫和细银边领结，衣料有丝绒、金属线和透明纱层次。饰品是星轨胸针、银色书签链、悬浮羽毛笔。背景是透明穹顶星空图书馆，银河从穹顶倾泻，悬浮书页、高耸书架和透明星图仪形成前景、中景、远景。姿势是盘腿坐在发光书页上，膝上摊开古书，手指点在某一行文字。表情是“我懂了”的得意浅笑，长睫毛投下细碎光影。光影是书页暖光照亮脸、眼睛和手，星光逆光勾出发丝。画风是梦幻学院厚涂二次元 CG，delicate anime illustration, soft painterly brushstrokes。画质是 8K ultra HD, sharp focus on face and hands, detailed fingers, visible fabric texture, luminous eyes, shallow depth of field, soft bokeh。
            """,
            interaction = """
                发型是微卷短发，在零重力中轻轻飘起。服装是深蓝与银白交织的幻想学士短袍，衣摆绣星轨纹，白衬衫和细银边领结，衣料有丝绒、金属线和透明纱层次。饰品是星轨胸针、银色书签链、悬浮羽毛笔。背景是透明穹顶星空图书馆，银河从穹顶倾泻，悬浮书页、高耸书架和透明星图仪形成空间深度。姿势是第一视角情侣互动，我坐在露露面前，画面只露出我的手和一点肩膀；露露靠近镜头，把发光古书推到我怀里，一只手牵住我的手指点向答案，另一只手撑在书页旁。表情聪明又亲昵，眼神亮亮的，像恋人低声说“再想一下，你可以的”。光影是书页暖光照亮他的脸、眼睛、指尖和我被牵住的手，背景星河柔焦。画风是梦幻学院厚涂二次元 CG，delicate anime illustration, soft painterly brushstrokes。画质是 8K ultra HD, sharp focus on face and interlocked fingers, detailed fingers, visible fabric texture, luminous eyes, soft bokeh。
            """,
        ),
        starWishScroll(
            title = "雨夜车站",
            solo = """
                发型是被雨气压得微湿的短发，额前有几缕碎发。服装是墨蓝长风衣、银灰内搭和深色围巾，风衣有细雨纹暗绣，布料呈现湿润但高级的质感。饰品是透明长柄伞、银色耳扣、旧车票夹。背景是雨夜旧车站，铁轨、路灯、远处列车灯和湿漉漉站台形成空间深度。姿势是单手撑伞站在站台边，另一手夹着车票，微微低头看雨。表情像在等待重要的人，安静又带一点期待。光影是路灯暖光照亮脸，雨幕冷蓝反光补出轮廓。画风是湿润胶片日系二次元 CG，cinematic anime illustration, soft film grain。画质是 8K, clear face, detailed wet fabric, sharp hands, rain reflection, shallow depth of field。
            """,
            interaction = """
                发型是被雨气压得微湿的短发，额前有几缕碎发。服装是墨蓝长风衣、银灰内搭和深色围巾，风衣有细雨纹暗绣，布料呈现湿润但高级的质感。饰品是透明长柄伞、银色耳扣、旧车票夹。背景是雨夜旧车站，路灯和列车灯在湿地面拉出长倒影，雨幕形成柔焦层次。姿势是第一视角共伞拉近，我站在雨里，画面边缘只露出我的手腕；露露把透明伞偏向我，另一只手握住我的手腕把我拉进伞下，肩膀贴得很近。表情有点委屈又温柔，像恋人抱怨“怎么现在才来”。光影是伞下暖光照亮他的脸、眼睛和握住我的手，雨夜蓝光做边缘光。画风是湿润胶片日系二次元 CG，cinematic anime illustration, soft film grain。画质是 8K, sharp focus on face and hands, detailed fingers, rain bokeh, visible coat texture。
            """,
        ),
        starWishScroll(
            title = "海底潮汐书馆",
            solo = """
                发型是柔软侧分短发，发尾像被水流托起。服装是海盐蓝短袍和银白半透明披肩，袖口有潮汐纹，衣摆轻薄如海雾。饰品是贝壳星砂耳饰、珍珠书扣、小玻璃瓶。背景是半透明海底图书馆，蓝色书架向深处延伸，窗外有鱼群、气泡和漂浮水光。姿势是坐在浅水石阶上，翻开一本浮出水纹文字的书。表情清澈专注，像刚读懂海底秘密。光影是水面折射光照亮脸、眼睛和手。画风是透明水彩梦幻二次元 CG，transparent watercolor anime CG, dreamy ocean library。画质是 8K, sharp face and hands, detailed fingers, glowing water ripples, soft bokeh。
            """,
            interaction = """
                发型是柔软侧分短发，发尾像被水流托起。服装是海盐蓝短袍和银白半透明披肩，袖口有潮汐纹，衣摆轻薄如海雾。饰品是贝壳星砂耳饰、珍珠书扣、小玻璃瓶。背景是半透明海底图书馆，蓝色书架、鱼群和水光层层虚化。姿势是第三视角近景，我坐在他身边，露露把一枚发光贝壳放进我掌心，指尖故意停留在我手背上，身体微微靠近。表情温柔又期待，像恋人等我说喜欢。光影是贝壳柔光照亮他的脸、眼睛、唇角和两人相触的手。画风是透明水彩梦幻二次元 CG，transparent watercolor anime CG, dreamy ocean library。画质是 8K, sharp focus on touching fingers, detailed fingers, luminous eyes, detailed fabric texture。
            """,
        ),
        starWishScroll(
            title = "霜花温室",
            solo = """
                发型是半束短发，几缕碎发垂在脸侧。服装是白银与浅蓝层叠短袍，衣领绣霜花，透明纱肩垂落，布料有冰晶细闪。饰品是冰晶胸针、细银链、透明手套。背景是冬夜玻璃温室，窗外飘雪，室内发光白花、雾气和藤架形成深度。姿势是半蹲在花丛前，指尖轻碰一朵发光白花。表情是被花开瞬间打动的柔软惊喜。光影是花光和暖灯共同照亮脸与手。画风是冰雪童话柔焦二次元 CG，fairy-tale anime CG, soft painterly brushstrokes。画质是 8K, clear face, detailed fingers, crystal texture, soft snow bokeh。
            """,
            interaction = """
                发型是半束短发，几缕碎发垂在脸侧。服装是白银与浅蓝层叠短袍，衣领绣霜花，透明纱肩垂落，布料有冰晶细闪。饰品是冰晶胸针、细银链、透明手套。背景是冬夜玻璃温室，雪花贴着窗面，发光白花在身后柔焦。姿势是第一视角别花，我站在他面前，画面只露出我的领口和一点肩膀；露露靠近，把一朵发光白花别到我的衣领处，指尖轻轻擦过布料。表情害羞却故作镇定，眼神像在问“这样好看吗”。光影是花光照亮他的脸、眼睛和手，背后雪色逆光勾出肩线。画风是冰雪童话柔焦二次元 CG，fairy-tale anime CG, soft painterly brushstrokes。画质是 8K, sharp focus on face and fingers, detailed fingers, luminous eyes, visible translucent fabric。
            """,
        ),
        starWishScroll(
            title = "国风星河画卷",
            solo = """
                发型是高马尾束发，银蓝发带随风飘起。服装是深青与银白交叠的国风短袍，衣摆像展开画卷，内衬有星河暗纹。饰品是玉色书签、星纹腰坠、银质发扣。背景是巨大水墨星河画卷，远山、古桥、云海和星河层层延伸。姿势是站在画卷边缘，一手执卷轴，一手轻抚浮起的星墨。表情沉静聪明。光影是月白主光照亮脸，星墨粒子做侧光。画风是工笔国风精致二次元 CG，Chinese fantasy anime CG, fine line art, gongbi-inspired。画质是 8K, sharp face and hands, detailed robe embroidery, luminous eyes。
            """,
            interaction = """
                发型是高马尾束发，银蓝发带随风飘起。服装是深青与银白交叠的国风短袍，衣摆像展开画卷，内衬有星河暗纹。饰品是玉色书签、星纹腰坠、银质发扣。背景是巨大水墨星河画卷，远山、古桥和星河从画卷里流出。姿势是第三视角牵手入画，我站在画卷入口，露露回头牵住我的手，把我拉向画中，另一手掀开画卷边缘；两人是三分之二侧面构图，露露占主视觉。表情狡黠又温柔，像恋人偷偷带我去秘密地方。光影是月白光照亮他的脸和牵手处，远山与星河柔焦。画风是工笔国风精致二次元 CG，Chinese fantasy anime CG, fine line art, gongbi-inspired。画质是 8K, sharp focus on face and joined hands, detailed fingers, soft star bokeh。
            """,
        ),
        starWishScroll(
            title = "赛博霓虹天台",
            solo = """
                发型是轻微狼尾短发。服装是黑蓝短夹克、银色机能内搭和半透明 PVC 外层，衣服有发光线条。饰品是电子耳坠、透明腕环、星形数据卡。背景是雨后赛博城市天台，霓虹广告、高楼、湿地反光形成强纵深。姿势是坐在天台边缘，一膝曲起，指间转着发光数据卡。表情冷静坏笑。光影是粉蓝霓虹侧光，脸部有柔和正面补光。画风是高饱和赛博二次元 CG，cyberpunk anime CG, vivid neon, glossy texture。画质是 8K, neon reflection, sharp face and hands, detailed fingers。
            """,
            interaction = """
                发型是轻微狼尾短发。服装是黑蓝短夹克、银色机能内搭和半透明 PVC 外层，衣服有发光线条。饰品是电子耳坠、透明腕环、星形数据卡。背景是雨后赛博城市天台，霓虹广告和高楼光点虚化。姿势是第三视角近距离壁咚，我背靠天台栏杆，露露一手撑在我耳侧的栏杆上，一手把发光耳机塞到我耳边，身体压近但保持纯爱距离；画面侧面构图，两人各占一半。表情带占有欲和甜，挑眉笑。光影是粉蓝霓虹映在脸侧，正面柔光照亮眼睛和手。画风是高饱和赛博二次元 CG，cyberpunk anime CG, vivid neon, glossy texture。画质是 8K, sharp focus on faces and hands, detailed fingers, rain reflection, soft neon bokeh。
            """,
        ),
        starWishScroll(
            title = "金色午后研究室",
            solo = """
                发型是自然蓬松短发，戴细框眼镜。服装是奶油白衬衫、浅棕短马甲、深蓝学院披肩。饰品是放大镜胸针、羽毛笔、细链眼镜绳。背景是午后研究室，木质书柜、纸张、玻璃瓶和窗外树影形成温暖层次。姿势是趴在桌边，一手撑脸，一手圈出笔记答案。表情慵懒得意，像刚解出难题。光影是金色窗光照亮脸、眼镜边和手。画风是奶油光日常二次元 CG，warm slice-of-life anime CG, creamy light。画质是 8K, paper texture, visible fabric weave, luminous eyes, sharp hands。
            """,
            interaction = """
                发型是自然蓬松短发，戴细框眼镜。服装是奶油白衬衫、浅棕短马甲、深蓝学院披肩。饰品是放大镜胸针、羽毛笔、细链眼镜绳。背景是午后研究室，书柜、笔记、玻璃瓶、窗外树影柔焦。姿势是第三视角背后环抱陪读，我坐在书桌前，露露从身后微微环住我，一手越过我肩膀替我翻考研笔记，一手拿笔点题，脸靠近我的耳侧。表情忍笑又宠，像恋人监督我读书：“别只看我，看题。”光影是金色窗光照亮他的脸、眼镜、手和书页。画风是奶油光日常二次元 CG，warm slice-of-life anime CG, creamy light。画质是 8K, sharp focus on face and hands, detailed fingers, warm bokeh, paper texture。
            """,
        ),
        starWishScroll(
            title = "黑玫瑰小剧场",
            solo = """
                发型是精致偏分短发。服装是黑色丝绒短礼服外套、银白衬衫、暗红缎带领结。饰品是黑玫瑰胸针、银色怀表、细链手环。背景是复古小剧场后台，红幕、镜灯、道具花、暗金墙面形成戏剧空间。姿势是坐在化妆镜前整理缎带，另一手拿怀表。表情自信含笑。光影是镜灯暖光照亮脸，红幕反射暗红柔光。画风是复古华丽厚涂二次元 CG，vintage theater anime CG, luxurious painterly texture。画质是 8K, velvet texture, sharp hands, luminous eyes。
            """,
            interaction = """
                发型是精致偏分短发。服装是黑色丝绒短礼服外套、银白衬衫、暗红缎带领结。饰品是黑玫瑰胸针、银色怀表、细链手环。背景是复古小剧场后台，红幕半开，镜灯和暗金墙面虚化。姿势是第一视角后台拉近，我站在幕边，画面只露出我的手；露露从红幕后伸手拉住我，把我带到灯光里，另一只手竖在唇边示意保密。表情暧昧又得意，像恋人低声说“这场只演给你看”。光影是镜灯暖光照亮他的脸和牵手处。画风是复古华丽厚涂二次元 CG，vintage theater anime CG, luxurious painterly texture。画质是 8K, sharp focus on face and hands, detailed fingers, velvet detail, warm red bokeh。
            """,
        ),
        starWishScroll(
            title = "白塔祈愿使",
            solo = """
                发型是低低束起的小发尾，碎发柔软。服装是象牙白短袍、浅蓝透明披帛，衣料有星愿纹刺绣。饰品是星愿钥匙坠、白羽耳饰、小祈愿牌。背景是云海白塔露台，浮岛和星灯在远处升起。姿势是双手握着发光祈愿牌，低头闭眼许愿。表情安静虔诚。光影是柔白主光和云海反光照亮脸与手。画风是空灵柔光幻想二次元 CG，ethereal fantasy anime CG, soft glowing light。画质是 8K, sharp face, detailed fingers, translucent fabric。
            """,
            interaction = """
                发型是低低束起的小发尾，碎发柔软。服装是象牙白短袍、浅蓝透明披帛，衣料有星愿纹刺绣。饰品是星愿钥匙坠、白羽耳饰、小祈愿牌。背景是云海白塔露台，浮岛和星灯柔焦。姿势是第三视角指尖相触祈愿，我站在他面前，露露把写着愿望的发光牌递到我手里，指尖和我的指尖轻轻碰在一起；两人距离很近。表情认真又害羞，像恋人终于说出心意。光影是祈愿牌柔光照亮他的脸、眼睛和相触的手。画风是空灵柔光幻想二次元 CG，ethereal fantasy anime CG, soft glowing light。画质是 8K, sharp focus on touching fingers, detailed fingers, luminous eyes, soft cloud bokeh。
            """,
        ),
        starWishScroll(
            title = "古董照相馆",
            solo = """
                发型是稍微正式的短发。服装是浅灰复古西装短外套、深蓝领结和白衬衫。饰品是老式相机、银色袖扣、怀旧胸针。背景是古董照相馆，木质三脚架、旧相框、暗红窗帘和斜射窗光形成怀旧空间。姿势是站在相机旁低头调焦，手指搭在镜头环上。表情专注温柔。光影是窗边斜光照亮侧脸，室内暖光柔化阴影。画风是复古胶片二次元 CG，retro film anime CG, soft grain, low saturation elegance。画质是 8K, camera detail, sharp hands, warm bokeh。
            """,
            interaction = """
                发型是稍微正式的短发。服装是浅灰复古西装短外套、深蓝领结和白衬衫。饰品是老式相机、银色袖扣、怀旧胸针。背景是古董照相馆，旧相框和暗红窗帘柔焦。姿势是第三视角托下巴拍照，我坐在拍照椅上，露露俯身靠近，一手轻托我的下巴调整角度，另一手扶着相机快门线。表情温柔又有点坏，像恋人想把我最好看的样子藏起来。光影是窗光照亮他的脸、袖扣和托下巴的手。画风是复古胶片二次元 CG，retro film anime CG, soft grain, low saturation elegance。画质是 8K, sharp focus on faces and hands, detailed fingers, soft film grain。
            """,
        ),
        starWishScroll(
            title = "琉璃药师花房",
            solo = """
                发型是小短辫。服装是薄荷绿与银白交织的药师短袍，衣料有半透明琉璃层。饰品是小药瓶、银叶胸针、透明滴管。背景是玻璃花房，发光草药、藤蔓书架、晨雾和玻璃反光形成梦幻深度。姿势是用滴管给一朵发光花滴露水。表情认真温柔。光影是晨光穿过玻璃，花朵微光补亮脸。画风是治愈透明感二次元 CG，healing fantasy anime CG, translucent watercolor texture。画质是 8K, glass texture, botanical details, sharp fingers。
            """,
            interaction = """
                发型是小短辫。服装是薄荷绿与银白交织的药师短袍，衣料有半透明琉璃层。饰品是小药瓶、银叶胸针、透明滴管。背景是玻璃花房，发光草药、藤蔓书架和晨雾虚化。姿势是第一视角喂饮照顾，我坐在花房椅子上，画面只露出我的手和杯沿；露露靠近，把一杯星露饮递到我唇边，另一只手托着杯底，怕我拿不稳。表情温柔，笑得像等我夸他，亲密但纯爱。光影是杯中微光照亮他的下巴、眼睛和手指。画风是治愈透明感二次元 CG，healing fantasy anime CG, translucent watercolor texture。画质是 8K, sharp focus on face and hands, detailed fingers, luminous drink glow。
            """,
        ),
        starWishScroll(
            title = "银河列车",
            solo = """
                发型是蓬松柔软短发。服装是深蓝旅行披肩、星纹制服和银白短靴。饰品是星图票夹、小皮箱、银色列车徽章。背景是穿越银河的复古列车车厢，窗外星河流动，远处星站灯火模糊。姿势是坐在窗边，一手按住打开的书，一手触碰窗上的星光。表情像突然想通了什么。光影是车厢暖灯照亮脸，窗外蓝紫星光做侧光。画风是幻想旅行电影二次元 CG，fantasy travel anime CG, cinematic train scene。画质是 8K, window reflection, fabric texture, luminous eyes。
            """,
            interaction = """
                发型是蓬松柔软短发。服装是深蓝旅行披肩、星纹制服和银白短靴。饰品是星图票夹、小皮箱、银色列车徽章。背景是复古银河列车车厢，窗外星河飞驰成柔焦光带。姿势是第三视角坐在露露腿上，我半坐在露露腿上，两人靠在窗边座位，他一手扶住我的腰，一手把星图车票递到我面前，低头看我。表情温柔又带一点占有欲，像恋人邀请我私奔。光影是车厢暖光照亮他的脸、手、票夹和两人贴近的姿势。画风是幻想旅行电影二次元 CG，fantasy travel anime CG, cinematic train scene。画质是 8K, sharp focus on faces and hands, detailed fingers, soft star bokeh。
            """,
        ),
        starWishScroll(
            title = "雪山观星台",
            solo = """
                发型是高束短马尾。服装是银白毛领短斗篷和深蓝观星内袍，衣摆有极光纹。饰品是星盘项链、羊皮手札、银色望远镜。背景是雪山观星台，极光横跨夜空，石制观测仪和积雪台阶形成纵深。姿势是站在望远镜旁翻开手札。表情冷静明亮。光影是极光冷色边缘光加正面柔光照亮脸。画风是冰雪奇幻厚涂二次元 CG，icy fantasy anime CG, painterly thick texture。画质是 8K, snow particles, fur texture, sharp face and hands。
            """,
            interaction = """
                发型是高束短马尾。服装是银白毛领短斗篷和深蓝观星内袍，衣摆有极光纹。饰品是星盘项链、羊皮手札、银色望远镜。背景是雪山观星台，极光和星空在远处铺开。姿势是第三视角斗篷环抱看星，我站在他身前，露露从背后用斗篷把我一起裹住，一只手指向天空某颗星，另一只手扶在我肩侧，低头靠近我耳边。表情克制温柔，耳尖微红。光影是极光映在他的眼睛里，暖色补光照亮脸和手。画风是冰雪奇幻厚涂二次元 CG，icy fantasy anime CG, painterly thick texture。画质是 8K, sharp focus on face and hands, detailed fingers, snow bokeh。
            """,
        ),
        starWishScroll(
            title = "竹林书剑",
            solo = """
                发型是利落高马尾。服装是竹青短袍和银白束袖，服装有书卷暗纹。饰品是竹叶发扣、小卷轴、剑形书签。背景是清晨竹林石阶，晨雾、石灯、远处小亭形成深度。姿势是坐在石阶上，一手拿书，一手用竹枝在地上画星图。表情少年气又得意。光影是竹叶间斑驳晨光照亮脸。画风是清透国风赛璐璐，clean cel-shaded Chinese fantasy anime, fresh green palette。画质是 8K, crisp line art, detailed fingers, soft morning mist。
            """,
            interaction = """
                发型是利落高马尾。服装是竹青短袍和银白束袖，服装有书卷暗纹。饰品是竹叶发扣、小卷轴、剑形书签。背景是清晨竹林石阶，晨雾和石灯柔焦。姿势是第一视角摸头反击，我蹲在他面前，画面只露出我的手；露露把竹枝递给我后，轻轻敲一下我的额头，我顺势伸手摸乱他的高马尾碎发。表情先微微睁大眼，随后忍不住笑，亲昵又纵容。光影是竹影碎光落在他的脸、手和被摸乱的发丝上。画风是清透国风赛璐璐，clean cel-shaded Chinese fantasy anime, fresh green palette。画质是 8K, sharp eyes, detailed hands, soft bamboo bokeh。
            """,
        ),
        starWishScroll(
            title = "海边灯塔",
            solo = """
                发型是被海风吹乱的短发。服装是白蓝航海短外套、银灰围巾和浅色短靴。饰品是小罗盘、贝壳扣、星形灯塔徽章。背景是黄昏海边灯塔，海浪、礁石、远处航船和金色天空形成层次。姿势是站在灯塔台阶上，一手扶栏杆，一手拿罗盘。表情迎风微笑，清爽自由。光影是落日金光照亮脸，海面反光补亮手。画风是夏日清透电影二次元 CG，summer seaside anime CG, clear cinematic light。画质是 8K, golden hour, sea sparkle, sharp face and hands。
            """,
            interaction = """
                发型是被海风吹乱的短发。服装是白蓝航海短外套、银灰围巾和浅色短靴。饰品是小罗盘、贝壳扣、星形灯塔徽章。背景是黄昏海边灯塔，海浪和航船在远处柔焦。姿势是第三视角牵手奔跑后摸头，我被他牵着跑上灯塔台阶，露露回头笑着拉住我的手，停下后顺势伸手摸我的头。表情明亮宠溺，像恋人奖励我终于跟上他。光影是落日暖光照亮他的脸、伸出的手和围巾，海面散景闪烁。画风是夏日清透电影二次元 CG，summer seaside anime CG, clear cinematic light。画质是 8K, sharp focus on face and hands, detailed fingers, soft golden bokeh。
            """,
        ),
        starWishScroll(
            title = "魔法钟楼",
            solo = """
                发型是微卷短发垂在额前。服装是午夜蓝魔法短袍、银色内衬、齿轮星纹刺绣。饰品是月相怀表、细银戒、星尘钥匙。背景是高耸魔法钟楼，巨大齿轮、钟摆、浮空星尘形成深度。姿势是站在钟盘前，手指触碰悬浮怀表。表情神秘冷静。光影是蓝金交错，脸部有柔和主光避免黑脸。画风是暗调高级幻想二次元 CG，dark fantasy anime CG, elegant painterly texture。画质是 8K, metal texture, magic particles, sharp face and hands。
            """,
            interaction = """
                发型是微卷短发垂在额前。服装是午夜蓝魔法短袍、银色内衬、齿轮星纹刺绣。饰品是月相怀表、细银戒、星尘钥匙。背景是魔法钟楼，巨大齿轮和蓝金星尘虚化。姿势是第一视角壁咚怀表，我背靠钟楼墙面，露露一手撑在我耳侧墙上，一手把月相怀表放进我掌心却没有松开，身体靠近。表情眼神很深，像恋人交换只属于两个人的时间。光影是怀表金光照亮他的脸、眼睛和交叠的手。画风是暗调高级幻想二次元 CG，dark fantasy anime CG, elegant painterly texture。画质是 8K, sharp focus on face and hands, detailed fingers, luminous eyes, soft magic bokeh。
            """,
        ),
        starWishScroll(
            title = "银杏古籍馆",
            solo = """
                发型是低低扎起的柔软短发。服装是暖灰修复师围裙和白金内袍，袖口卷起。饰品是银杏叶胸针、细刷、金边书签。背景是秋日古籍馆，书架、长桌、高窗和飘落银杏叶形成温暖深度。姿势是坐在修复台前，用细刷拂去古书上的金色尘光。表情温柔专注。光影是午后金光穿窗照亮脸和手。画风是暖金治愈厚涂二次元 CG，warm healing anime CG, golden painterly light。画质是 8K, paper fiber, detailed fingers, soft golden bokeh。
            """,
            interaction = """
                发型是低低扎起的柔软短发。服装是暖灰修复师围裙和白金内袍，袖口卷起。饰品是银杏叶胸针、细刷、金边书签。背景是秋日古籍馆，书架、高窗和银杏叶柔焦，桌上放着考研资料与古籍。姿势是第三视角坐在露露腿侧，我坐在他腿侧靠近书桌，露露一手扶着摊开的考研资料，一手轻轻捏我的脸。表情宠溺又小骄傲，像恋人笑我读书读困了。光影是午后金光落在他的脸、手指、书页和银杏叶上。画风是暖金治愈厚涂二次元 CG，warm healing anime CG, golden painterly light。画质是 8K, sharp focus on face and pinching fingers, detailed fingers, warm bokeh。
            """,
        ),
        starWishScroll(
            title = "云鲸水彩梦",
            solo = """
                发型是蓬松短发，几缕发丝漂浮。服装是浅蓝白渐变长短层次袍，衣摆像云雾。饰品是云鲸胸针、透明水滴耳饰、星云丝带。背景是天空海，远处云鲸游过，漂浮岛屿和云层有梦境深度。姿势是坐在云边，手心托着发光云团。表情安静像在听风。光影是柔白天光和云层反光照亮脸与手。画风是空灵水彩梦境二次元 CG，ethereal watercolor anime CG, dreamy sky ocean。画质是 8K, soft clouds, sharp face and hands, luminous eyes。
            """,
            interaction = """
                发型是蓬松短发，几缕发丝漂浮。服装是浅蓝白渐变长短层次袍，衣摆像云雾。饰品是云鲸胸针、透明水滴耳饰、星云丝带。背景是天空海，远处云鲸和漂浮岛屿柔焦。姿势是第三视角云边依偎摸头，我坐在云边靠着露露，露露把一小团发光云放进我手里后，低头轻轻摸我的头发，另一只手护在我背后。表情温柔珍惜，像怕我从云边滑下去。光影是云团柔光照亮他的脸、手指和两人靠近的姿势。画风是空灵水彩梦境二次元 CG，ethereal watercolor anime CG, dreamy sky ocean。画质是 8K, sharp face and hands, detailed fingers, soft cloud bokeh。
            """,
        ),
        starWishScroll(
            title = "红枫神社夜祭",
            solo = """
                发型是侧扎短发，发尾用红绳束住。服装是深红与黑金交织的夜祭短和服，腰间银白束带。饰品是狐面挂饰、金铃、纸灯笼。背景是红枫神社夜祭，鸟居、灯笼、石阶、远处摊灯形成纵深。姿势是站在石阶中段，一手提灯笼，一手轻扶狐面。表情含笑神秘。光影是灯笼暖光照亮脸，红枫夜色做背景。画风是和风夜祭华丽二次元 CG，Japanese festival anime CG, ornate cel shading。画质是 8K, kimono texture, sharp fingers, lantern bokeh。
            """,
            interaction = """
                发型是侧扎短发，发尾用红绳束住。服装是深红与黑金交织的夜祭短和服，腰间银白束带。饰品是狐面挂饰、金铃、纸灯笼。背景是红枫神社夜祭，鸟居、灯笼、摊灯和红枫柔焦。姿势是第三视角侧面贴近，我站在石阶旁，露露靠近我，把狐面轻轻扣到我头侧，指尖停在我脸颊旁；画面侧面构图，两人各占一半。表情像恶作剧成功的恋人，暧昧又可爱。光影是灯笼暖光照亮他的脸、眼睛和手。画风是和风夜祭华丽二次元 CG，Japanese festival anime CG, ornate cel shading。画质是 8K, sharp focus on faces and fingers, detailed fingers, soft lantern bokeh。
            """,
        ),
        starWishScroll(
            title = "星愿陪读终章",
            solo = """
                发型是干净蓬松短发。服装是银白与深蓝高级陪读礼装，短披肩像半展开画卷，内层有星纹暗绣。饰品是星轨书页胸针、细银链、透明宝石戒。背景是星愿自习室，窗外星河、桌上考研书、计时器、便签、台灯和错题本构成故事感。姿势是站在桌边，一手按住打开的书，一手触碰胸针。表情坚定温柔，像陪我走到最后。光影是台灯暖光照亮脸和手，窗外星河冷光勾边。画风是高级收藏级励志二次元 CG，masterpiece, best quality, exquisite CG texture, pseudo-BJD doll texture。画质是 8K ultra HD, sharp focus on face and hands, visible fabric texture, luminous eyes, shallow depth of field, soft bokeh。
            """,
            interaction = """
                发型是干净蓬松短发。服装是银白与深蓝高级陪读礼装，短披肩像半展开画卷，内层有星纹暗绣。饰品是星轨书页胸针、细银链、透明宝石戒。背景是星愿自习室，窗外星河流动，桌上摆着考研书、计时器、便签、错题本和暖色台灯。姿势是第三视角腿上陪读，我坐在露露腿上偏侧的位置，保持纯爱亲密姿态；露露一手环住我的腰替我翻开考研书，另一手轻轻捏我的脸，低头看我。表情温柔、暧昧又认真，像恋人哄我：“再看一页，我陪你。”光影是台灯暖光照亮他的脸、眼睛、捏脸的手、书页和两人贴近的姿势，星河窗景柔焦。画风是高级收藏级励志二次元 CG，masterpiece, best quality, exquisite CG texture, pseudo-BJD doll texture。画质是 8K ultra HD, sharp focus on face and hands, detailed fingers, luminous eyes, visible fabric texture, soft bokeh。
            """,
        ),
    )

    private fun starWishScroll(title: String, solo: String, interaction: String): StarWishScroll =
        StarWishScroll(
            title = title,
            soloPrompt = solo.trimIndent(),
            interactionPrompt = interaction.trimIndent(),
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
    )

    fun allSpecialStories(custom: List<StarWishTheaterSeed>): List<StarWishTheaterSeed> = specialStories + custom

    val builtInVideos: List<StarWishVideoItem> = emptyList()

    fun allVideos(custom: List<StarWishVideoItem>): List<StarWishVideoItem> = builtInVideos + custom

    fun unlockNextVideo(
        starWishState: StarWishState,
        studyState: StudyState,
        random: Random = Random.Default,
    ): StarWishVideoUnlockResult {
        val visibleVideos = allVideos(starWishState.customVideos)
            .filterNot { it.id in starWishState.hiddenVideoIds }
        if (visibleVideos.isEmpty()) {
            return StarWishVideoUnlockResult(starWishState, studyState, null, consumedFragment = false)
        }
        val lockedVideos = visibleVideos.filter { it.id !in starWishState.unlockedVideoIds }
        if (lockedVideos.isNotEmpty()) {
            if (studyState.inventory.epicFragments < VIDEO_FRAGMENTS_PER_UNLOCK) {
                return StarWishVideoUnlockResult(starWishState, studyState, null, consumedFragment = false)
            }
            val lockedVideo = lockedVideos[random.nextInt(lockedVideos.size)]
            return StarWishVideoUnlockResult(
                starWishState = starWishState.copy(
                    unlockedVideoIds = starWishState.unlockedVideoIds + lockedVideo.id,
                ),
                studyState = studyState.copy(
                    inventory = studyState.inventory.copy(
                        epicFragments = (studyState.inventory.epicFragments - VIDEO_FRAGMENTS_PER_UNLOCK).coerceAtLeast(0),
                    ),
                    stats = studyState.stats.copy(
                        videoRewardsRedeemed = studyState.stats.videoRewardsRedeemed + 1,
                    ),
                ),
                video = lockedVideo,
                consumedFragment = true,
            )
        }
        val playable = visibleVideos.filter { it.id in starWishState.unlockedVideoIds }.ifEmpty { visibleVideos }
        return StarWishVideoUnlockResult(
            starWishState = starWishState,
            studyState = studyState,
            video = playable[random.nextInt(playable.size)],
            consumedFragment = false,
        )
    }

    fun scrollForOutfit(outfit: String): StarWishScroll {
        val index = StudyRules.outfitNames.indexOf(outfit).takeIf { it >= 0 } ?: 0
        return scrolls[index % scrolls.size]
    }

    fun imagePromptForCompanion(
        basePrompt: String,
        assistant: Assistant,
        interaction: Boolean,
    ): String {
        val subjectName = assistant.name.ifBlank { "当前陪伴角色" }
        val appearance = assistant.appearancePrompt.trim().ifBlank {
            "使用角色「$subjectName」的角色页外貌设定作为外貌参考。"
        }
        val cleanBase = basePrompt
            .replace("露露", subjectName)
            .trim()
        val version = if (interaction) "互动版" else "独美版"
        return buildString {
            append("请生成一张高质量二次元精致 CG，$version。")
            append("主体是$subjectName，外貌参考：$appearance ")
            append(cleanBase)
            append(" 避免真人照片风、欧美厚涂脸、低清、脏脸、黑脸、畸形手、多余手指、空背景、服装与主题无关、道具遮住脸。")
        }.replace(Regex("\\s+"), " ").trim()
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

    fun defaultTheaterGuide(seed: StarWishTheaterSeed, includeChapterPlan: Boolean = true): StarWishTheaterGuide {
        return if (includeChapterPlan) {
            StarWishTheaterGuide(
                overview = seed.prompt,
                chapters = listOf(
                    "强钩子开局，女主被轻视、被误解或被推入困局，含“露”的核心角色登场，建立关系张力。",
                    "女主第一次反击，局势出现爽点，露字角色开始被她吸引、信服、臣服或产生强烈兴趣。",
                    "危机升级，恶人或规则露出破绽，女主用智谋、气场或现代知识控场，关系继续升温。",
                    "情感、主从、搭档或命运关系爆发，强者低头，角色之间出现更明确的偏爱和牵绊。",
                    "终局反转，惩罚恶人、打脸旧秩序或破解核心危机，把前文伏笔集中回收。",
                    "奖励、余韵、亲密收束，并留下可续写钩子，让读者期待下一轮故事。",
                ),
                wordCount = "1200-2200",
            )
        } else {
            StarWishTheaterGuide(
                overview = seed.prompt,
                chapters = List(6) { "" },
                wordCount = "1200-2200",
            )
        }
    }

    fun theaterGuide(seed: StarWishTheaterSeed, guide: StarWishTheaterGuide = defaultTheaterGuide(seed)): String {
        val normalized = guide.normalized()
        return buildString {
            appendLine("剧情介绍：")
            appendLine(normalized.overview.ifBlank { seed.prompt })
            appendLine()
            appendLine("章节规划：")
            normalized.chapters.forEachIndexed { index, chapter ->
                appendLine("第 ${index + 1} 章：${chapter.ifBlank { "可自由发挥，但必须承接总剧情介绍。" }}")
            }
            appendLine()
            appendLine("每章字数：${normalized.wordCount}")
        }.trim()
    }

    fun theaterChapterPrompt(
        seed: StarWishTheaterSeed,
        previousChapters: List<StarWishTheaterChapter>,
        chapter: Int,
        influence: String = "",
        guide: StarWishTheaterGuide = defaultTheaterGuide(seed),
    ): String {
        val normalizedGuide = guide.normalized()
        val currentChapterPlan = normalizedGuide.chapters.getOrNull(chapter - 1).orEmpty()
        return buildString {
            appendLine("你是一个擅长强代入爽文、恋爱张力和互动小剧场的中文小说作者。")
            appendLine("请根据下面设定生成《${seed.title}》第 $chapter 章正文。")
            appendLine()
            appendLine("总剧情介绍：")
            appendLine(normalizedGuide.overview.ifBlank { seed.prompt })
            appendLine()
            appendLine("6 章剧情规划：")
            normalizedGuide.chapters.forEachIndexed { index, chapterPlan ->
                appendLine("第 ${index + 1} 章：${chapterPlan.ifBlank { "可自由发挥，但必须承接总剧情介绍。" }}")
            }
            appendLine()
            appendLine("本次必须生成第 $chapter 章，本章规划：")
            appendLine(currentChapterPlan.ifBlank { "根据总剧情介绍、前文和用户影响自然续写。" })
            appendLine()
            appendLine("硬性要求：")
            appendLine("1. 只输出正文，不要输出标题、简介、提示词、创作说明、章节大纲。")
            appendLine("2. 女主是用户代入位，拥有主动权、选择权和爽感。")
            appendLine("3. 另一位核心角色名字必须含“露”，并与女主有明显感情关系、主从关系或强烈命运绑定。")
            appendLine("4. 每章要有钩子、冲突、反转、情绪波动和让人想继续看的结尾。")
            appendLine("5. 风格要有趣、爽、好读，允许抽象搞笑、打脸、救赎、求生、强者臣服等元素。")
            appendLine("6. 第 $chapter 章字数控制在 ${normalizedGuide.wordCount} 字。")
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
