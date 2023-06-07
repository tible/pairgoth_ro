package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.TeamTournament
import org.jeudego.pairgoth.web.Event
import org.jeudego.pairgoth.web.Event.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object TeamHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        if (tournament !is TeamTournament) badRequest("tournament is not a team tournament")
        return when (val pid = getSubSelector(request)?.toIntOrNull()) {
            null -> tournament.teams.values.map { it.toJson() }.toJsonArray()
            else -> tournament.teams[pid]?.toJson() ?: badRequest("no team with id #${pid}")
        }
    }

    override fun post(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        if (tournament !is TeamTournament) badRequest("tournament is not a team tournament")
        val payload = getObjectPayload(request)
        val team = tournament.teamFromJson(payload)
        tournament.teams[team.id] = team
        tournament.dispatchEvent(teamAdded, team.toJson())
        return Json.Object("success" to true, "id" to team.id)
    }

    override fun put(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        if (tournament !is TeamTournament) badRequest("tournament is not a team tournament")
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid player selector")
        val team = tournament.teams[id] ?: badRequest("invalid team id")
        val payload = getObjectPayload(request)
        val updated = tournament.teamFromJson(payload, team)
        tournament.teams[updated.id] = updated
        tournament.dispatchEvent(teamUpdated, team.toJson())
        return Json.Object("success" to true)
    }

    override fun delete(request: HttpServletRequest): Json {
        val tournament = getTournament(request)
        if (tournament !is TeamTournament) badRequest("tournament is not a team tournament")
        val id = getSubSelector(request)?.toIntOrNull() ?: badRequest("missing or invalid team selector")
        tournament.teams.remove(id) ?: badRequest("invalid team id")
        tournament.dispatchEvent(teamDeleted, Json.Object("id" to id))
        return Json.Object("success" to true)
    }
}