package com.calebnoble.slipperyseals.util

import com.calebnoble.slipperyseals.model.*
import org.json.JSONArray
import org.json.JSONObject

object JsonParser {

    fun parseLevelSet(json: String): List<LevelData> {
        val root = JSONObject(json)
        val levels = root.getJSONArray("levels")
        return (0 until levels.length()).map { parseLevel(levels.getJSONObject(it)) }
    }

    private fun parseLevel(obj: JSONObject): LevelData {
        val walls = parseArray(obj.getJSONArray("walls")) { parseWall(it) }
        val seals = parseArray(obj.getJSONArray("seals")) { parseSeal(it) }
        val destinations = parseArray(obj.getJSONArray("destinations")) { parseDestination(it) }
        val solution = if (obj.has("optimalSolution")) {
            parseArray(obj.getJSONArray("optimalSolution")) { parseMove(it) }
        } else {
            emptyList()
        }

        return LevelData(
            id = obj.getString("id"),
            rows = obj.getInt("rows"),
            cols = obj.getInt("cols"),
            walls = walls,
            seals = seals,
            destinations = destinations,
            optimalMoves = obj.getInt("optimalMoves"),
            optimalSolution = solution,
            instruction = obj.optString("instruction", null)
        )
    }

    private fun parseWall(obj: JSONObject): Wall = Wall(
        position = Position(obj.getInt("row"), obj.getInt("col")),
        side = Direction.valueOf(obj.getString("side"))
    )

    private fun parseSeal(obj: JSONObject): Seal = Seal(
        id = obj.getString("id"),
        color = SealColor.valueOf(obj.getString("color")),
        position = Position(obj.getInt("row"), obj.getInt("col"))
    )

    private fun parseDestination(obj: JSONObject): Destination = Destination(
        color = SealColor.valueOf(obj.getString("color")),
        position = Position(obj.getInt("row"), obj.getInt("col"))
    )

    private fun parseMove(obj: JSONObject): Move = Move(
        sealId = obj.getString("sealId"),
        direction = Direction.valueOf(obj.getString("direction"))
    )

    private fun <T> parseArray(arr: JSONArray, transform: (JSONObject) -> T): List<T> =
        (0 until arr.length()).map { transform(arr.getJSONObject(it)) }
}
