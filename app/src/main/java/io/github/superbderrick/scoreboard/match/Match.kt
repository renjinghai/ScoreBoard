package io.github.superbderrick.scoreboard.match

/** A best-of-N match. The first server alternates every game. */
class Match(val gamesToWin: Int, private val firstServerOfFirstGame: Side = Side.LEFT) {
    private val gameList = ArrayList<Game>()

    init {
        gameList.add(Game(firstServerOfFirstGame))
    }

    val games: List<Game> get() = gameList
    val currentGame: Game get() = gameList[gameList.size - 1]

    val leftGames: Int get() = gameList.count { it.winner == Side.LEFT }
    val rightGames: Int get() = gameList.count { it.winner == Side.RIGHT }

    val winner: Side?
        get() = when {
            leftGames >= gamesToWin -> Side.LEFT
            rightGames >= gamesToWin -> Side.RIGHT
            else -> null
        }

    val isOver: Boolean get() = winner != null

    val canStartNextGame: Boolean
        get() = currentGame.status == Game.Status.OVER && !isOver

    fun addRally(rally: Rally): Boolean = !isOver && currentGame.add(rally)

    fun startNextGame(): Boolean {
        if (!canStartNextGame) return false
        gameList.add(Game(currentGame.firstServer.other()))
        return true
    }

    /** Undoes the last ball; from an empty new game it steps back into the previous game. */
    fun undo(): Boolean {
        if (currentGame.ballsPlayed == 0 && gameList.size > 1) {
            gameList.removeAt(gameList.size - 1)
        }
        return currentGame.undo() != null
    }

    /** Switches who is serving now; later games keep alternating from the corrected server. */
    fun swapServer(): Boolean = currentGame.swapServer()

    fun encode(): String =
            "$gamesToWin|${firstServerOfFirstGame.name}|" + gameList.joinToString(";") { it.encodeWithServer() }

    companion object {
        fun decode(s: String): Match? {
            try {
                val parts = s.split("|", limit = 3)
                if (parts.size != 3) return null
                val match = Match(parts[0].toInt(), Side.valueOf(parts[1]))
                match.gameList.clear()
                var first = match.firstServerOfFirstGame
                parts[2].split(";").forEachIndexed { i, g ->
                    // "L:LR?" carries the first server; a bare "LR?" (older saves) alternates.
                    if (i > 0) first = first.other()
                    val colon = g.indexOf(':')
                    if (colon == 1) first = if (g[0] == 'L') Side.LEFT else Side.RIGHT
                    match.gameList.add(Game.decode(first, g.substring(colon + 1)))
                }
                return match
            } catch (e: Exception) {
                return null
            }
        }
    }
}
