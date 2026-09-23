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
        val content = DragonService.getContent(itemId)
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
        returnData.setData(DragonService.getItemComments(itemId, bookId, page, count))
        return returnData
    }

    private fun checkCommentPage(page: Int, count: Int): String? = when {
        page < 1 -> "参数page不能小于1"
        count !in 1..MAX_COMMENT_COUNT -> "参数count需在1-${MAX_COMMENT_COUNT}之间"
        else -> null
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