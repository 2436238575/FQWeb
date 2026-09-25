package me.fycz.fqweb.web.service

import me.fycz.fqweb.constant.Config
import me.fycz.fqweb.utils.FiledNameUtils
import me.fycz.fqweb.utils.GlobalApp
import me.fycz.fqweb.utils.callMethod
import me.fycz.fqweb.utils.callStaticMethod
import me.fycz.fqweb.utils.findClass
import me.fycz.fqweb.utils.findField
import me.fycz.fqweb.utils.getObjectField
import me.fycz.fqweb.utils.getStaticObjectFieldOrNull
import me.fycz.fqweb.utils.log
import me.fycz.fqweb.utils.new
import me.fycz.fqweb.utils.setBooleanField
import me.fycz.fqweb.utils.setFloatField
import me.fycz.fqweb.utils.setIntField
import me.fycz.fqweb.utils.setLongField
import me.fycz.fqweb.utils.setObjectField
import me.fycz.fqweb.utils.setShortField

/**
 * @author fengyue
 * @date 2023/5/30 10:54
 * @description
 */
object DragonService {

    private const val CRYPT_KEY_STORE = "prefix_public_crypt_key_kv_0"
    private val TEXT_P_REGEX = Regex("<p\\b([^>]*)>.*?</p>", RegexOption.DOT_MATCHES_ALL)
    private val IMG_REGEX = Regex("<img\\b([^>]*)>")
    private val CAPTION_REGEX = Regex("class=\"pictureDesc\"[^>]*>(.*?)<")

    private val dragonClassLoader: ClassLoader by lazy {
        GlobalApp.getClassloader()
    }

    fun search(keyword: String, page: Int = 1): Any {
        val GetSearchPageRequest =
            "${Config.rpcModelPackage}.GetSearchPageRequest".findClass(dragonClassLoader)
        val getSearchPageRequest = GetSearchPageRequest.newInstance()
        getSearchPageRequest.setIntField("bookshelfSearchPlan", 4)
        getSearchPageRequest.setIntField("bookstoreTab", 2)
        getSearchPageRequest.setObjectField("clickedContent", "page_search_button")
        getSearchPageRequest.setObjectField("query", keyword)
        getSearchPageRequest.setObjectField(
            "searchSource",
            "${Config.rpcModelPackage}.SearchSource"
                .findClass(dragonClassLoader)
                .callStaticMethod("findByValue", arrayOf(Int::class.java), 1)
        )
        getSearchPageRequest.setObjectField("searchSourceId", "clks###")
        getSearchPageRequest.setObjectField("tabName", "store")
        getSearchPageRequest.setObjectField(
            "tabType", "${Config.rpcModelPackage}.SearchTabType"
                .findClass(dragonClassLoader)
                .callStaticMethod("findByValue", arrayOf(Int::class.java), 1)
        )
        getSearchPageRequest.setShortField("userIsLogin", 1)
        setField(getSearchPageRequest, "offset", (page - 1) * 10)
        setField(getSearchPageRequest, "passback", (page - 1) * 10)
        return callFunction(
            clzName = "${Config.rpcApiPackage}.a",
            obj = getSearchPageRequest,
            funcName = "b"
        )
    }

    fun getInfo(bookId: Long): Any {
        val BookDetailRequest =
            "${Config.rpcModelPackage}.BookDetailRequest".findClass(dragonClassLoader)
        val bookDetailRequest = BookDetailRequest.newInstance()
        bookDetailRequest.setLongField("bookId", bookId)
        return callFunction(
            clzName = "${Config.rpcApiPackage}.a",
            obj = bookDetailRequest
        )
    }

    fun getCatalog(bookId: Long): Any {
        val GetDirectoryForItemIdRequest =
            "${Config.rpcModelPackage}.GetDirectoryForItemIdRequest".findClass(dragonClassLoader)
        val getDirectoryForItemIdRequest = GetDirectoryForItemIdRequest.newInstance()
        getDirectoryForItemIdRequest.setObjectField("bookId", bookId)
        return callFunction(
            clzName = "${Config.rpcApiPackage}.a",
            obj = getDirectoryForItemIdRequest
        )
    }

    fun getContent(itemId: String, novelTextType: String? = null): Any {
        val FullRequest =
            "${Config.rpcModelPackage}.FullRequest".findClass(dragonClassLoader)
        val fullRequest = FullRequest.newInstance()
        fullRequest.setObjectField("itemId", itemId)
        if (!novelTextType.isNullOrEmpty()) {
            fullRequest.setObjectField(
                "novelTextType",
                commentEnum("NovelTextType", novelTextType)
            )
        }
        return callFunction(
            clzName = Config.readerFullRequestClz,
            obj = fullRequest
        )
    }

    fun decodeContent(itemContent: Any): Any {
        return withRetry {
            "com.dragon.read.reader.bookend.a.a".findClass(dragonClassLoader)
                .new(null).callMethod("a", itemContent)!!.callMethod("blockingFirst")!!
        }
    }

    fun getBookComments(
        bookId: String,
        page: Int,
        count: Int,
        sort: String,
        sessionId: String? = null,
    ): Any {
        val GetCommentByBookIdRequest =
            "${Config.rpcModelPackage}.GetCommentByBookIdRequest".findClass(dragonClassLoader)
        val getCommentByBookIdRequest = GetCommentByBookIdRequest.newInstance()
        getCommentByBookIdRequest.setObjectField("bookId", bookId)
        getCommentByBookIdRequest.setLongField("offset", (page - 1).toLong() * count)
        getCommentByBookIdRequest.setLongField("count", count.toLong())
        getCommentByBookIdRequest.setObjectField("sort", sort)
        getCommentByBookIdRequest.setObjectField(
            "sourceType",
            commentEnum("SourcePageType", "DetailBookCommentList")
        )
        //smart_hot 是推荐流，服务端只认会话游标不认 offset；翻页请求按宿主做法带上会话
        if (!sessionId.isNullOrEmpty()) {
            getCommentByBookIdRequest.setObjectField("sessionId", sessionId)
            getCommentByBookIdRequest.setObjectField(
                "queryType",
                commentEnum("CommentQueryType", "Normal")
            )
        }
        return callFunction(clzName = Config.commentRpcApiClz, obj = getCommentByBookIdRequest)
    }

    //章评面板请求（hook 实测）：source=comment_list + queryCol=All 是排序生效的前提；
    //app 配对为 smart_hot→Unfold、time→Normal，与段评的 Normal/Unfold 方向相反
    fun getItemComments(
        itemId: String,
        bookId: String,
        page: Int,
        count: Int,
        sort: String = "smart_hot",
    ): Any {
        val GetCommentByItemIdRequest =
            "${Config.rpcModelPackage}.GetCommentByItemIdRequest".findClass(dragonClassLoader)
        val getCommentByItemIdRequest = GetCommentByItemIdRequest.newInstance()
        getCommentByItemIdRequest.setObjectField("itemId", itemId)
        getCommentByItemIdRequest.setObjectField("bookId", bookId)
        getCommentByItemIdRequest.setLongField("offset", (page - 1).toLong() * count)
        getCommentByItemIdRequest.setLongField("count", count.toLong())
        //needReply 才会带上每条评论的内联回复
        getCommentByItemIdRequest.setBooleanField("needReply", true)
        getCommentByItemIdRequest.setObjectField("source", "comment_list")
        getCommentByItemIdRequest.setObjectField("sort", sort)
        getCommentByItemIdRequest.setObjectField(
            "queryCol",
            commentEnum("QueryCollection", "All")
        )
        getCommentByItemIdRequest.setObjectField(
            "queryType",
            commentEnum("CommentQueryType", if (sort == "smart_hot") "Unfold" else "Normal")
        )
        return callFunction(clzName = Config.commentRpcApiClz, obj = getCommentByItemIdRequest)
    }

    //段评：宿主按 paraIndex 取某一段的段评，paraIndex/count/offset 在该模型里都是 Int
    //itemVersion 取自 /content 响应的 data.data.novel_data.version，缺了服务端会拒绝
    fun getParaComments(
        bookId: String,
        itemId: String,
        paraIndex: Int,
        page: Int,
        count: Int,
        itemVersion: String,
        sort: String? = null,
    ): Any {
        val GetIdeaCommentListRequest =
            "${Config.rpcModelPackage}.GetIdeaCommentListRequest".findClass(dragonClassLoader)
        val getIdeaCommentListRequest = GetIdeaCommentListRequest.newInstance()
        getIdeaCommentListRequest.setObjectField("bookId", bookId)
        getIdeaCommentListRequest.setObjectField("itemId", itemId)
        getIdeaCommentListRequest.setIntField("paraIndex", paraIndex)
        getIdeaCommentListRequest.setIntField("offset", (page - 1) * count)
        getIdeaCommentListRequest.setIntField("count", count)
        getIdeaCommentListRequest.setObjectField("itemVersion", itemVersion)
        //段评的 sort 是 CommentSortType 枚举（Hot/TimeAsc/TimeDesc/ReplyTimeDesc）
        if (!sort.isNullOrEmpty()) {
            getIdeaCommentListRequest.setObjectField("sort", commentEnum("CommentSortType", sort))
        }
        return callFunction(clzName = Config.commentRpcApiClz, obj = getIdeaCommentListRequest)
    }

    //段评概览：返回 paraIndex -> 该段段评数据（含 ideaCount），用于知道哪些段落有段评
    fun getParaCommentIndex(bookId: String, itemId: String, itemVersion: String): Any {
        val GetIdeaListRequest =
            "${Config.rpcModelPackage}.GetIdeaListRequest".findClass(dragonClassLoader)
        val getIdeaListRequest = GetIdeaListRequest.newInstance()
        getIdeaListRequest.setObjectField("bookId", bookId)
        getIdeaListRequest.setObjectField("itemId", itemId)
        getIdeaListRequest.setObjectField("itemVersion", itemVersion)
        return callFunction(clzName = Config.commentRpcApiClz, obj = getIdeaListRequest)
    }

    //章节配图：RichText 正文是加密的 XHTML，图片以 <img src=.. img-width=.. img-height=../> 出现，
    //用户配图外面还套 <div data-fanqie-type="image">、后面跟 <p class="pictureDesc">配图说明</p>，出版书插图没有外层 div
    //para_index 按"图片之前的正文段落数"计，与 /content 的 content 按换行切分后的行号一致
    fun getChapterImages(itemId: String): Map<String, Any?> {
        val xhtml = getChapterRichContent(itemId)
        val images = mutableListOf<Map<String, Any?>>()
        if (!xhtml.isNullOrEmpty()) {
            //正文段落（不含图片行与图片说明行）的结束位置，用于定位图片插在哪一段之后
            val paragraphEnds = TEXT_P_REGEX.findAll(xhtml)
                .filterNot { it.groupValues[1].contains("class=\"picture") }
                .map { it.range.last }
                .toList()
            IMG_REGEX.findAll(xhtml).forEach { m ->
                val attrs = m.groupValues[1]
                val pos = m.range.first
                val caption = CAPTION_REGEX.find(xhtml, pos)?.let {
                    //说明行紧跟在图片之后（同一图片块内）才算
                    if (it.range.first - pos < 600) xmlUnescape(it.groupValues[1]) else null
                }
                images.add(
                    linkedMapOf(
                        "url" to xmlAttr(attrs, "src"),
                        "width" to xmlAttr(attrs, "img-width")?.toIntOrNull(),
                        "height" to xmlAttr(attrs, "img-height")?.toIntOrNull(),
                        "para_index" to paragraphEnds.count { it < pos },
                        "caption" to caption,
                    )
                )
            }
        }
        return linkedMapOf(
            "item_id" to itemId,
            "has_image" to images.isNotEmpty(),
            "images" to images,
        )
    }

    //取解密后的富文本 XHTML；密钥由宿主按 keyVersion 存在 MMKV，缺失时先让宿主解码一次以触发密钥注册
    private fun getChapterRichContent(itemId: String): String? {
        val request = "${Config.rpcModelPackage}.FullRequest".findClass(dragonClassLoader)
            .newInstance().apply {
                setObjectField("itemId", itemId)
                setObjectField("novelTextType", commentEnum("NovelTextType", "RichText"))
            }
        val item = callFunction(clzName = Config.readerFullRequestClz, obj = request)
            .getObjectField("data") ?: throw IllegalStateException("章节内容为空")
        val blob = item.getObjectField("content")?.toString().orEmpty()
        if (blob.isEmpty()) return null
        val keyVersion = (item.getObjectField("keyVersion") as? Int) ?: 0
        var key = readCryptKey(keyVersion)
        if (key.isNullOrEmpty()) {
            runCatching { decodeContent(item) }
            key = readCryptKey(keyVersion)
        }
        if (key.isNullOrEmpty()) throw IllegalStateException("章节密钥缺失，无法解析配图")
        val bytes = "com.dragon.read.reader.d.a".findClass(dragonClassLoader)
            .callStaticMethod("a", blob, key) as? ByteArray
        return bytes?.toString(Charsets.UTF_8)
    }

    private fun readCryptKey(keyVersion: Int): String? {
        val crypt = "com.dragon.read.reader.d.a".findClass(dragonClassLoader)
        val name = runCatching { crypt.callStaticMethod("a", keyVersion.toLong())?.toString() }
            .getOrNull() ?: "key_$keyVersion"
        return runCatching {
            "com.tencent.mmkv.MMKV".findClass(dragonClassLoader)
                .callStaticMethod("mmkvWithID", CRYPT_KEY_STORE)
                ?.callMethod("getString", name, null) as? String
        }.getOrNull()
    }

    private fun xmlAttr(attrs: String, name: String): String? =
        Regex("$name=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1)?.let { xmlUnescape(it) }

    private fun xmlUnescape(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")

    //枚举常量名在 rpc.model 下未被混淆，按名取；取不到时留空交由宿主按默认值处理
    private fun commentEnum(clzName: String, name: String): Any? {
        val value = "${Config.rpcModelPackage}.$clzName"
            .findClass(dragonClassLoader)
            .getStaticObjectFieldOrNull(name)
        if (value == null) log("枚举常量缺失：$clzName.$name")
        return value
    }

    fun bookMall(parameters: Map<String, MutableList<String>>): Any {
        val GetBookMallCellChangeRequest =
            "${Config.rpcModelPackage}.GetBookMallCellChangeRequest".findClass(dragonClassLoader)
        val getBookMallCellChangeRequest = GetBookMallCellChangeRequest.newInstance()
        parameters.forEach { (key, value) ->
            setField(getBookMallCellChangeRequest, key, value.first())
        }
        return callFunction(
            clzName = "${Config.rpcApiPackage}.a",
            obj = getBookMallCellChangeRequest
        )
    }

    fun newCategory(parameters: Map<String, MutableList<String>>): Any {
        val GetNewCategoryLandingPageRequest =
            "${Config.rpcModelPackage}.GetNewCategoryLandingPageRequest".findClass(dragonClassLoader)
        val getNewCategoryLandingPageRequest = GetNewCategoryLandingPageRequest.newInstance()
        parameters.forEach { (key, value) ->
            setField(getNewCategoryLandingPageRequest, key, value.first())
        }
        return callFunction(
            clzName = "${Config.rpcApiPackage}.a",
            obj = getNewCategoryLandingPageRequest
        )
    }

    //宿主 RPC 走 Cronet/QUIC，大响应偶发中断流错误；接口全部只读，失败自动重试一次
    private inline fun <T> withRetry(block: () -> T): T {
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            if (attempt > 0) runCatching { Thread.sleep(300) }
            try {
                return block()
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError!!
    }

    private fun callFunction(clzName: String, funcName: String = "a", obj: Any): Any {
        return withRetry {
            clzName.findClass(dragonClassLoader)
                .callStaticMethod(
                    funcName,
                    obj
                )!!.callMethod("blockingFirst")!!
        }
    }

    private fun setField(obj: Any, name: String, value: Any) {
        try {
            val fieldName = FiledNameUtils.underlineToCamel(name)
            val fieldValueStr = value.toString()
            when (val field = obj.getObjectField(fieldName)) {
                is Short -> obj.setShortField(fieldName, fieldValueStr.toShort())
                is Int -> obj.setIntField(fieldName, fieldValueStr.toInt())
                is Long -> obj.setLongField(fieldName, fieldValueStr.toLong())
                is Float -> obj.setFloatField(fieldName, fieldValueStr.toFloat())
                is Boolean -> obj.setBooleanField(fieldName, fieldValueStr.toBoolean())
                else -> {
                    val fieldClz = field?.javaClass ?: obj.findField(fieldName)?.type!!
                    if (fieldClz.isEnum) {
                        obj.setObjectField(
                            fieldName,
                            fieldClz.callStaticMethod("findByValue", fieldValueStr.toInt())
                        )
                    } else {
                        //String
                        obj.setObjectField(fieldName, fieldValueStr)
                    }
                }
            }
        } catch (e: Throwable) {
            log("Set field $name=$value error：\n${e.stackTraceToString()}")
        }
    }
}