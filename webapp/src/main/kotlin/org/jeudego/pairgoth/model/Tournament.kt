package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import kotlinx.datetime.LocalDate
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.store.Store

data class Tournament(
    val id: Int,
    val type: Type,
    val name: String,
    val shortName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val country: String,
    val location: String,
    val online: Boolean,
    val timeSystem: TimeSystem,
    val pairing: Pairing,
    val rules: Rules = Rules.FRENCH,
    val gobanSize: Int = 19,
    val komi: Double = 7.5
) {
    companion object {}
    enum class Type(val playersNumber: Int) {
        INDIVIDUAL(1),
        PAIRGO(2),
        RENGO2(2),
        RENGO3(3),
        TEAM2(2),
        TEAM3(3),
        TEAM4(4),
        TEAM5(5);
    }

    enum class Criterion {
        NBW, MMS, SOS, SOSOS, SODOS
    }

    // pairables per id
    val pairables = mutableMapOf<Int, Pairable>()

    // pairing
    fun pair(round: Int, pairables: List<Pairable>): List<Game> {
        // Minimal check on round number.
        // CB TODO - the complete check should verify, for each player, that he was either non pairable or implied in the previous round
        if (round > games.size + 1) badRequest("previous round not paired")
        val evenPairables =
            if (pairables.size % 2 == 0) pairables
            else pairables.toMutableList()?.also { it.add(ByePlayer) }
        return pairing.pair(this, round, evenPairables)
    }

    // games per id for each round
    val games = mutableListOf<MutableMap<Int, Game>>()

    // standings criteria
    val criteria = mutableListOf<Criterion>(
        if (pairing.type == Pairing.PairingType.MACMAHON) Criterion.MMS else Criterion.NBW,
        Criterion.SOS,
        Criterion.SOSOS
    )
}

// Serialization

fun Tournament.Companion.fromJson(json: Json.Object, default: Tournament? = null) = Tournament(
    id = json.getInt("id") ?: default?.id ?: Store.nextTournamentId,
    type = json.getString("type")?.uppercase()?.let { Tournament.Type.valueOf(it) } ?: default?.type ?:  badRequest("missing type"),
    name = json.getString("name") ?: default?.name ?: badRequest("missing name"),
    shortName = json.getString("shortName") ?: default?.shortName ?: badRequest("missing shortName"),
    startDate = json.getLocalDate("startDate") ?: default?.startDate ?: badRequest("missing startDate"),
    endDate = json.getLocalDate("endDate") ?: default?.endDate ?: badRequest("missing endDate"),
    country = json.getString("country") ?: default?.country ?: badRequest("missing country"),
    location = json.getString("location") ?: default?.location ?: badRequest("missing location"),
    online = json.getBoolean("online") ?: default?.online ?: false,
    komi = json.getDouble("komi") ?: default?.komi ?: 7.5,
    rules = json.getString("rules")?.let { Rules.valueOf(it) } ?: default?.rules ?: Rules.FRENCH,
    gobanSize = json.getInt("gobanSize") ?: default?.gobanSize ?: 19,
    timeSystem = json.getObject("timeSystem")?.let { TimeSystem.fromJson(it) } ?: default?.timeSystem ?: badRequest("missing timeSystem"),
    pairing = json.getObject("pairing")?.let { Pairing.fromJson(it) } ?: default?.pairing ?: badRequest("missing pairing")
)

fun Tournament.toJson() = Json.Object(
    "id" to id,
    "type" to type.name,
    "name" to name,
    "shortName" to shortName,
    "startDate" to startDate.toString(),
    "endDate" to endDate.toString(),
    "country" to country,
    "location" to location,
    "online" to online,
    "komi" to komi,
    "rules" to rules.name,
    "gobanSize" to gobanSize,
    "timeSystem" to timeSystem.toJson(),
    "pairing" to pairing.toJson()
)
