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

    fun getContent(itemId: String): Any {
        val FullRequest =
            "${Config.rpcModelPackage}.FullRequest".findClass(dragonClassLoader)
        val fullRequest = FullRequest.newInstance()
        fullRequest.setObjectField("itemId", itemId)
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

    fun getItemComments(itemId: String, bookId: String, page: Int, count: Int): Any {
        val GetCommentByItemIdRequest =
            "${Config.rpcModelPackage}.GetCommentByItemIdRequest".findClass(dragonClassLoader)
        val getCommentByItemIdRequest = GetCommentByItemIdRequest.newInstance()
        getCommentByItemIdRequest.setObjectField("itemId", itemId)
        getCommentByItemIdRequest.setObjectField("bookId", bookId)
        getCommentByItemIdRequest.setLongField("offset", (page - 1).toLong() * count)
        getCommentByItemIdRequest.setLongField("count", count.toLong())
        //needReply 才会带上每条评论的内联回复；queryCol 限定只取评论本身
        getCommentByItemIdRequest.setBooleanField("needReply", true)
        getCommentByItemIdRequest.setObjectField(
            "queryCol",
            commentEnum("QueryCollection", "OnlyComment")
        )
        return callFunction(clzName = Config.commentRpcApiClz, obj = getCommentByItemIdRequest)
    }

    //枚举常量名在 rpc.model 下未被混淆，按名取；取不到时留空交由宿主按默认值处理
    private fun commentEnum(clzName: String, name: String): Any? {
        val value = "${Config.rpcModelPackage}.$clzName"
            .findClass(dragonClassLoader)
            .getStaticObjectFieldOrNull(name)
        if (value == null) log("枚举常量缺失：$clzName.$name")
        return value
    }

    fun bookMall(parameters: Map<String, MutableList<String>>): Any {        val GetBookMallCellChangeRequest =
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