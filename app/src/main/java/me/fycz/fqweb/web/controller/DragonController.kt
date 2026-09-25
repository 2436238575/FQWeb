package me.fycz.fqweb.web.controller

import me.fycz.fqweb.utils.getObjectField
import me.fycz.fqweb.web.ReturnData
import me.fycz.fqweb.web.service.DragonService


/**
 * @author fengyue
 * @date 2023/5/29 18:04
 * @description
 */
object DragonController {

    private const val DEFAULT_COMMENT_COUNT = 20
    private const val MAX_COMMENT_COUNT = 50
    private val COMMENT_SORTS = listOf("smart_hot", "time")
    private const val ITEM_VERSION_TIP =
        "参数item_version不能为空，取自/content返回的data.data.novel_data.version"

    //段评排序：URL 用下划线风格，映射到宿主 CommentSortType 的枚举常量名
    //实测服务端只区分 hot 与按时间倒序，TimeAsc/ReplyTimeDesc 结果与 time_desc 相同，故不暴露
    private val PARA_SORTS = linkedMapOf(
        "hot" to "Hot",
        "time_desc" to "TimeDesc",
    )

    fun search(parameters: Map<String, List<String>>): ReturnData {
        val keyword = parameters["query"]?.firstOrNull()
        val page = parameters["page"]?.firstOrNull()?.toIntOrNull() ?: 1
        val returnData = ReturnData()
        if (keyword.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数query不能为空")
        }
        returnData.setData(DragonService.search(keyword, page))
        return returnData
    }

    fun info(parameters: Map<String, MutableList<String>>): ReturnData {
        val bookId = parameters["book_id"]?.firstOrNull()?.toLongOrNull()
        val returnData = ReturnData()
        if (bookId == null) {
            return returnData.setErrorMsg("参数book_id不能为空或非法")
        }
        returnData.setData(DragonService.getInfo(bookId))
        return returnData
    }

    fun catalog(parameters: Map<String, MutableList<String>>): ReturnData {
        val bookId = parameters["book_id"]?.firstOrNull()?.toLongOrNull()
        val returnData = ReturnData()
        if (bookId == null) {
            return returnData.setErrorMsg("参数book_id不能为空或非法")
        }
        returnData.setData(DragonService.getCatalog(bookId))
        return returnData
    }

    fun content(parameters: Map<String, MutableList<String>>): ReturnData {
        val itemId = parameters["item_id"]?.firstOrNull()
        val returnData = ReturnData()
        if (itemId.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数item_id不能为空")
        }
        val content = DragonService.getContent(itemId, parameters["text_type"]?.firstOrNull())
        val data = content.getObjectField("data") ?: return returnData.setErrorMsg("章节内容为空")
        DragonService.decodeContent(data)
        returnData.setData(content)
        return returnData
    }

    fun comment(parameters: Map<String, List<String>>): ReturnData {
        val bookId = parameters["book_id"]?.firstOrNull()
        val page = parameters["page"]?.firstOrNull()?.toIntOrNull() ?: 1
        val count = parameters["count"]?.firstOrNull()?.toIntOrNull() ?: DEFAULT_COMMENT_COUNT
        val sort = parameters["sort"]?.firstOrNull() ?: COMMENT_SORTS.first()
        val sessionId = parameters["session_id"]?.firstOrNull()
        val returnData = ReturnData()
        if (bookId.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数book_id不能为空")
        }
        checkCommentPage(page, count)?.let { return returnData.setErrorMsg(it) }
        if (sort !in COMMENT_SORTS) {
            return returnData.setErrorMsg("参数sort仅支持${COMMENT_SORTS.joinToString("/")}")
        }
        returnData.setData(DragonService.getBookComments(bookId, page, count, sort, sessionId))
        return returnData
    }

    fun itemComment(parameters: Map<String, List<String>>): ReturnData {
        val itemId = parameters["item_id"]?.firstOrNull()
        val bookId = parameters["book_id"]?.firstOrNull()
        val page = parameters["page"]?.firstOrNull()?.toIntOrNull() ?: 1
        val count = parameters["count"]?.firstOrNull()?.toIntOrNull() ?: DEFAULT_COMMENT_COUNT
        val returnData = ReturnData()
        if (itemId.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数item_id不能为空")
        }
        if (bookId.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数book_id不能为空")
        }
        checkCommentPage(page, count)?.let { return returnData.setErrorMsg(it) }
        val sort = parameters["sort"]?.firstOrNull() ?: COMMENT_SORTS.first()
        if (sort !in COMMENT_SORTS) {
            return returnData.setErrorMsg("参数sort仅支持${COMMENT_SORTS.joinToString("/")}")
        }
        returnData.setData(DragonService.getItemComments(itemId, bookId, page, count, sort))
        return returnData
    }

    fun paraComment(parameters: Map<String, List<String>>): ReturnData {
        val bookId = parameters["book_id"]?.firstOrNull()
        val itemId = parameters["item_id"]?.firstOrNull()
        val itemVersion = parameters["item_version"]?.firstOrNull()
        val paraIndex = parameters["para_index"]?.firstOrNull()?.toIntOrNull()
        val page = parameters["page"]?.firstOrNull()?.toIntOrNull() ?: 1
        val count = parameters["count"]?.firstOrNull()?.toIntOrNull() ?: DEFAULT_COMMENT_COUNT
        val returnData = ReturnData()
        if (bookId.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数book_id不能为空")
        }
        if (itemId.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数item_id不能为空")
        }
        if (itemVersion.isNullOrEmpty()) {
            return returnData.setErrorMsg(ITEM_VERSION_TIP)
        }
        if (paraIndex == null || paraIndex < 0) {
            return returnData.setErrorMsg("参数para_index不能为空或非法")
        }
        checkCommentPage(page, count)?.let { return returnData.setErrorMsg(it) }
        val sort = parameters["sort"]?.firstOrNull()
        if (sort != null && PARA_SORTS[sort] == null) {
            return returnData.setErrorMsg("参数sort仅支持${PARA_SORTS.keys.joinToString("/")}")
        }
        returnData.setData(
            DragonService.getParaComments(
                bookId, itemId, paraIndex, page, count, itemVersion,
                sort = sort?.let { PARA_SORTS[it] },
            )
        )
        return returnData
    }

    fun paraCommentList(parameters: Map<String, List<String>>): ReturnData {
        val bookId = parameters["book_id"]?.firstOrNull()
        val itemId = parameters["item_id"]?.firstOrNull()
        val itemVersion = parameters["item_version"]?.firstOrNull()
        val returnData = ReturnData()
        if (bookId.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数book_id不能为空")
        }
        if (itemId.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数item_id不能为空")
        }
        if (itemVersion.isNullOrEmpty()) {
            return returnData.setErrorMsg(ITEM_VERSION_TIP)
        }
        returnData.setData(DragonService.getParaCommentIndex(bookId, itemId, itemVersion))
        return returnData
    }

    private fun checkCommentPage(page: Int, count: Int): String? = when {
        page < 1 -> "参数page不能小于1"
        count !in 1..MAX_COMMENT_COUNT -> "参数count需在1-${MAX_COMMENT_COUNT}之间"
        else -> null
    }





    fun contentImage(parameters: Map<String, List<String>>): ReturnData {
        val itemId = parameters["item_id"]?.firstOrNull()
        val returnData = ReturnData()
        if (itemId.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数item_id不能为空")
        }
        returnData.setData(DragonService.getChapterImages(itemId))
        return returnData
    }

    fun bookMall(parameters: Map<String, MutableList<String>>): ReturnData {
        val returnData = ReturnData()
        returnData.setData(DragonService.bookMall(parameters))
        return returnData
    }

    fun newCategory(parameters: Map<String, MutableList<String>>): ReturnData {
        val returnData = ReturnData()
        returnData.setData(DragonService.newCategory(parameters))
        return returnData
    }

}