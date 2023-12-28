package org.jeudego.pairgoth.web

import com.republicate.kson.Json
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class LoginServlet: HttpServlet() {

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val contentType = req.contentType
            val sep = contentType.indexOf(';')
            val mimeType = if (sep == -1) contentType else contentType.substring(0, sep).trim { it <= ' ' }
            if (!isJson(mimeType)) throw Error("expecting json")
            val payload = Json.Companion.parse(req.reader.readText())?.asObject() ?: throw Error("null json")
            val user = when (WebappManager.getProperty("auth")) {
                "sesame" -> checkSesame(payload)
                else -> null
            } ?: throw Error("authentication failed")
            req.session.setAttribute("logged", user)
            val ret = Json.Object("status" to "ok")
            resp.contentType = "application/json"
            resp.writer.println(ret.toString())
        } catch (t: Throwable) {
            logger.error("exception while logging in", t)
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST)
        }
    }

    fun checkSesame(payload: Json.Object): Boolean? {
        val expected = WebappManager.getProperty("auth.sesame") ?: throw Error("sesame wrongly configured")
        return if (payload.getString("sesame")?.equals(expected) == true) true else null
    }

    companion object {
        fun isJson(mimeType: String) = "text/json" == mimeType || "application/json" == mimeType || mimeType.endsWith("+json")
        val logger = LoggerFactory.getLogger("login")
    }
}