package io.github.eyalshimshi.betterstips.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language (#32): the phone's, Hebrew or English, picked in the intro's first step or in Settings →
 * Appearance. From Android 13 it's Android's own per-app language, which Android keeps, applies to the whole app
 * (notifications and widgets too) and shows in the phone's settings. Before 13 it's kept here, and StipsApp and
 * MainActivity [wrap] their contexts in it; a change makes the screen again.
 */
object AppLanguage {
    /** The choices, with the language tag each sets ([tag] null: the phone's) and its name in its own words. */
    enum class Choice(val tag: String?, val nativeName: String?) {
        System(null, null),
        Hebrew("he", "עברית"),
        English("en", "English"),
    }

    /** The choice a language tag stands for, as Android or the saved setting has it; Hebrew's old code "iw" too. Pure. */
    fun choiceOf(tag: String?): Choice = when (tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase()) {
        "he", "iw" -> Choice.Hebrew
        "en" -> Choice.English
        else -> Choice.System
    }

    private const val PREFS = "app_language"
    private const val KEY = "tag"

    /** What's picked now. */
    fun current(context: Context): Choice =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty()) Choice.System else choiceOf(locales[0].toLanguageTag())
        } else {
            choiceOf(saved(context))
        }

    /** Picks [choice]: from Android 13 Android makes the screen again in it; before, [activity] is made again here. */
    fun set(activity: Activity, choice: Choice) {
        if (current(activity) == choice) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                choice.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, choice.tag).commit()
            activity.recreate()
        }
    }

    /** [base] in the picked language, before Android 13; from 13 Android does it itself, so [base] as it is. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = saved(base)
        if (tag == null || choiceOf(tag) == Choice.System) {
            Locale.setDefault(systemLocale())
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        // Dates and numbers formatted by the app follow it too.
        Locale.setDefault(locale)
        return base.createConfigurationContext(Configuration().apply { setLocale(locale) })
    }

    /** The phone's own language, whatever the app's is. */
    fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    /** The phone's language by its own name ("English", "עברית"), for "Phone's language (English)". */
    fun systemLanguageName(): String {
        val locale = systemLocale()
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** The activity [context] belongs to, through the wrappers around it. */
    fun activityOf(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun saved(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
}
