package com.saffri.caloriecoach

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ActivityEntry(val id: Long, val name: String, val minutes: Int, val kcal: Int)

data class FoodEntry(
    val id: Long,
    val note: String,
    val photoPath: String?,
    val kcal: Int,
    val breakdown: String
)

data class DayLog(
    val activities: List<ActivityEntry> = emptyList(),
    val foods: List<FoodEntry> = emptyList()
) {
    val burned: Int get() = activities.sumOf { it.kcal }
    val intake: Int get() = foods.sumOf { it.kcal }
    val net: Int get() = intake - burned
}

/** Simple on-device storage (SharedPreferences + JSON). One log per calendar day. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("calorie_coach", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v.trim()).apply()

    fun loadProfile(): Profile? {
        val s = prefs.getString("profile", null) ?: return null
        return try {
            val o = JSONObject(s)
            Profile(
                o.getDouble("current"), o.getDouble("target"), o.getDouble("height"),
                o.getInt("age"), o.getBoolean("male")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveProfile(p: Profile) {
        val o = JSONObject()
            .put("current", p.currentWeightKg).put("target", p.targetWeightKg)
            .put("height", p.heightCm).put("age", p.age).put("male", p.isMale)
        prefs.edit().putString("profile", o.toString()).apply()
    }

    fun loadDay(date: String): DayLog {
        val s = prefs.getString("day_$date", null) ?: return DayLog()
        return try {
            val o = JSONObject(s)
            val acts = o.getJSONArray("activities").let { a ->
                (0 until a.length()).map { i ->
                    val x = a.getJSONObject(i)
                    ActivityEntry(x.getLong("id"), x.getString("name"), x.getInt("minutes"), x.getInt("kcal"))
                }
            }
            val foods = o.getJSONArray("foods").let { a ->
                (0 until a.length()).map { i ->
                    val x = a.getJSONObject(i)
                    FoodEntry(
                        x.getLong("id"), x.getString("note"),
                        if (x.isNull("photo")) null else x.getString("photo"),
                        x.getInt("kcal"), x.optString("breakdown", "")
                    )
                }
            }
            DayLog(acts, foods)
        } catch (e: Exception) {
            DayLog()
        }
    }

    fun saveDay(date: String, d: DayLog) {
        val acts = JSONArray()
        d.activities.forEach {
            acts.put(JSONObject().put("id", it.id).put("name", it.name).put("minutes", it.minutes).put("kcal", it.kcal))
        }
        val foods = JSONArray()
        d.foods.forEach {
            foods.put(
                JSONObject().put("id", it.id).put("note", it.note)
                    .put("photo", it.photoPath ?: JSONObject.NULL)
                    .put("kcal", it.kcal).put("breakdown", it.breakdown)
            )
        }
        prefs.edit().putString("day_$date", JSONObject().put("activities", acts).put("foods", foods).toString()).apply()
    }
}
