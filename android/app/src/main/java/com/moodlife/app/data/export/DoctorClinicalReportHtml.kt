package com.moodlife.app.data.export

/**
 * HTML shell matching the attached clinical-report reference layout.
 * Content is observational self-report only — no invented diagnoses or therapy advice.
 */
internal object DoctorClinicalReportHtml {

    fun render(m: Model): String = """
    <!DOCTYPE html>
    <html lang="ru">
    <head>
      <meta charset="utf-8"/>
      <meta name="viewport" content="width=device-width, initial-scale=1"/>
      <title>${esc(m.title)}</title>
      <style>
        :root {
          --bg: #f7f8fa; --surface: #ffffff; --surface-2: #f0f2f5; --border: #e2e6ed;
          --text: #1a1d23; --text-2: #5a6070; --text-3: #8b919f;
          --mood-5p: #8B2020; --mood-4p: #c0392b; --mood-3p: #e55d3c; --mood-2p: #f0944a; --mood-1p: #f5c09a;
          --mood-0: #b0b8c8;
          --mood-1m: #a8c8e8; --mood-2m: #6aa3d0; --mood-3m: #3a7fba; --mood-4m: #1e5a9a; --mood-5m: #0f3060;
          --green: #1a9e6e; --yellow: #e8a020; --red: #c8342a; --blue: #2a78d6; --orange: #eb6834;
          --r: 6px; --r-lg: 12px;
          --shadow: 0 1px 4px rgba(0,0,0,.07), 0 4px 16px rgba(0,0,0,.05);
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
          font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
          background: var(--bg); color: var(--text); font-size: 13px; line-height: 1.55;
          -webkit-font-smoothing: antialiased;
        }
        .report-header {
          background: var(--surface); border-bottom: 2px solid var(--border);
          padding: 24px 40px 20px; display: grid; grid-template-columns: 1fr auto; gap: 16px; align-items: start;
        }
        .report-header h1 { font-size: 17px; font-weight: 600; letter-spacing: -.2px; margin-bottom: 2px; }
        .report-header .subtitle { font-size: 12px; color: var(--text-2); font-weight: 400; }
        .report-meta { text-align: right; font-size: 11.5px; color: var(--text-2); line-height: 1.8; }
        .report-meta strong { color: var(--text); font-weight: 500; }
        .patient-row {
          background: var(--surface); border-bottom: 1px solid var(--border);
          padding: 12px 40px; display: flex; gap: 40px; flex-wrap: wrap;
        }
        .patient-row .field { display: flex; flex-direction: column; gap: 1px; }
        .patient-row .label { font-size: 10px; color: var(--text-3); text-transform: uppercase; letter-spacing: .5px; }
        .patient-row .value { font-size: 13px; font-weight: 500; }
        .page { max-width: 1060px; margin: 0 auto; padding: 28px 40px 60px; }
        .section { margin-bottom: 36px; }
        .section-title {
          font-size: 11px; font-weight: 600; letter-spacing: .6px; color: var(--text-3);
          text-transform: uppercase; margin-bottom: 14px; padding-bottom: 8px;
          border-bottom: 1px solid var(--border); display: flex; align-items: center; gap: 8px;
        }
        .section-title .num {
          background: var(--blue); color: #fff; font-size: 10px; width: 18px; height: 18px;
          border-radius: 50%; display: grid; place-items: center;
        }
        .card {
          background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-lg);
          box-shadow: var(--shadow); padding: 20px 24px;
        }
        .card-title {
          font-size: 12px; font-weight: 600; color: var(--text); margin-bottom: 14px;
          display: flex; align-items: center; justify-content: space-between; gap: 12px;
        }
        .card-note {
          font-size: 11px; color: var(--text-2); margin-top: 10px; padding-top: 10px;
          border-top: 1px solid var(--border); font-style: italic;
        }
        .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
        .grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
        .metric-card {
          background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-lg);
          padding: 16px 20px; box-shadow: var(--shadow);
        }
        .metric-card .m-label { font-size: 10.5px; color: var(--text-2); margin-bottom: 4px; }
        .metric-card .m-value {
          font-size: 26px; font-weight: 600; line-height: 1; margin-bottom: 3px;
          font-variant-numeric: tabular-nums;
        }
        .metric-card .m-sub { font-size: 11px; color: var(--text-2); }
        .metric-card.ok .m-value { color: var(--green); }
        .metric-card.warn .m-value { color: var(--yellow); }
        .metric-card.bad .m-value { color: var(--red); }
        .risk-card {
          border-radius: var(--r-lg); padding: 14px 18px; display: flex; align-items: center; gap: 14px; border: 1px solid;
        }
        .risk-card.low  { background: #edfaf4; border-color: #a4dfc4; }
        .risk-card.mid  { background: #fef8ec; border-color: #f5d080; }
        .risk-card.high { background: #fef0ef; border-color: #f5aba8; }
        .risk-dot { width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0; }
        .risk-card.low  .risk-dot { background: var(--green); }
        .risk-card.mid  .risk-dot { background: var(--yellow); }
        .risk-card.high .risk-dot { background: var(--red); }
        .risk-label { font-weight: 600; font-size: 13px; }
        .risk-desc  { font-size: 11.5px; color: var(--text-2); }
        .badge { display: inline-block; padding: 2px 8px; border-radius: 20px; font-size: 11px; font-weight: 500; }
        .badge-dep  { background: #ddeeff; color: #1a4f8a; }
        .badge-man  { background: #fde8e8; color: #8B2020; }
        .badge-hyp  { background: #fff0e0; color: #9a4000; }
        .badge-eut  { background: #e6f9ee; color: #0d6e38; }
        .badge-mix  { background: #f0e8f8; color: #5a2080; }
        .heatmap-wrap { overflow-x: auto; }
        .heatmap { display: inline-grid; gap: 3px; }
        .heatmap-day { width: 15px; height: 15px; border-radius: 3px; }
        .hm-legend {
          display: flex; align-items: center; gap: 4px; margin-top: 10px; font-size: 10.5px; color: var(--text-2);
        }
        .hm-swatch { width: 13px; height: 13px; border-radius: 2px; }
        .trigger-item {
          display: flex; align-items: center; gap: 10px; padding: 7px 0; border-bottom: 1px solid var(--border);
        }
        .trigger-item:last-child { border-bottom: none; }
        .trig-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
        .trig-name { flex: 1; font-size: 12.5px; }
        .trig-date { font-size: 11px; color: var(--text-3); font-variant-numeric: tabular-nums; }
        .prodrome-item { margin-bottom: 10px; }
        .prodrome-label { display: flex; justify-content: space-between; margin-bottom: 3px; font-size: 12px; }
        .prodrome-bar-bg { background: var(--surface-2); border-radius: 20px; height: 8px; overflow: hidden; }
        .prodrome-bar-fill { height: 100%; border-radius: 20px; }
        .med-table, .hist-table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
        .med-table th, .hist-table th {
          background: var(--surface-2); font-weight: 500; padding: 8px 12px; text-align: left;
          font-size: 11px; color: var(--text-2); border-bottom: 1px solid var(--border);
        }
        .med-table td, .hist-table td { padding: 9px 12px; border-bottom: 1px solid var(--border); vertical-align: top; }
        .med-table tr:last-child td, .hist-table tr:last-child td { border-bottom: none; }
        .med-table tr:nth-child(even) td, .hist-table tr:nth-child(even) td { background: #fafbfc; }
        .crisis-levels { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
        .crisis-level { border-radius: var(--r); padding: 14px 16px; border: 1px solid; }
        .crisis-level.green { background: #edfaf4; border-color: #a4dfc4; }
        .crisis-level.yellow { background: #fef8ec; border-color: #f5d080; }
        .crisis-level.red { background: #fef0ef; border-color: #f5aba8; }
        .crisis-level .cl-title { font-weight: 600; font-size: 12.5px; margin-bottom: 8px; display: flex; align-items: center; gap: 6px; }
        .crisis-level .cl-dot { width: 9px; height: 9px; border-radius: 50%; }
        .crisis-level.green .cl-dot { background: var(--green); }
        .crisis-level.yellow .cl-dot { background: var(--yellow); }
        .crisis-level.red .cl-dot { background: var(--red); }
        .crisis-level ul { list-style: none; }
        .crisis-level ul li { font-size: 11.5px; padding: 2.5px 0; color: var(--text); }
        .crisis-level ul li::before { content: "→ "; color: var(--text-3); }
        .report-footer {
          border-top: 1px solid var(--border); padding-top: 18px; margin-top: 10px;
          font-size: 11px; color: var(--text-3); display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px;
        }
        .chart { overflow-x: auto; }
        .disclaimer {
          border-left: 3px solid var(--orange); background: var(--surface);
          padding: 12px 16px; margin-bottom: 20px; font-size: 12px; color: var(--text-2); white-space: pre-line;
        }
        @media print {
          body { background: #fff; }
          .card, .metric-card { box-shadow: none; }
          .page { padding: 0 20px 40px; }
          .report-header { padding: 16px 20px; }
        }
        @media (max-width: 800px) {
          .grid-2, .grid-3, .crisis-levels { grid-template-columns: 1fr; }
          .report-header { grid-template-columns: 1fr; padding: 16px 20px; }
          .report-meta { text-align: left; }
          .patient-row { padding: 12px 20px; gap: 16px; }
          .page { padding: 20px 16px 40px; }
        }
      </style>
    </head>
    <body>
      <div class="report-header">
        <div>
          <h1>Клинический отчёт пациента</h1>
          <div class="subtitle">${esc(m.subtitle)}</div>
        </div>
        <div class="report-meta">
          <div>Период отчёта: <strong>${esc(m.periodLabel)}</strong></div>
          <div>Сформирован: <strong>${esc(m.exportedAt)}</strong></div>
          <div>Источник: <strong>Trace (локальный дневник)</strong></div>
        </div>
      </div>

      <div class="patient-row">
        <div class="field"><span class="label">Пациент</span><span class="value">${esc(m.patientName)}</span></div>
        <div class="field"><span class="label">Дата рождения</span><span class="value">${esc(m.patientDob)}</span></div>
        <div class="field"><span class="label">Диагноз</span><span class="value">${esc(m.diagnosis)}</span></div>
        <div class="field"><span class="label">Лечащий врач</span><span class="value">${esc(m.doctorName)}</span></div>
        <div class="field"><span class="label">Следующий приём</span><span class="value">${esc(m.nextVisit)}</span></div>
        <div class="field"><span class="label">Приверженность лечению</span><span class="value" style="color:${m.adherenceColor}">${esc(m.adherenceLabel)}</span></div>
      </div>

      <div class="page">
        <div class="disclaimer">${esc(m.disclaimer)}</div>

        <div class="section">
          <div class="section-title"><span class="num">↑</span> Ключевые показатели периода</div>
          <div class="grid-3" style="margin-bottom:14px">
            <div class="metric-card ${m.moodCardClass}">
              <div class="m-label">Среднее настроение</div>
              <div class="m-value">${esc(m.avgPolarity)}</div>
              <div class="m-sub">Диапазон: ${esc(m.polarityRange)} · шкала −5…+5</div>
            </div>
            <div class="metric-card ${m.sleepCardClass}">
              <div class="m-label">Нарушений сна (дней)</div>
              <div class="m-value">${m.sleepBadDays}</div>
              <div class="m-sub">Из ${m.totalDays} дней (${m.sleepBadPct}%) · сон &lt;6 ч</div>
            </div>
            <div class="metric-card ${m.medCardClass}">
              <div class="m-label">Приём лекарств</div>
              <div class="m-value">${esc(m.adherenceLabel)}</div>
              <div class="m-sub">${esc(m.medMissesSub)}</div>
            </div>
          </div>
          <div class="risk-card ${m.riskLevel}">
            <div class="risk-dot"></div>
            <div>
              <div class="risk-label">${esc(m.riskTitle)}</div>
              <div class="risk-desc">${esc(m.riskDesc)}</div>
            </div>
          </div>
        </div>

        <div class="section">
          <div class="section-title"><span class="num">1</span> Динамика настроения — ${m.totalDays} ${dayWord(m.totalDays)}</div>
          <div class="card">
            <div class="card-title">Шкала −5…+5 по дням (красная зона: подъём ≥+2; синяя: спад ≤−2)</div>
            <div class="chart">${m.polaritySvg.ifBlank { "<p style=\"color:var(--text-2)\">Нет данных.</p>" }}</div>
            <div class="card-note">${esc(m.moodNote)}</div>
          </div>
        </div>

        <div class="section">
          <div class="section-title"><span class="num">2</span> Сон и настроение — двойная ось</div>
          <div class="card">
            <div class="card-title">
              <span>Часы сна (столбцы) + настроение (линия)</span>
              <span style="font-size:11px;color:var(--text-2);font-weight:400">Ориентир сна: 7–9 ч</span>
            </div>
            <div class="chart">${m.sleepMoodSvg.ifBlank { "<p style=\"color:var(--text-2)\">Нет данных о сне.</p>" }}</div>
            <div class="card-note">${esc(m.sleepNote)}</div>
          </div>
        </div>

        <div class="section grid-2">
          <div>
            <div class="section-title"><span class="num">3</span> Профиль симптомов — последние 7 дней</div>
            <div class="card" style="height:calc(100% - 36px)">
              <div class="card-title">Радарная диаграмма (средние за неделю)</div>
              <div class="chart" style="text-align:center">${m.radarSvg.ifBlank { "<p style=\"color:var(--text-2)\">Нет данных.</p>" }}</div>
              <div class="card-note">${esc(m.radarNote)}</div>
            </div>
          </div>
          <div>
            <div class="section-title"><span class="num">4</span> Тепловая карта настроения</div>
            <div class="card" style="height:calc(100% - 36px)">
              <div class="card-title">Цвет = полярность по дням (${esc(m.periodLabel)})</div>
              ${m.heatmapHtml}
              <div class="card-note">${esc(m.heatmapNote)}</div>
            </div>
          </div>
        </div>

        <div class="section">
          <div class="section-title"><span class="num">5</span> Триггеры и ранние признаки</div>
          <div class="grid-2">
            <div class="card">
              <div class="card-title">Наблюдаемые триггеры (из дневника)</div>
              ${m.triggersHtml.ifBlank { "<p style=\"color:var(--text-2)\">Нет отмеченных триггеров за период.</p>" }}
            </div>
            <div class="card">
              <div class="card-title">Ранние признаки (частота отметок)</div>
              ${m.prodromeHtml.ifBlank { "<p style=\"color:var(--text-2)\">Нет отметок ранних признаков.</p>" }}
              <div class="card-note">Доля дней с отметкой. Не вероятность эпизода и не диагноз.</div>
            </div>
          </div>
        </div>

        <div class="section">
          <div class="section-title"><span class="num">6</span> Приём лекарств — период отчёта</div>
          <div class="card">
            <div class="card-title">
              <span>Схема терапии и приверженность</span>
              <span style="font-size:11px;color:var(--text-2);font-weight:400">Самоотчёт · не совет по терапии</span>
            </div>
            ${if (m.medTableRows.isNotBlank()) """
            <table class="med-table">
              <thead><tr>
                <th>Препарат</th><th>Доза</th><th>Частота</th>
                <th>Принято / слотов</th><th>Пропуски</th><th>Приверженность</th>
              </tr></thead>
              <tbody>${m.medTableRows}</tbody>
            </table>
            """ else "<p style=\"color:var(--text-2)\">Нет записей приёма за период.</p>"}
            <div class="card-note">${esc(m.medNote)}</div>
          </div>
          ${if (m.medDoseSvg.isNotBlank()) """
          <div class="card" style="margin-top:18px">
            <div class="card-title">Дозы по дням (мг)</div>
            <div class="chart">${m.medDoseSvg}</div>
          </div>
          """ else ""}
        </div>

        ${if (m.historyRows.isNotBlank()) """
        <div class="section">
          <div class="section-title"><span class="num">7</span> Таблица истории по шкалам</div>
          <div class="card">
            <div class="card-title">Агрегаты дневника (не типы эпизодов)</div>
            <table class="hist-table">
              <thead><tr>
                <th>Период</th><th>По шкале</th><th>Величина</th>
                <th>Лекарства</th><th>Режим</th><th>Ключевые события</th>
              </tr></thead>
              <tbody>${m.historyRows}</tbody>
            </table>
            <div class="card-note">Метки — агрегаты ваших шкал, не диагноз эпизода.</div>
          </div>
        </div>
        """ else ""}

        <div class="section">
          <div class="section-title"><span class="num">8</span> Кризисный план (из настроек)</div>
          <div class="crisis-levels">
            ${m.crisisHtml}
          </div>
        </div>

        <div class="report-footer">
          <div>Отчёт сформирован автоматически трекером Trace по данным пользователя. Не замена клинической оценки врача.</div>
          <div style="font-variant-numeric:tabular-nums">Версия 1.0 · ${esc(m.exportedAt)}</div>
        </div>
      </div>
    </body>
    </html>
    """.trimIndent()

    data class Model(
        val title: String,
        val subtitle: String,
        val periodLabel: String,
        val exportedAt: String,
        val patientName: String,
        val patientDob: String,
        val diagnosis: String,
        val doctorName: String,
        val nextVisit: String,
        val adherenceLabel: String,
        val adherenceColor: String,
        val disclaimer: String,
        val avgPolarity: String,
        val polarityRange: String,
        val moodCardClass: String,
        val sleepBadDays: Int,
        val sleepBadPct: Int,
        val sleepCardClass: String,
        val totalDays: Int,
        val medCardClass: String,
        val medMissesSub: String,
        val riskLevel: String,
        val riskTitle: String,
        val riskDesc: String,
        val polaritySvg: String,
        val moodNote: String,
        val sleepMoodSvg: String,
        val sleepNote: String,
        val radarSvg: String,
        val radarNote: String,
        val heatmapHtml: String,
        val heatmapNote: String,
        val triggersHtml: String,
        val prodromeHtml: String,
        val medTableRows: String,
        val medNote: String,
        val medDoseSvg: String,
        val historyRows: String,
        val crisisHtml: String,
    )

    private fun dayWord(n: Int): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod100 in 11..14 -> "дней"
            mod10 == 1 -> "день"
            mod10 in 2..4 -> "дня"
            else -> "дней"
        }
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
