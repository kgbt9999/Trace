package com.moodlife.app.domain

/**
 * Observational prodrome hints from marked symptoms — not a diagnosis.
 * Maps symptom names (seed catalog + common variants) to mania / depression signals.
 */
object ProdromeInference {

    data class SymptomSignal(
        val name: String,
        val severity: Int,
        val scaleMax: Int,
    )

    data class Hint(
        val direction: String,
        val title: String,
        val reasons: List<String>,
    )

    data class SignRef(
        val id: String,
        val name: String,
        val direction: String,
    )

    fun isMarked(severity: Int, scaleMax: Int): Boolean {
        if (severity <= 0) return false
        if (scaleMax <= 1) return severity >= 1
        return severity >= ((scaleMax + 1) / 2).coerceAtLeast(1)
    }

    fun hints(symptoms: List<SymptomSignal>): List<Hint> {
        val active = symptoms.filter { isMarked(it.severity, it.scaleMax) }
        if (active.isEmpty()) return emptyList()

        val mania = active.filter { directionOf(it.name) == "mania" }
        val dep = active.filter { directionOf(it.name) == "depression" }
        val out = mutableListOf<Hint>()
        if (mania.isNotEmpty() && dep.isNotEmpty()) {
            out += Hint(
                direction = "mixed",
                title = "По симптомам: смешанные ранние признаки",
                reasons = (mania + dep).map { it.name }.distinct(),
            )
        } else if (mania.isNotEmpty()) {
            out += Hint(
                direction = "mania",
                title = "По симптомам: признаки подъёма",
                reasons = mania.map { it.name },
            )
        } else if (dep.isNotEmpty()) {
            out += Hint(
                direction = "depression",
                title = "По симптомам: признаки спада",
                reasons = dep.map { it.name },
            )
        }
        return out
    }

    /** Warning-sign IDs that should be on, given current symptoms. */
    fun matchSignIds(signs: List<SignRef>, symptoms: List<SymptomSignal>): Set<String> {
        val active = symptoms.filter { isMarked(it.severity, it.scaleMax) }
        if (active.isEmpty()) return emptySet()
        val dirs = active.mapNotNull { directionOf(it.name) }.toSet()
        val result = mutableSetOf<String>()
        for (sign in signs) {
            if (sign.direction !in dirs && !(sign.direction == "mixed" && dirs.size >= 2)) continue
            if (signMatchesAnySymptom(sign.name, active.map { it.name })) {
                result += sign.id
            } else if (dirs.contains(sign.direction) && looseDirectionMatch(sign, active)) {
                // At least one strong symptom in this direction → light up related catalog signs
                result += sign.id
            }
        }
        // If mixed symptoms, prefer mixed-direction signs
        if ("mania" in dirs && "depression" in dirs) {
            signs.filter { it.direction == "mixed" }.forEach { result += it.id }
        }
        return result
    }

    fun directionOf(name: String): String? {
        val n = name.lowercase()
        val maniaKeys = listOf(
            "речь", "гонк", "мысли", "не сидит", "трат", "риск",
            "энерг", "раздраж", "бессон", "мало сн",
        )
        val depKeys = listOf(
            "интерес", "уход", "плакс", "туман", "безнад", "вин",
            "изоляц", "мотивац", "концентр", "долгий сон", "много сн",
        )
        if (maniaKeys.any { n.contains(it) }) return "mania"
        if (depKeys.any { n.contains(it) }) return "depression"
        return null
    }

    private fun signMatchesAnySymptom(signName: String, symptomNames: List<String>): Boolean {
        val s = signName.lowercase()
        return symptomNames.any { sym ->
            val n = sym.lowercase()
            tokens(s).any { t -> n.contains(t) } || tokens(n).any { t -> s.contains(t) }
        }
    }

    private fun looseDirectionMatch(sign: SignRef, active: List<SymptomSignal>): Boolean {
        val count = active.count { directionOf(it.name) == sign.direction }
        return count >= 2
    }

    private fun tokens(s: String): List<String> =
        s.split(' ', '/', ',', '.', '·', '-')
            .map { it.trim() }
            .filter { it.length >= 4 }
}
