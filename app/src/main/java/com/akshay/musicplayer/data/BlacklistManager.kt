package com.akshay.musicplayer.data

import android.content.SharedPreferences
import com.akshay.musicplayer.domain.models.BlacklistRule
import com.akshay.musicplayer.domain.models.BlacklistTargetType
import com.akshay.musicplayer.domain.models.TrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class BlacklistManager(private val sharedPreferences: SharedPreferences) {
    private val _rules = MutableStateFlow(readRules())
    val rules: StateFlow<List<BlacklistRule>> = _rules.asStateFlow()

    fun addRule(rule: BlacklistRule) {
        val updated = _rules.value.filterNot { it.id == rule.id } + rule
        saveRules(updated)
    }

    fun removeRule(ruleId: String) {
        saveRules(_rules.value.filterNot { it.id == ruleId })
    }

    fun isBlocked(track: TrackEntity, minuteOfDay: Int = currentMinuteOfDay()): Boolean =
        _rules.value.any { it.matches(track, minuteOfDay) }

    fun currentMinuteOfDay(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    private fun saveRules(rules: List<BlacklistRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("targetType", rule.targetType.name)
                    .put("value", rule.value)
                    .put("artistFilter", rule.artistFilter)
                    .put("trackId", rule.trackId ?: JSONObject.NULL)
                    .put("trackDurationMs", rule.trackDurationMs ?: JSONObject.NULL)
                    .put("startMinute", rule.startMinute ?: JSONObject.NULL)
                    .put("endMinute", rule.endMinute ?: JSONObject.NULL)
            )
        }
        sharedPreferences.edit().putString(PREF_BLACKLIST_RULES, array.toString()).apply()
        _rules.value = rules
    }

    private fun readRules(): List<BlacklistRule> {
        val source = sharedPreferences.getString(PREF_BLACKLIST_RULES, null) ?: return emptyList()
        return try {
            val array = JSONArray(source)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val targetType = runCatching {
                        BlacklistTargetType.valueOf(item.optString("targetType"))
                    }.getOrNull() ?: continue
                    val value = item.optString("value").trim()
                    if (value.isBlank()) continue
                    add(
                        BlacklistRule(
                            id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                            targetType = targetType,
                            value = value,
                            artistFilter = item.optString("artistFilter"),
                            trackId = item.optNullableLong("trackId"),
                            trackDurationMs = item.optNullableLong("trackDurationMs"),
                            startMinute = item.optNullableInt("startMinute"),
                            endMinute = item.optNullableInt("endMinute")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key).takeIf { it in 0..1439 }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0L }

    private companion object {
        const val PREF_BLACKLIST_RULES = "blacklist_rules"
    }
}
