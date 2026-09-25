package me.fycz.fqweb.web

/**
 * 需要以原始内容而非统一 JSON 包装返回的响应，例如解密后的富文本 XHTML。
 * HttpServer 见到它就直接吐 body，不套 ReturnData 的 JSON 外壳。
 */
class RawResponse(val body: String, val mimeType: String)
