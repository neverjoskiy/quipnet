package com.example.quipnet

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Switch
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.theme.MaterialComponentsViewInflater

/**
 * Раздувает разметку нашими видами вместо штатных, чтобы любой `setText` проходил
 * через перевод ([QuipNetLanguage.onSetText]).
 *
 * Подключён темой (`viewInflaterClass` в `Theme.QuipNet` и `Theme.QuipNet.Settings`), а
 * создаётся AppCompat отражением — отсюда правило `-keep` в `proguard-rules.pro`.
 * Наследуется от инфлейтера Material, а не AppCompat: тема приложения — Material
 * Components, и виды обязаны остаться теми же `MaterialButton`/`MaterialTextView`,
 * иначе поменялся бы их вид.
 *
 * `EditText` не подменяется: введённый текст не переводится никогда, а подсказку
 * переводит обход дерева.
 */
class QuipNetViewInflater : MaterialComponentsViewInflater() {

    override fun createTextView(context: Context, attrs: AttributeSet?): AppCompatTextView =
        QuipNetTextView(context, attrs)

    override fun createButton(context: Context, attrs: AttributeSet): AppCompatButton =
        QuipNetButton(context, attrs)

    override fun createRadioButton(context: Context, attrs: AttributeSet?): AppCompatRadioButton =
        QuipNetRadioButton(context, attrs)

    override fun createCheckBox(context: Context, attrs: AttributeSet?): AppCompatCheckBox =
        QuipNetCheckBox(context, attrs)

    override fun createView(context: Context?, name: String?, attrs: AttributeSet?): View? = when (name) {
        null -> null
        "Switch" -> context?.let { QuipNetSwitch(it, attrs) }
        "androidx.appcompat.widget.SwitchCompat" -> context?.let { QuipNetSwitchCompat(it, attrs) }
        "androidx.appcompat.widget.AppCompatTextView" -> context?.let { QuipNetAppCompatTextView(it, attrs) }
        else -> null
    }
}

class QuipNetTextView(context: Context, attrs: AttributeSet?) :
    MaterialTextView(context, attrs), QuipNetLanguage.LocalizedText {
    override fun setText(text: CharSequence?, type: BufferType?) = QuipNetLanguage.onSetText(this, text, type)
    override fun setTextDirect(text: CharSequence?, type: BufferType?) = super.setText(text, type)
}

class QuipNetAppCompatTextView(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs), QuipNetLanguage.LocalizedText {
    override fun setText(text: CharSequence?, type: BufferType?) = QuipNetLanguage.onSetText(this, text, type)
    override fun setTextDirect(text: CharSequence?, type: BufferType?) = super.setText(text, type)
}

class QuipNetButton(context: Context, attrs: AttributeSet?) :
    MaterialButton(context, attrs), QuipNetLanguage.LocalizedText {
    override fun setText(text: CharSequence?, type: BufferType?) = QuipNetLanguage.onSetText(this, text, type)
    override fun setTextDirect(text: CharSequence?, type: BufferType?) = super.setText(text, type)
}

class QuipNetRadioButton(context: Context, attrs: AttributeSet?) :
    MaterialRadioButton(context, attrs), QuipNetLanguage.LocalizedText {
    override fun setText(text: CharSequence?, type: BufferType?) = QuipNetLanguage.onSetText(this, text, type)
    override fun setTextDirect(text: CharSequence?, type: BufferType?) = super.setText(text, type)
}

class QuipNetCheckBox(context: Context, attrs: AttributeSet?) :
    MaterialCheckBox(context, attrs), QuipNetLanguage.LocalizedText {
    override fun setText(text: CharSequence?, type: BufferType?) = QuipNetLanguage.onSetText(this, text, type)
    override fun setTextDirect(text: CharSequence?, type: BufferType?) = super.setText(text, type)
}

@Suppress("DEPRECATION")
class QuipNetSwitch(context: Context, attrs: AttributeSet?) :
    Switch(context, attrs), QuipNetLanguage.LocalizedText {
    override fun setText(text: CharSequence?, type: BufferType?) = QuipNetLanguage.onSetText(this, text, type)
    override fun setTextDirect(text: CharSequence?, type: BufferType?) = super.setText(text, type)
}

class QuipNetSwitchCompat(context: Context, attrs: AttributeSet?) :
    SwitchCompat(context, attrs), QuipNetLanguage.LocalizedText {
    override fun setText(text: CharSequence?, type: BufferType?) = QuipNetLanguage.onSetText(this, text, type)
    override fun setTextDirect(text: CharSequence?, type: BufferType?) = super.setText(text, type)
}
