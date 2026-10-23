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
import com.moodlife.app.domain.MixedStateForms
import com.moodlife.app.ui.theme.LocalMoodColors

/**
 * Psychoeducation on mixed states: Kraepelin's six forms along three axes
 * (mood, thinking, activity), with ICD codes. Reference text, not a diagnosis.
 */
private val FORMS = MixedStateForms.FORMS

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
            "На экране «Сегодня» приложение сверяет ваши отметки с этой таблицей: «Спад»/«Подъём» — " +
                "настроение, «Как работала голова» и симптомы вроде скачки мыслей — мышление, " +
                "«Сколько было сил» и двигательное беспокойство или упадок сил — активность. " +
                "Если оси расходятся, вы увидите, на какую форму это похоже. Это подсказка, не диагноз; " +
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
