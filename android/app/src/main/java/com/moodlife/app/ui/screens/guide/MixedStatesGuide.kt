package com.moodlife.app.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moodlife.app.ui.theme.LocalMoodColors

/**
 * Psychoeducation on mixed states: Kraepelin's six forms along three axes
 * (mood, thinking, activity), with ICD codes. Reference text, not a diagnosis.
 */
private data class MixedForm(
    val name: String,
    val mood: Boolean,
    val thoughts: Boolean,
    val activity: Boolean,
    val description: String,
)

private val FORMS = listOf(
    MixedForm(
        "Депрессивная (тревожная) мания", mood = false, thoughts = true, activity = true,
        "Тоска и пустота внутри, но мысли несутся, человек суетлив и много говорит.",
    ),
    MixedForm(
        "Депрессивное возбуждение", mood = false, thoughts = false, activity = true,
        "Мысли вязкие, на душе тревога и безнадёжность, а тело не может успокоиться: хождение, нервные движения.",
    ),
    MixedForm(
        "Депрессия со скачкой мыслей", mood = false, thoughts = true, activity = false,
        "Идей много и они быстро сменяются, но сил нет даже встать; настроение подавленное.",
    ),
    MixedForm(
        "Маниакальный ступор", mood = true, thoughts = false, activity = false,
        "Приподнятое настроение при заторможенном мышлении и почти полной неподвижности.",
    ),
    MixedForm(
        "Заторможенная мания", mood = true, thoughts = true, activity = false,
        "В голове много планов, настроение приподнятое, но действовать физически не получается.",
    ),
    MixedForm(
        "Непродуктивная мания", mood = true, thoughts = false, activity = true,
        "Энергии много, настроение приподнятое, но мысли путаются — действия хаотичные и не дают результата.",
    ),
)

const val MIXED_STATES_TITLE = "Смешанные состояния"

@Composable
fun MixedStatesGuide() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Смешанное состояние — это когда признаки мании и депрессии есть одновременно " +
                "или сменяют друг друга за часы–дни. Такие эпизоды обычно тяжелее «чистых» фаз, " +
                "хуже отвечают на лечение и заметно повышают риск суицидального поведения — " +
                "это повод связаться с врачом раньше, а не ждать.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Э. Крепелин описал шесть форм по трём осям: настроение, мышление и активность. " +
                "Каждая ось может быть повышена или снижена независимо от других.",
            style = MaterialTheme.typography.bodyMedium,
        )
        AxesTable()
        FORMS.forEachIndexed { i, f ->
            Column {
                Text("${i + 1}. ${f.name}", style = MaterialTheme.typography.titleSmall)
                Text(
                    f.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text("Как это кодируется", style = MaterialTheme.typography.titleSmall)
        Text(
            "МКБ-10: F31.6 — биполярное аффективное расстройство, текущий эпизод смешанного характера. " +
                "Симптомы держатся не менее 2 недель, если лечение не прервало эпизод раньше.\n" +
                "МКБ-11: отдельного кода F31.6 нет; смешанный эпизод указывается в рамках биполярного " +
                "расстройства, например 6A60.9 — БАР I типа, текущий эпизод смешанный, без психотических симптомов.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "В дневнике смешанное состояние выглядит как одновременно высокие «Спад» и «Подъём» " +
                "или как спад вместе с ускоренными мыслями и беспокойством. Справка — не диагноз; " +
                "оценивает состояние врач.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AxesTable() {
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Форма", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.6f))
            listOf("Настроение", "Мысли", "Активность").forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FORMS.forEach { f ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    f.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1.6f).padding(end = 6.dp),
                )
                Direction(f.mood, Modifier.weight(1f))
                Direction(f.thoughts, Modifier.weight(1f))
                Direction(f.activity, Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Arrow plus a word, so the direction never depends on color alone. */
@Composable
private fun Direction(up: Boolean, modifier: Modifier) {
    val mood = LocalMoodColors.current
    val word = if (up) "выше" else "ниже"
    Row(
        modifier.clearAndSetSemantics { contentDescription = if (up) "повышено" else "снижено" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = null,
            tint = if (up) mood.elevated else mood.depressed,
            modifier = Modifier.size(18.dp),
        )
        Text(word, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp))
    }
}
