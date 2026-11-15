package io.github.eyalshimshi.betterstips.util

import io.github.eyalshimshi.betterstips.data.model.FeedQuery
import io.github.eyalshimshi.betterstips.data.model.Question

/**
 * Home's "Unanswered" category (#37). Stips has no list of unanswered questions, so the app reads New's pages and keeps
 * only the questions no one has answered yet. A page may hold none, so one load looks a few pages further. Pure.
 */
object FeedRules {
    /** The most pages of New one load looks through for an unanswered question before it says that's all (#37). */
    const val UNANSWERED_PAGES_PER_LOAD = 4

    /** Whether [question] belongs in the list [query] asks for: any, or only one without answers (#37). */
    fun shows(query: FeedQuery, question: Question): Boolean = !query.unanswered || question.answerCount == 0

    /**
     * Whether one load goes on to the next page (#37): only for the unanswered category, while it hasn't found a question
     * to add, Stips still has pages, and it looked at fewer than [UNANSWERED_PAGES_PER_LOAD].
     */
    fun keepLooking(query: FeedQuery, found: Int, lastPageEmpty: Boolean, pagesLooked: Int): Boolean =
        query.unanswered && found == 0 && !lastPageEmpty && pagesLooked < UNANSWERED_PAGES_PER_LOAD

    /**
     * Whether the list is at its end: Stips sent an empty page, or the unanswered category looked through its pages and
     * found nothing more (#37).
     */
    fun ended(query: FeedQuery, found: Int, lastPageEmpty: Boolean): Boolean = lastPageEmpty || (query.unanswered && found == 0)
}
