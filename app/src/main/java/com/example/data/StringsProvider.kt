package com.example.data

object StringsProvider {
    fun num(value: Int, lang: AppLanguage): String =
        if (lang == AppLanguage.BN) StudyDateUtils.toBnDigits(value) else value.toString()

    fun text(lang: AppLanguage, bn: String, en: String): String =
        if (lang == AppLanguage.BN) bn else en

    fun navToday(lang: AppLanguage) = text(lang, "আজ", "Today")
    fun navRoadmap(lang: AppLanguage) = text(lang, "রোডম্যাপ", "Roadmap")
    fun navWeekly(lang: AppLanguage) = text(lang, "সাপ্তাহিক", "Weekly")
    fun navMistakes(lang: AppLanguage) = text(lang, "ভুলের খাতা", "Mistakes")
    fun navProgress(lang: AppLanguage) = text(lang, "অগ্রগতি", "Progress")
    fun navAiTutor(lang: AppLanguage) = text(lang, "AI টিউটর", "AI Tutor")
    fun navSettings(lang: AppLanguage) = text(lang, "সেটিংস", "Settings")

    fun appHeaderTitle(lang: AppLanguage) =
        text(lang, "HSC ২০২৮ বিজ্ঞান", "HSC 2028 Science")
}
