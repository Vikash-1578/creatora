package com.creatora.app.domain.publish

import com.creatora.app.domain.model.Project

enum class ChecklistItem { RENDERED, TITLE, THUMBNAIL, CAPTIONS, AI_LABEL, MADE_FOR_KIDS }

data class ChecklistEntry(val item: ChecklistItem, val done: Boolean)

/** YouTube's title limit. */
const val MAX_TITLE_LENGTH = 100

/**
 * The pre-publish checklist: video rendered, a title, a thumbnail, captions, and the two questions
 * YouTube asks (AI-generated or altered content, made for kids) answered.
 */
fun publishChecklist(project: Project): List<ChecklistEntry> = listOf(
    ChecklistEntry(ChecklistItem.RENDERED, project.renderUri != null),
    ChecklistEntry(ChecklistItem.TITLE, project.publishTitle.isNotBlank() && project.publishTitle.length <= MAX_TITLE_LENGTH),
    ChecklistEntry(ChecklistItem.THUMBNAIL, project.thumbnailUri != null),
    ChecklistEntry(ChecklistItem.CAPTIONS, project.captionsIncluded),
    ChecklistEntry(ChecklistItem.AI_LABEL, project.aiContent != null),
    ChecklistEntry(ChecklistItem.MADE_FOR_KIDS, project.madeForKids != null),
)

fun List<ChecklistEntry>.allDone(): Boolean = all { it.done }

/** The title and description to paste where the video is posted: description first, hashtags are part of it. */
fun postText(project: Project): String =
    listOf(project.publishTitle.trim(), project.publishDescription.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")

/** A reminder is shown for a scheduled post at [timeMillis]. Posts in the past are not scheduled. */
fun reminderDelayMillis(timeMillis: Long, nowMillis: Long): Long? = (timeMillis - nowMillis).takeIf { it > 0 }
