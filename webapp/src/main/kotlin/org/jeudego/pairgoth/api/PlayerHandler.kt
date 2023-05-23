package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.web.Event
import org.jeudego.pairgoth.web.Event.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object PlayerHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        return when (val pid = getSubSelector(request)?.toIntOrNull()) {
            null -> tournament.pairables.values.map { it.toJson() }.toJsonArray()
            else -> tournament.pairables[pid]?.toJson() ?: badRequest("no player with id #${pid}")
        }
    }

    override fun post(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val payload = getObjectPayload(request)
        val player = Player.fromJson(payload)
        tournament.players[player.id] = player
        Event.dispatch(playerAdded, Json.Object("tournament" to tournament.id, "data" to player.toJson()))
        return Json.Object("success" to true, "id" to player.id)
    }

    override fun put(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid player selector")
        val player = tournament.players[id] ?: badRequest("invalid player id")
        val payload = getObjectPayload(request)
        val updated = Player.fromJson(payload, player)
        tournament.players[updated.id] = updated
        Event.dispatch(playerUpdated, Json.Object("tournament" to tournament.id, "data" to player.toJson()))
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid player selector")
        tournament.players.remove(id) ?: badRequest("invalid player id")
        Event.dispatch(playerDeleted, Json.Object("tournament" to tournament.id, "data" to id))
        return Json.Object("success" to true)
    }
}
