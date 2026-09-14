package com.example.toread.categories

/**
 * Local category inference for imported books.
 *
 * Everything here is offline and deterministic: a built-in keyword dictionary, the reader's own
 * `关键词=分类` rules, and the categories this reader has already chosen for the same author, series
 * or keyword. Nothing is fetched, nothing is uploaded, and a suggestion is only ever a suggestion —
 * the book's own 分类/标签 value belongs to the reader.
 */

/** One `关键词 -> 分类` rule, from the built-in dictionary or from the reader's own list. */
data class BookCategoryEntry(
    val keyword: String,
    val category: String,
)

/** Where a suggestion came from, so the UI can explain why it is offered. */
enum class BookCategorySource {
    /** A rule the reader wrote in 设置 - 分类词典. */
    UserDictionary,

    /** A category this reader already gave to the same author, series or keyword. */
    UserMemory,

    /** The shipped keyword dictionary. */
    BuiltInDictionary,
}

data class BookCategorySuggestion(
    val category: String,
    val reason: String,
    val source: BookCategorySource,
    val score: Int,
)

/** Everything a book offers as classification evidence, strongest signal first. */
data class BookCategoryInput(
    val title: String = "",
    val author: String? = null,
    val series: String? = null,
    val tags: String = "",
    val description: String? = null,
)

/** The score a category needs before it is worth showing as a chip. */
private const val MinimumSuggestionScore = 1

/** One category never accumulates more than this, so a long blurb cannot drown the title. */
private const val MaximumScorePerCategory = 12

private const val MemoryAuthorScore = 8
private const val MemorySeriesScore = 6
private const val MemoryKeywordScore = 4
private const val SeriesDictionaryScore = 4
private const val UserTitleScore = 4
private const val BuiltInTitleScore = 3
private const val UserDescriptionScore = 2
private const val BuiltInDescriptionScore = 1

/** Keywords shorter than this are dropped: one character matches everything. */
private const val MinimumKeywordLength = 2

private const val MaximumMemoryEntries = 200

/** The longest category name the reader's own rules and the suggestions accept. */
internal const val MaximumCategoryLength = 12

/**
 * The shipped dictionary, grouped as `分类 -> 关键词`, and shown read-only in
 * 设置 - 分类词典.
 *
 * The vocabulary follows what Chinese readers and platforms actually use rather than anything
 * invented here:
 *
 * - the web-novel side follows the 番茄小说 ranking menus (男频/女频 platform tags such as
 *   都市修真、都市高武、战神赘婿、科幻末世、悬疑灵异、抗战谍战, and their canonical genres) and the
 *   classic 起点 taxonomy (玄幻、奇幻、武侠、仙侠、都市、历史、军事、科幻、悬疑、游戏…), including the
 *   aliases that source lists (修仙/修真 → 仙侠, 末世/丧尸/废土/赛博朋克 → 科幻, 谍战/抗日 → 军事,
 *   异能/赘婿/战神 → 都市, 克苏鲁/诡秘 → 悬疑, 宫斗/宅斗/权谋 → 古言 …);
 * - the non-fiction side follows 《中国图书馆分类法》 top-level classes — 文学 I、历史地理 K、
 *   经济 F、哲学宗教 B、社会科学总论 C、军事 E、艺术 J、数理科学和化学 O、生物科学 Q、
 *   医药卫生 R、工业技术/计算机 TP、文化教育 G。
 *
 * A 套路 such as 系统流/退婚流/无限流 is deliberately **not** a category: the same 套路 appears in
 * every 题材, so it would only add noise to a suggestion.
 */
internal val builtInCategoryKeywords: List<Pair<String, List<String>>> = listOf(
    "玄幻" to listOf(
        "玄幻", "东方玄幻", "传统玄幻", "玄幻脑洞", "异世", "异世大陆", "斗气", "大陆争霸",
        "废材", "退婚", "莫欺少年穷", "强者为尊",
    ),
    "奇幻" to listOf(
        "奇幻", "西方奇幻", "魔法", "巫师", "精灵", "矮人", "巨龙", "骑士", "史诗", "神话",
        "魔药", "封印物", "蒸汽", "蒸汽朋克", "克苏鲁",
    ),
    "武侠" to listOf(
        "武侠", "江湖", "侠客", "剑客", "门派", "内功", "剑法", "刀法", "侠义", "恩怨",
    ),
    "仙侠" to listOf(
        "仙侠", "东方仙侠", "修仙", "修真", "修道", "都市修真", "元婴", "金丹", "剑仙",
        "飞升", "渡劫", "天劫", "宗门", "灵气", "因果", "长生",
    ),
    "都市" to listOf(
        "都市", "都市日常", "都市脑洞", "都市高武", "高武", "战神", "赘婿", "上门女婿",
        "异能", "职场", "商战", "创业", "神医",
    ),
    "历史" to listOf(
        "历史", "历史古代", "历史脑洞", "穿越", "架空", "权谋", "三国", "大明", "大唐",
        "王朝", "争霸", "通史", "断代史", "史学", "考古", "文物",
    ),
    "军事" to listOf(
        "军事", "战争", "谍战", "抗战", "抗日", "军旅", "特种兵", "雇佣兵", "兵王",
    ),
    "科幻" to listOf(
        "科幻", "科幻末世", "星际", "机甲", "宇宙", "太空", "赛博", "赛博朋克", "人工智能",
        "末世", "丧尸", "废土", "未来世界", "时间旅行",
    ),
    "悬疑" to listOf(
        "悬疑", "悬疑脑洞", "悬疑灵异", "推理", "侦探", "刑侦", "犯罪", "密室", "诡计",
        "规则怪谈", "无限流", "盗墓", "古董", "诡秘",
    ),
    "恐怖" to listOf(
        "恐怖", "惊悚", "灵异", "鬼怪", "怪谈", "民俗恐怖", "僵尸", "诅咒",
    ),
    "游戏" to listOf(
        "游戏", "游戏体育", "电竞", "网游", "竞技", "体育", "体育竞技",
    ),
    "言情" to listOf(
        "言情", "现言", "现言脑洞", "青春甜宠", "甜宠", "虐恋", "婚恋", "职场婚恋",
        "豪门", "豪门总裁", "总裁", "霸总", "娱乐圈", "明星", "星光璀璨", "青春", "校园", "暗恋",
    ),
    "古言" to listOf(
        "古言", "古言脑洞", "古风世情", "宫斗", "宅斗", "后宫", "宫廷", "王妃", "嫡女",
        "庶女", "世家", "穿书",
    ),
    "年代" to listOf(
        "年代", "民国", "民国言情", "知青", "六零", "七零", "八零", "九零",
    ),
    "种田" to listOf(
        "种田", "经营", "基建", "慢生活", "农家", "田园", "开店",
    ),
    "快穿" to listOf(
        "快穿", "任务世界", "位面", "攻略",
    ),
    "衍生" to listOf(
        "衍生", "同人", "二次创作", "动漫衍生", "轻小说", "二次元", "综漫",
    ),
    "文学" to listOf(
        "文学", "散文", "随笔", "小说集", "短篇", "名著", "诗歌", "戏剧", "报告文学",
        "外国文学", "现当代文学",
    ),
    "社科" to listOf(
        "社会学", "人类学", "政治", "法律", "文化研究", "制度", "田野调查",
    ),
    "科普" to listOf(
        "科普", "物理学", "数学", "化学", "生物学", "天文学", "地质", "科学", "量子",
        "相对论", "进化论",
    ),
    "经管" to listOf(
        "经济", "经济学", "管理", "管理学", "投资", "理财", "金融", "商业", "营销",
        "会计", "财务",
    ),
    "传记" to listOf(
        "传记", "自传", "回忆录", "生平", "人物志",
    ),
    "哲学" to listOf(
        "哲学", "思想史", "宗教", "佛学", "禅修", "伦理学", "逻辑学", "美学", "国学",
    ),
    "心理" to listOf(
        "心理学", "认知", "情绪", "焦虑", "疗愈", "心理咨询",
    ),
    "生活" to listOf(
        "养生", "中医", "健康", "食谱", "烹饪", "烘焙", "旅行", "旅游", "家居", "收纳",
        "健身", "育儿",
    ),
    "艺术" to listOf(
        "艺术", "绘画", "书法", "摄影", "音乐", "设计", "电影", "建筑",
    ),
    "童书" to listOf(
        "童书", "童话", "绘本", "儿童", "少儿", "亲子", "幼儿",
    ),
    "漫画" to listOf(
        "漫画", "画集", "条漫",
    ),
    "学习" to listOf(
        "教材", "教辅", "教程", "考试", "编程", "计算机", "英语", "语法",
    ),
)

/** The same rules flattened into `关键词 -> 分类` pairs, which is what the engine matches. */
internal val builtInCategoryEntries: List<BookCategoryEntry> =
    builtInCategoryKeywords.flatMap { (category, keywords) ->
        keywords.map { keyword -> BookCategoryEntry(keyword, category) }
    }

/**
 * Parses the reader's `关键词=分类` lines. Blank lines and lines without a separator are dropped
 * instead of failing: a half-typed line in the settings must never take the whole dictionary down.
 */
internal fun parseCategoryEntries(text: String?): List<BookCategoryEntry> =
    text.orEmpty()
        .split('\n')
        .mapNotNull { line -> line.toCategoryEntry() }
        .distinct()

private fun String.toCategoryEntry(): BookCategoryEntry? {
    val separatorIndex = indexOfFirst { it == '=' || it == '\t' }
    if (separatorIndex <= 0) return null
    val keyword = substring(0, separatorIndex).trim()
    val category = substring(separatorIndex + 1).trim()
    if (keyword.length < MinimumKeywordLength) return null
    if (category.isBlank() || category.length > MaximumCategoryLength) return null
    return BookCategoryEntry(keyword = keyword, category = category)
}

internal fun encodeCategoryEntries(entries: List<BookCategoryEntry>): String =
    entries
        .map { entry -> "${entry.keyword.trim()}=${entry.category.trim()}" }
        .distinct()
        .joinToString("\n")

/** Splits the 分类/标签 field, which is a comma-separated list in the book info page. */
internal fun parseTagList(tags: String): List<String> =
    tags.split(',', '，', '、', ';', '；', '\n')
        .map(String::trim)
        .filter { it.isNotBlank() }
        .distinct()

internal fun joinTagList(tags: Collection<String>): String =
    tags.map(String::trim).filter { it.isNotBlank() }.distinct().joinToString(", ")

/** Appends [tag] to the comma-separated [tags] field unless it is already there. */
internal fun appendTag(tags: String, tag: String): String {
    val clean = tag.trim()
    if (clean.isBlank()) return tags
    val existing = parseTagList(tags)
    if (existing.any { it.equals(clean, ignoreCase = true) }) return tags
    return joinTagList(existing + clean)
}

/** Adds [tag] when it is missing and removes it when it is there — what a chip tap does. */
internal fun toggleTag(tags: String, tag: String): String {
    val clean = tag.trim()
    if (clean.isBlank()) return tags
    val existing = parseTagList(tags)
    val remaining = existing.filterNot { it.equals(clean, ignoreCase = true) }
    return if (remaining.size != existing.size) {
        joinTagList(remaining)
    } else {
        joinTagList(existing + clean)
    }
}

/**
 * Ranks category suggestions for one book.
 *
 * The reader's own memory outranks the shipped dictionary, the title outranks the blurb, and a
 * category the reader already typed into 分类/标签 is never suggested back at them.
 */
internal fun suggestBookCategories(
    input: BookCategoryInput,
    userEntries: List<BookCategoryEntry> = emptyList(),
    memoryEntries: List<BookCategoryEntry> = emptyList(),
    limit: Int = 4,
): List<BookCategorySuggestion> {
    val existingTags = parseTagList(input.tags).map { it.lowercase() }
    val title = input.title
    val description = input.description.orEmpty()
    val author = input.author?.trim().orEmpty()
    val series = input.series?.trim().orEmpty()
    val hits = mutableListOf<CategoryHit>()

    fun addHit(category: String, score: Int, reason: String, source: BookCategorySource) {
        val clean = category.trim()
        if (clean.isBlank() || clean.length > MaximumCategoryLength) return
        if (clean.lowercase() in existingTags) return
        hits += CategoryHit(category = clean, score = score, reason = reason, source = source)
    }

    memoryEntries.forEach { entry ->
        val keyword = entry.keyword.trim()
        when {
            keyword.isBlank() -> Unit
            keyword.equals(author, ignoreCase = true) && author.isNotBlank() -> addHit(
                category = entry.category,
                score = MemoryAuthorScore,
                reason = "你曾为作者「$keyword」选择该分类",
                source = BookCategorySource.UserMemory,
            )
            keyword.equals(series, ignoreCase = true) && series.isNotBlank() -> addHit(
                category = entry.category,
                score = MemorySeriesScore,
                reason = "你曾为系列「$keyword」选择该分类",
                source = BookCategorySource.UserMemory,
            )
            else -> when {
                title.contains(keyword, ignoreCase = true) -> addHit(
                    category = entry.category,
                    score = MemoryKeywordScore,
                    reason = "你曾为含「$keyword」的书选择该分类",
                    source = BookCategorySource.UserMemory,
                )
                description.contains(keyword, ignoreCase = true) -> addHit(
                    category = entry.category,
                    score = MemoryKeywordScore,
                    reason = "你曾为含「$keyword」的书选择该分类",
                    source = BookCategorySource.UserMemory,
                )
            }
        }
    }

    fun applyEntries(entries: List<BookCategoryEntry>, source: BookCategorySource) {
        entries.forEach { entry ->
            val keyword = entry.keyword.trim()
            if (keyword.length < MinimumKeywordLength) return@forEach
            val inTitle = title.contains(keyword, ignoreCase = true)
            val inDescription = description.contains(keyword, ignoreCase = true)
            val inSeries = series.contains(keyword, ignoreCase = true)
            when {
                inSeries -> addHit(
                    category = entry.category,
                    score = SeriesDictionaryScore,
                    reason = "系列名为「$keyword」",
                    source = source,
                )
                inTitle -> addHit(
                    category = entry.category,
                    score = if (source == BookCategorySource.UserDictionary) {
                        UserTitleScore
                    } else {
                        BuiltInTitleScore
                    },
                    reason = if (source == BookCategorySource.UserDictionary) {
                        "你的词典：书名含「$keyword」"
                    } else {
                        "书名含「$keyword」"
                    },
                    source = source,
                )
                inDescription -> addHit(
                    category = entry.category,
                    score = if (source == BookCategorySource.UserDictionary) {
                        UserDescriptionScore
                    } else {
                        BuiltInDescriptionScore
                    },
                    reason = if (source == BookCategorySource.UserDictionary) {
                        "你的词典：简介含「$keyword」"
                    } else {
                        "简介含「$keyword」"
                    },
                    source = source,
                )
            }
        }
    }

    applyEntries(userEntries, BookCategorySource.UserDictionary)
    applyEntries(builtInCategoryEntries, BookCategorySource.BuiltInDictionary)

    return hits
        .groupBy { it.category }
        .map { (category, categoryHits) ->
            val best = categoryHits.maxBy { it.score }
            val total = categoryHits.sumOf { it.score }.coerceAtMost(MaximumScorePerCategory)
            BookCategorySuggestion(
                category = category,
                reason = best.reason,
                source = best.source,
                score = total,
            )
        }
        .filter { it.score >= MinimumSuggestionScore }
        // Equal scores are decided by who said it: the reader's memory, then the reader's own rules,
        // then the shipped dictionary — so adding a rule always beats the built-in keyword it overlaps.
        .sortedWith(
            compareByDescending<BookCategorySuggestion> { it.score }
                .thenBy { it.source.preferenceRank }
                .thenBy { it.category },
        )
        .take(limit.coerceAtLeast(0))
}

/** Lower wins a tie in [suggestBookCategories]. */
private val BookCategorySource.preferenceRank: Int
    get() = when (this) {
        BookCategorySource.UserMemory -> 0
        BookCategorySource.UserDictionary -> 1
        BookCategorySource.BuiltInDictionary -> 2
    }

/**
 * Remembers what the reader chose, so the next book by the same author or series can be suggested
 * the same way. Later choices for one keyword win, and the list stays bounded.
 */
internal fun recordCategoryMemory(
    existing: List<BookCategoryEntry>,
    input: BookCategoryInput,
    savedTags: String,
): List<BookCategoryEntry> {
    val categories = parseTagList(savedTags)
    if (categories.isEmpty()) return existing

    val author = input.author?.trim().orEmpty()
    val series = input.series?.trim().orEmpty()
    val learned = buildList {
        if (author.length >= MinimumKeywordLength) {
            categories.forEach { category -> add(BookCategoryEntry(author, category)) }
        }
        if (series.length >= MinimumKeywordLength) {
            categories.forEach { category -> add(BookCategoryEntry(series, category)) }
        }
    }
    if (learned.isEmpty()) return existing

    val learnedKeywords = learned.map { it.keyword }.toSet()
    return (learned + existing.filterNot { it.keyword in learnedKeywords })
        .distinct()
        .take(MaximumMemoryEntries)
}

/** Removes one memorised rule; the settings page deletes exactly what it shows. */
internal fun removeCategoryMemory(
    existing: List<BookCategoryEntry>,
    entry: BookCategoryEntry,
): List<BookCategoryEntry> =
    existing.filterNot { it.keyword == entry.keyword && it.category == entry.category }

private data class CategoryHit(
    val category: String,
    val score: Int,
    val reason: String,
    val source: BookCategorySource,
)
