package com.moodlife.app.domain

import org.json.JSONArray
import org.json.JSONObject

/** Named crisis quick-dial contacts (label + phone). User-controlled; ACTION_DIAL only. */
data class CrisisContact(
    val id: String,
    val label: String,
    val phone: String,
) {
    fun telUri(): String {
        val digits = phone.filter { it.isDigit() || it == '+' }
        return "tel:$digits"
    }
}

object CrisisContacts {
    const val KEY = "crisis_contacts_json"

    fun parse(raw: String?): List<CrisisContact> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val label = o.optString("label").trim()
                val phone = o.optString("phone").trim()
                if (label.isEmpty() || phone.isEmpty()) return@mapNotNull null
                CrisisContact(
                    id = o.optString("id").ifBlank { "c$i" },
                    label = label,
                    phone = phone,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serialize(list: List<CrisisContact>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("label", c.label)
                    .put("phone", c.phone),
            )
        }
        return arr.toString()
    }

    fun extractPhone(text: String): String? {
        val m = Regex("""(\+?\d[\d\s\-()]{5,}\d)""").find(text) ?: return null
        return m.groupValues[1].trim()
    }
}
