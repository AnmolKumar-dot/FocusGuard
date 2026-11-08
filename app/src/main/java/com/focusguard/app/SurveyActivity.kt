package com.focusguard.app

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

/**
 * Five quick questions on first launch. The answers only shape the messages on the block screen
 * and never leave the phone. Every step can be skipped.
 */
class SurveyActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var stepText: TextView
    private lateinit var next: MaterialButton
    private lateinit var back: TextView

    private var step = 0
    private val total = 5

    // answers: index of the chosen option, -1 = none yet
    private var name = ""
    private val picked = intArrayOf(-1, -1, -1, -1, -1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Accent.setFocus(false, animate = false)
        step = savedInstanceState?.getInt("step") ?: 0
        name = savedInstanceState?.getString("name") ?: prefs.surveyName
        savedInstanceState?.getIntArray("picked")?.let { saved ->
            for (i in saved.indices) if (i < picked.size) picked[i] = saved[i]
        }
        if (savedInstanceState == null) {
            picked[1] = prefs.surveyDistraction
            picked[2] = prefs.surveyGoal
            picked[3] = prefs.surveyPhone
            picked[4] = if (prefs.surveyDone) prefs.surveyTone else -1
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.tag = "aurora"
        root.setPadding(dp(24), dp(28), dp(24), dp(20))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        stepText = TextView(this)
        stepText.textSize = 13f
        stepText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        top.addView(stepText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val skip = TextView(this)
        skip.text = getString(R.string.survey_skip)
        skip.textSize = 14f
        skip.setPadding(dp(12), dp(8), 0, dp(8))
        skip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        skip.setOnClickListener { finishSurvey(save = false) }
        top.addView(skip)
        root.addView(top)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = total + 1
        progress.tag = "accent_tint"
        progress.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
        val plp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6))
        plp.topMargin = dp(6)
        root.addView(progress, plp)

        val scroll = ScrollView(this)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, dp(24), 0, dp(12))
        scroll.addView(body, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        back = TextView(this)
        back.text = getString(R.string.survey_back)
        back.textSize = 16f
        back.setPadding(dp(8), dp(16), dp(24), dp(16))
        back.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        back.setOnClickListener { goBack() }
        bottom.addView(back)
        next = MaterialButton(this)
        next.isAllCaps = false
        next.textSize = 16f
        next.typeface = Typeface.DEFAULT_BOLD
        next.cornerRadius = dp(28)
        next.setTextColor(0xFF0D0D0D.toInt())
        next.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFAFAF5.toInt())
        next.setOnClickListener { onNext() }
        bottom.addView(next, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(bottom)

        setContentView(root)
        Accent.apply(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step > 0) goBack() else finishSurvey(save = false)
            }
        })
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step)
        outState.putString("name", name)
        outState.putIntArray("picked", picked)
    }

    private fun goBack() {
        if (step > 0) {
            step--
            render()
        }
    }

    private fun onNext() {
        if (step == 0) name = (body.findViewWithTag<EditText>("nameInput")?.text?.toString() ?: name).trim()
        if (step >= total) {
            finishSurvey(save = true)
        } else {
            step++
            render()
        }
    }

    private fun canContinue(): Boolean = when (step) {
        0 -> true // name is optional
        in 1..4 -> picked[step] >= 0
        else -> true
    }

    private fun render() {
        body.removeAllViews()
        stepText.text = if (step < total) getString(R.string.survey_step, step + 1, total) else ""
        progress.progress = step + 1
        back.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        next.text = getString(if (step >= total) R.string.survey_go else R.string.survey_next)

        if (step >= total) {
            renderDone()
        } else {
            val q = TextView(this)
            q.text = getString(
                when (step) {
                    0 -> R.string.survey_q1
                    1 -> R.string.survey_q2
                    2 -> R.string.survey_q3
                    3 -> R.string.survey_q4
                    else -> R.string.survey_q5
                }
            )
            q.textSize = 27f
            q.typeface = Typeface.DEFAULT_BOLD
            q.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            body.addView(q)
            body.addView(Ui.gap(this, 20))
            if (step == 0) renderName() else renderOptions()
        }
        next.isEnabled = canContinue()
        next.alpha = if (next.isEnabled) 1f else 0.4f
        Accent.apply(body)
    }

    private fun renderName() {
        val input = EditText(this)
        input.tag = "nameInput"
        input.setText(name)
        input.hint = getString(R.string.survey_q1_hint)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        input.maxLines = 1
        input.textSize = 20f
        input.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        input.setHintTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        input.setBackgroundResource(R.drawable.bg_input)
        input.setPadding(dp(18), dp(16), dp(18), dp(16))
        body.addView(input, Ui.match())
        body.addView(Ui.note(this, getString(R.string.survey_q1_note)))
    }

    private fun renderOptions() {
        val options = resources.getStringArray(
            when (step) {
                1 -> R.array.survey_q2_opts
                2 -> R.array.survey_q3_opts
                3 -> R.array.survey_q4_opts
                else -> R.array.survey_q5_opts
            }
        )
        options.forEachIndexed { i, label ->
            val chosen = picked[step] == i
            val row = TextView(this)
            row.text = label
            row.textSize = 17f
            row.typeface = Typeface.DEFAULT_BOLD
            row.setPadding(dp(20), dp(18), dp(20), dp(18))
            row.setBackgroundResource(if (chosen) R.drawable.bg_segment_selected else R.drawable.bg_card)
            row.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            row.setOnClickListener {
                picked[step] = i
                render()
            }
            val lp = Ui.match()
            lp.bottomMargin = dp(10)
            body.addView(row, lp)
        }
    }

    private fun renderDone() {
        // Save now so the preview uses the answers just given.
        saveAnswers()
        val title = TextView(this)
        val shown = name.trim().ifEmpty { getString(R.string.joke_name_default) }
        title.text = getString(R.string.survey_done_title, shown)
        title.textSize = 27f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        body.addView(title)

        val sub = TextView(this)
        sub.text = getString(R.string.survey_done_sub)
        sub.textSize = 15f
        sub.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        sub.setPadding(0, dp(10), 0, dp(20))
        body.addView(sub)

        val card = Ui.card(this)
        card.setPadding(dp(22), dp(24), dp(22), dp(24))
        val emoji = TextView(this)
        emoji.text = Jokes.emoji(this)
        emoji.textSize = 44f
        emoji.gravity = Gravity.CENTER
        card.addView(emoji, Ui.match())
        val joke = TextView(this)
        joke.text = Jokes.pick(this, prefs)
        joke.textSize = 20f
        joke.typeface = Typeface.DEFAULT_BOLD
        joke.gravity = Gravity.CENTER
        joke.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        joke.setPadding(0, dp(14), 0, 0)
        card.addView(joke, Ui.match())
        body.addView(card, Ui.match())

        val again = TextView(this)
        again.text = getString(R.string.block_another)
        again.textSize = 14f
        again.gravity = Gravity.CENTER
        again.setPadding(dp(16), dp(18), dp(16), dp(8))
        again.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        again.setOnClickListener {
            joke.text = Jokes.pick(this, prefs)
            emoji.text = Jokes.emoji(this)
        }
        body.addView(again, Ui.match())
    }

    private fun saveAnswers() {
        prefs.surveyName = name.trim()
        prefs.surveyDistraction = picked[1]
        prefs.surveyGoal = picked[2]
        prefs.surveyPhone = picked[3]
        prefs.surveyTone = if (picked[4] >= 0) picked[4] else 1
    }

    private fun finishSurvey(save: Boolean) {
        if (save) saveAnswers()
        prefs.surveyDone = true
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        finish()
    }
}
