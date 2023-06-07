package org.jeudego.pairgoth.model

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import kotlinx.datetime.LocalDate
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.store.Store
import kotlin.math.roundToInt

sealed class Tournament <P: Pairable>(
    val id: ID,
    val type: Type,
    val name: String,
    val shortName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val country: String,
    val location: String,
    val online: Boolean,
    val timeSystem: TimeSystem,
    val rounds: Int,
    val pairing: Pairing,
    val rules: Rules = Rules.FRENCH,
    val gobanSize: Int = 19,
    val komi: Double = 7.5
) {
    companion object {}
    enum class Type(val playersNumber: Int, val individual: Boolean = true) {
        INDIVIDUAL(1),
        PAIRGO(2, false),
        RENGO2(2, false),
        RENGO3(3, false),
        TEAM2(2),
        TEAM3(3),
        TEAM4(4),
        TEAM5(5);
    }

    enum class Criterion {
        NBW, MMS, SOS, SOSOS, SODOS
    }

    // players per id
    abstract val players: MutableMap<ID, Player>

    // pairables per id
    protected val _pairables = mutableMapOf<ID, P>()
    val pairables: Map<ID, Pairable> get() = _pairables

    // pairing
    fun pair(round: Int, pairables: List<Pairable>): List<Game> {
        // Minimal check on round number.
        // CB TODO - the complete check should verify, for each player, that he was either non pairable or implied in the previous round
        if (round > games.size + 1) badRequest("previous round not paired")
        if (round > rounds) badRequest("too many rounds")
        val evenPairables =
            if (pairables.size % 2 == 0) pairables
            else pairables.toMutableList().also { it.add(ByePlayer) }
        return pairing.pair(this, round, evenPairables).also { newGames ->
            if (games.size < round) games.add(mutableMapOf())
            games[round - 1].putAll( newGames.associateBy { it.id } )
        }
    }

    // games per id for each round
    private val games = mutableListOf<MutableMap<ID, Game>>()

    fun games(round: Int) = games.getOrNull(round - 1) ?:
        if (round > games.size + 1) throw Error("invalid round")
        else mutableMapOf<ID, Game>().also { games.add(it) }
    fun lastRound() = games.size

    // standings criteria
    val criteria = mutableListOf<Criterion>(
        if (pairing.type == Pairing.PairingType.MACMAHON) Criterion.MMS else Criterion.NBW,
        Criterion.SOS,
        Criterion.SOSOS
    )
}

// standard tournament of individuals
class StandardTournament(
    id: ID,
    type: Tournament.Type,
    name: String,
    shortName: String,
    startDate: LocalDate,
    endDate: LocalDate,
    country: String,
    location: String,
    online: Boolean,
    timeSystem: TimeSystem,
    rounds: Int,
    pairing: Pairing,
    rules: Rules = Rules.FRENCH,
    gobanSize: Int = 19,
    komi: Double = 7.5
): Tournament<Player>(id, type, name, shortName, startDate, endDate, country, location, online, timeSystem, rounds, pairing, rules, gobanSize, komi) {
    override val players get() = _pairables
}

// team tournament
class TeamTournament(
    id: ID,
    type: Tournament.Type,
    name: String,
    shortName: String,
    startDate: LocalDate,
    endDate: LocalDate,
    country: String,
    location: String,
    online: Boolean,
    timeSystem: TimeSystem,
    rounds: Int,
    pairing: Pairing,
    rules: Rules = Rules.FRENCH,
    gobanSize: Int = 19,
    komi: Double = 7.5
): Tournament<TeamTournament.Team>(id, type, name, shortName, startDate, endDate, country, location, online, timeSystem, rounds, pairing, rules, gobanSize, komi) {
    companion object {}
    override val players = mutableMapOf<ID, Player>()
    val teams: MutableMap<ID, Team> = _pairables

    inner class Team(id: ID, name: String): Pairable(id, name, 0, 0) {
        val playerIds = mutableSetOf<ID>()
        val teamPlayers: Set<Player> get() = playerIds.mapNotNull { players[id] }.toSet()
        override val rating: Int get() = if (teamPlayers.isEmpty()) super.rating else (teamPlayers.sumOf { player -> player.rating.toDouble() } / players.size).roundToInt()
        override val rank: Int get() = if (teamPlayers.isEmpty()) super.rank else (teamPlayers.sumOf { player -> player.rank.toDouble() } / players.size).roundToInt()
        override val club: String? get() = teamPlayers.map { club }.distinct().let { if (it.size == 1) it[0] else null }
        override val country: String? get() = teamPlayers.map { country }.distinct().let { if (it.size == 1) it[0] else null }
        override fun toJson() = Json.Object(
            "id" to id,
            "name" to name,
            "players" to playerIds.toList().toJsonArray()
        )
        val teamOfIndividuals: Boolean get() = type.individual
    }

    fun teamFromJson(json: Json.Object, default: TeamTournament.Team? = null) = Team(
        id = json.getInt("id") ?: default?.id ?: Store.nextPlayerId,
        name = json.getString("name") ?: default?.name ?: badRequest("missing name")
    ).apply {
        json.getArray("players")?.let { arr ->
            arr.mapTo(playerIds) {
                if (it != null && it is Number) it.toInt().also { id -> players.containsKey(id) }
                else badRequest("invalid players array")
            }
        } ?: badRequest("missing players")
    }

}

// Serialization

fun Tournament.Companion.fromJson(json: Json.Object, default: Tournament<*>? = null): Tournament<*> {
    val type = json.getString("type")?.uppercase()?.let { Tournament.Type.valueOf(it) } ?: default?.type ?:  badRequest("missing type")
    // No clean way to avoid this redundancy
    val tournament = if (type.playersNumber == 1)
            StandardTournament(
                id = json.getInt("id") ?: default?.id ?: Store.nextTournamentId,
                type = type,
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
                rounds = json.getInt("rounds") ?: default?.rounds ?: badRequest("missing rounds"),
                pairing = json.getObject("pairing")?.let { Pairing.fromJson(it) } ?: default?.pairing ?: badRequest("missing pairing")
            )
        else
            TeamTournament(
                id = json.getInt("id") ?: default?.id ?: Store.nextTournamentId,
                type = type,
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
                rounds = json.getInt("rounds") ?: default?.rounds ?: badRequest("missing rounds"),
                pairing = json.getObject("pairing")?.let { Pairing.fromJson(it) } ?: default?.pairing ?: badRequest("missing pairing")
            )
    json["pairables"]?.let { pairables ->

    }
    return tournament
}

fun Tournament<*>.toJson() = Json.Object(
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
    "rounds" to rounds,
    "pairing" to pairing.toJson()
)