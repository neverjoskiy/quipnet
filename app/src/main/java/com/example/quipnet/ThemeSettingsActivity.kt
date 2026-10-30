package com.example.quipnet

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Экран «Оформление»: тема, фактура и температура — с живым предпросмотром.
 *
 * ## Почему экран, а не диалог
 *
 * Тема выбиралась диалогом со списком в столбик. Владелец попросил меню, как у
 * выбора региона на главном экране; заодно в диалог не поместились бы ни
 * пятнадцать фактур, ни ползунок.
 *
 * ## Как устроен предпросмотр
 *
 * Выбор кладётся в [QuipNetAppearance.preview] — это память процесса, не файл, — и
 * экран пересоздаёт себя. `QuipNetTheme.current` предпочитает предпросмотр
 * сохранённому, поэтому пересозданный экран рисуется уже по-новому, а ничего
 * записано ещё не было. «Применить» переписывает сохранённое и снимает
 * предпросмотр; уход с экрана снимает его молча.
 *
 * `recreate()`, а не точечная перекраска: тема — это ресурсы, они выдаются окну
 * при создании, и половину из них живому экрану не переставить вовсе. Пересоздание
 * стоит один кадр и даёт настоящий вид, а не его приближение.
 *
 * ## Почему выбор не теряется при пересоздании
 *
 * Он и не хранится в экране: единственный его носитель — предпросмотр в
 * [QuipNetAppearance]. Пересозданный экран читает оттуда же, откуда читает и тема.
 */
class ThemeSettingsActivity : AppCompatActivity() {

    private lateinit var themeGroup: FlowRadioGroup
    private lateinit var textureGroup: FlowRadioGroup
    private lateinit var temperatureBar: SeekBar
    private lateinit var temperatureValue: TextView
    private lateinit var themeNote: TextView

    /** Что стояло на входе. Нужно «Отмене» и уходу назад. */
    private var initialTheme: String = QuipNetTheme.DEFAULT_KEY
    private var initialTexture: QuipNetAppearance.Texture = QuipNetAppearance.Texture.NONE
    private var initialTemperature: Int = QuipNetAppearance.TEMPERATURE_NEUTRAL

    /** Применили ли выбор. Снимать предпросмотр в `onDestroy` тогда не надо. */
    private var applied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тему ставим до super.onCreate: позже окно уже создано со старым фоном.
        QuipNetTheme.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_settings)
        LogManager.setAppContext(this)

        // Сохранённые значения, а не текущие: «Сбросить» возвращает к тому, что
        // записано, и переживает любое число пересозданий экрана.
        initialTheme = QuipNetAppearance.storedThemeKey(this)
        initialTexture = QuipNetAppearance.storedTexture(this)
        initialTemperature = QuipNetAppearance.storedTemperature(this)

        themeGroup = findViewById(R.id.rg_theme)
        textureGroup = findViewById(R.id.rg_texture)
        temperatureBar = findViewById(R.id.sb_temperature)
        temperatureValue = findViewById(R.id.tv_temperature_value)
        themeNote = findViewById(R.id.tv_theme_note)

        val accent = QuipNetTheme.color(this, R.attr.m3ColorPrimary)
        themeGroup.selectionGlowColor = accent
        textureGroup.selectionGlowColor = accent

        buildThemeChips()
        buildTextureChips()
        setupTemperature()

        findViewById<Button>(R.id.btn_temperature_default).let { button ->
            QuipNetDialogs.styleButton(button, primary = false)
            button.setOnClickListener {
                if (QuipNetAppearance.temperature(this) == QuipNetAppearance.TEMPERATURE_NEUTRAL) return@setOnClickListener
                previewWith(temperature = QuipNetAppearance.TEMPERATURE_NEUTRAL)
            }
        }

        QuipNetFontHelper.apply(findViewById(android.R.id.content))
        if (savedScrollY > 0) {
            val scroll = findViewById<android.widget.ScrollView>(R.id.sv_theme_scroll)
            val target = savedScrollY
            savedScrollY = 0
            scroll?.viewTreeObserver?.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    scroll.viewTreeObserver.removeOnPreDrawListener(this)
                    scroll.scrollTo(0, target)
                    return true
                }
            })
        }
    }

    override fun onDestroy() {
        // Снимать предпросмотр можно только когда экран уходит **насовсем**.
        //
        // Дефект, который это чинит: `recreate()` — это уничтожение и создание
        // заново, причём именно в таком порядке (`performDestroyActivity`, затем
        // `handleLaunchActivity`). Безусловный сброс в `onDestroy` стирал
        // предпросмотр за мгновение до того, как новый экран собирался его
        // прочитать, — и тот поднимался в прежней теме. Снаружи это выглядело как
        // «нажимаю на тему, ничего не происходит», а «Применить» следом сохраняла
        // прежний выбор, потому что читала то же пустое место.
        //
        // `isFinishing` отличает одно от другого: при уходе назад он `true`, при
        // пересоздании — `false`.
        if (!applied && isFinishing) QuipNetAppearance.clearPreview()
        super.onDestroy()
    }

    private fun currentThemeKey(): String = QuipNetAppearance.previewThemeKey() ?: QuipNetTheme.current(this)

    private fun buildThemeChips() {
        themeGroup.removeAllViews()
        val current = currentThemeKey()
        QuipNetTheme.ORDER.forEachIndexed { index, option ->
            val chip = RadioButton(this, null, 0, R.style.QuipNetAppearanceChip).apply {
                id = 0x7100 + index
                text = option.title
                isChecked = option.key == current
            }
            themeGroup.addView(chip)
            chip.setOnClickListener { previewWith(theme = option.key) }
        }
        themeNote.text = QuipNetTheme.optionFor(current).note
    }

    private fun buildTextureChips() {
        textureGroup.removeAllViews()
        val current = QuipNetAppearance.texture(this)
        QuipNetAppearance.Texture.entries.forEachIndexed { index, texture ->
            val chip = RadioButton(this, null, 0, R.style.QuipNetAppearanceChip).apply {
                id = 0x7200 + index
                text = texture.title
                isChecked = texture == current
            }
            textureGroup.addView(chip)
            chip.setOnClickListener {
                // Касание уже выбранной фактуры — это просьба построить узор
                // заново: фактура задаёт правило, а не одну картинку. Нажатие на
                // другую просто переключает, иначе выбор каждый раз выдавал бы
                // ещё и случайный пересев, и вернуться к увиденному было бы
                // нельзя.
                if (texture == current && texture != QuipNetAppearance.Texture.NONE) {
                    previewWith(texture = texture, variant = QuipNetAppearance.variant(this) + 1)
                } else {
                    previewWith(texture = texture)
                }
            }
        }
    }

    private fun setupTemperature() {
        val span = QuipNetAppearance.TEMPERATURE_MAX - QuipNetAppearance.TEMPERATURE_MIN
        temperatureBar.max = span
        val current = QuipNetAppearance.temperature(this)
        temperatureBar.progress = current - QuipNetAppearance.TEMPERATURE_MIN
        renderTemperature(current)
        temperatureBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) renderTemperature(progress + QuipNetAppearance.TEMPERATURE_MIN)
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit

            // Предпросмотр — на отпускании: пересоздавать экран на каждый шаг
            // ползунка значило бы сорок пересозданий на одно движение пальца.
            override fun onStopTrackingTouch(bar: SeekBar) {
                previewWith(temperature = bar.progress + QuipNetAppearance.TEMPERATURE_MIN)
            }
        })
    }

    private fun renderTemperature(value: Int) {
        temperatureValue.text = when {
            value == QuipNetAppearance.TEMPERATURE_NEUTRAL -> "нейтрально"
            value < 0 -> "теплее ${-value} %"
            else -> "холоднее $value %"
        }
    }

    /**
     * Применяет выбор сразу: записывает и пересоздаёт экран. Кнопки «Применить»
     * больше нет. Незаданные значения берутся из сохранённого.
     */
    private fun previewWith(
        theme: String = currentThemeKey(),
        texture: QuipNetAppearance.Texture = QuipNetAppearance.texture(this),
        temperature: Int = QuipNetAppearance.temperature(this),
        variant: Int = QuipNetAppearance.variant(this),
    ) {
        QuipNetAppearance.store(this, theme, texture, temperature, variant)
        LogManager.log(
            "Оформление: тема ${QuipNetTheme.optionFor(theme).title}, фактура ${texture.title}, " +
                "температура $temperature."
        )
        // Пересоздание поднимало страницу наверх: запоминаем позицию прокрутки.
        savedScrollY = findViewById<android.widget.ScrollView>(R.id.sv_theme_scroll)?.scrollY ?: 0
        recreate()
    }

    companion object {
        /** Позиция прокрутки на время пересоздания экрана. */
        private var savedScrollY = 0
    }
}
