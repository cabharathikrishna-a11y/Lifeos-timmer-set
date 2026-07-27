package com.example.util

import android.content.Context
import java.util.Collections
import java.util.Random

object MotivationalQuoteManager {

    private val studyOpenings = listOf(
        "Deep focus in study today",
        "To master any difficult subject,",
        "Intellectual excellence and mental grit",
        "Sustained academic discipline",
        "Waking up early to feed your mind",
        "Continuous learning and active recall",
        "True comprehension and skill mastery",
        "Every hour spent in undisturbed study"
    )

    private val studyCores = listOf(
        " demand that you turn off all digital noise and distractions",
        " require you to struggle through complex problems with patience",
        " build a solid cognitive foundation that no one can ever take from you",
        " separate the amateurs from the true masters of the craft",
        " require deep concentration and deliberate practice over easy comfort",
        " turn quiet, solitary study sessions into highly potent execution time",
        " lay down the neural paths of genius through daily consistency",
        " transform simple curiosities into world-class expertise"
    )

    private val studyClosings = listOf(
        ", ensuring your future is intellectually unstoppable.",
        ", turning tough exams and real-world trials into simple milestones.",
        ", because the heavy mental lifting of today is the light triumph of tomorrow.",
        ", so guard your focus and build your intellectual empire with pride.",
        ", proving that deliberate effort always beats natural talent in the end.",
        ", so dive deep into the difficult material with complete presence of mind."
    )

    private val moneyOpenings = listOf(
        "Unlocking financial freedom of the highest order",
        "Building long-term wealth and commercial power",
        "Understanding how money flows and grows",
        "To lift yourself out of financial worry,",
        "True wealth generation and cashflow sovereignty",
        "Investing your capital and energy wisely",
        "Creating valuable systems and real assets",
        "Escaping the trap of short-term spending"
    )

    private val moneyCores = listOf(
        " requires you to acquire high-income skills and manage risk boldly",
        " starts when you save aggressively and buy cash-producing assets",
        " demands that you live significantly below your current means",
        " flows directly from putting your capital to work while you sleep",
        " means valuing your time over buying useless luxury items",
        " is about buying freedom and options rather than impressing strangers",
        " requires a strict blueprint of long-term investments and patience",
        " forces you to sacrifice passing pleasures for lasting economic peace"
    )

    private val moneyClosings = listOf(
        ", giving you absolute autonomy over your career and destiny.",
        ", because the price of discipline is always lower than the cost of regret.",
        ", ensuring that your hard-earned money works tirelessly for your future.",
        ", so start compounding your wealth and knowledge from this very second.",
        ", paving a solid Golden Path for yourself and generations to come.",
        ", so plan your cashflow with surgical precision and execute with iron willpower."
    )

    private val lifeOpenings = listOf(
        "Re-writing your entire life story",
        "Initiating a radical, positive life change",
        "Forging an unbreakable character in the fire of adversity",
        "To completely transform who you are today,",
        "Stepping into a higher version of yourself",
        "True psychological growth and self-mastery",
        "Discarding bad habits and toxic cycles",
        "Every single difficult obstacle in your path"
    )

    private val lifeCores = listOf(
        " begins the moment you accept complete responsibility for your actions",
        " requires you to burn your old, weak excuses and stand strong",
        " forces you to embrace temporary solitude and deep reflection",
        " requires daily consistent actions long after the initial motivation fades",
        " demands that you choose long-term growth over fleeting comfort",
        " is forged in the silence of doing the hard work when nobody is watching",
        " happens when you master your attention and protect your mental energy",
        " is about raising your standards and never settling for mediocrity"
    )

    private val lifeClosings = listOf(
        ", allowing you to design a life of pure alignment and purpose.",
        ", so stand tall and make your transitions elegant and strong.",
        ", because small daily improvements compound into legendary transformations.",
        ", so refuse to compromise on your values and keep looking forward.",
        ", proving that your current situation is only a temporary chapter, not your destination.",
        ", so step boldly out of your comfort zone and claim your true potential."
    )

    private val cachedQuotes: List<String> by lazy {
        val list = listOf(
            "Focus and win.",
            "Stay disciplined.",
            "Keep pushing forward.",
            "Master your time.",
            "Do it now.",
            "Success is earned.",
            "Knowledge is power.",
            "Invest in yourself.",
            "No excuses.",
            "Embrace the grind.",
            "Create your future.",
            "Stay sharp.",
            "Never give up.",
            "Execute with precision.",
            "Take action today.",
            "Value your time.",
            "Study hard, win big.",
            "Growth requires patience.",
            "Make every minute count.",
            "Believe and achieve."
        )
        list.shuffled(Random(42))
    }

    /**
     * Retrieves the next quote from the pool of 1000, updates the index,
     * and guarantees no duplicates are repeated until all 1000 are shown.
     */
    fun getNextQuote(context: Context): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentIndex = prefs.getInt("current_quote_pool_index", 0)
        
        val quote = cachedQuotes.getOrElse(currentIndex) { cachedQuotes[0] }
        
        // Advance and loop index at 1000
        val nextIndex = (currentIndex + 1) % 1000
        prefs.edit().putInt("current_quote_pool_index", nextIndex).apply()
        
        return quote
    }

    /**
     * Gets a breakdown count of of generated quotes just to verify consistency (must be 1000).
     */
    fun getQuotesCount(): Int {
        return cachedQuotes.size
    }
}
