package com.arise.app.game

import com.arise.app.data.AppData
import com.arise.app.data.DayType
import com.arise.app.data.Settings
import com.arise.app.data.Stat
import com.arise.app.data.Task
import com.arise.app.data.TaskKind
import com.arise.app.data.TaskStep
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

/** A task as heard from the mic, before you confirm it. */
data class Draft(
    val title: String,
    val deadline: LocalDate?,
    val kind: TaskKind,
    val steps: List<TaskStep>,
    val pairable: Boolean,
)

/** What a task asks of you on one day. */
data class TaskToday(
    val task: Task,
    /** Progress when the day started. */
    val from: Int,
    /** Where to aim today (projects); 100 for one-offs. */
    val aim: Int,
    val step: TaskStep?,
    val note: String,
)

/**
 * To-dos without an AI: speech is split into tasks, deadlines are read from phrases like "before
 * November 7th" or "by Friday", and projects get a built-in step plan by what kind of task it is.
 * Each day the System spreads what's left over the days until the deadline, so finishing half in one
 * sitting makes the next days lighter, and a missed day just comes back as "start".
 */
object Todo {
    const val XP = 15

    fun questId(t: Task) = "todo_${t.id}"
    fun taskOf(data: AppData, questId: String): Task? = data.tasks.firstOrNull { questId(it) == questId }

    // ---- Built-in plans ----

    private data class Template(val words: List<String>, val project: Boolean, val pairable: Boolean, val steps: List<TaskStep>)

    private fun st(vararg p: Pair<String, Int>) = p.map { TaskStep(it.first, it.second) }

    private val templates = listOf(
        Template(listOf("thesis", "dissertation", "research paper", "report", "paper", "journal article"), true, false,
            st("Research and collect sources" to 30, "Outline: jot down the points" to 10, "Write the introduction" to 10,
                "Write the main sections" to 35, "Write the conclusion" to 5, "Edit and proofread" to 10)),
        Template(listOf("essay", "article", "blog", "write up", "write-up"), true, false,
            st("Collect ideas and notes" to 25, "Outline" to 15, "First draft" to 40, "Edit and polish" to 20)),
        Template(listOf("assignment", "homework", "case study", "lab"), true, false,
            st("Read the brief, list what's asked" to 10, "Research and notes" to 25, "Draft it" to 40, "Check, format, submit" to 25)),
        Template(listOf("exam", "test", "revise", "revision", "study for", "prepare for", "interview"), true, false,
            st("Gather the material, list the topics" to 10, "Learn the topics" to 50, "Practice questions" to 25, "Revise weak spots" to 15)),
        Template(listOf("presentation", "ppt", "slides", "deck", "pitch", "speech"), true, false,
            st("Outline the story" to 20, "Write the content" to 30, "Design the slides" to 25, "Rehearse out loud" to 25)),
        Template(listOf("crochet", "knit", "embroider", "sew", "stitch", "handkerchief", "kerchief", "scarf", "sweater", "quilt"), true, true,
            st("Gather yarn, hook, pattern" to 5, "Start: the first rows" to 30, "The middle" to 35, "Finish the body" to 25, "Weave in ends, final touches" to 5)),
        Template(listOf("paint", "painting", "draw", "drawing", "sketch", "craft", "diy", "scrapbook", "journal"), true, true,
            st("Gather materials, plan it" to 10, "Rough layout" to 25, "Main work" to 45, "Details and finishing" to 20)),
        Template(listOf("wardrobe", "closet", "declutter", "organise", "organize", "clean my", "cleaning", "deep clean"), false, true,
            st("Take everything out" to 25, "Sort: keep, donate, throw" to 35, "Wipe and clean" to 15, "Put it back neatly" to 25)),
        Template(listOf("read", "book", "novel"), true, true,
            st("First quarter" to 25, "Second quarter" to 25, "Third quarter" to 25, "Last quarter" to 25)),
        Template(listOf("course", "learn", "tutorial", "certification", "certificate"), true, false,
            st("First modules" to 25, "Middle modules" to 40, "Last modules" to 25, "Final test or project" to 10)),
        Template(listOf("code", "app", "website", "build", "project"), true, false,
            st("Plan what it needs to do" to 15, "Build the core" to 45, "Fix and finish" to 25, "Test and wrap up" to 15)),
    )

    private val general = st("Start" to 30, "Keep going" to 40, "Finish" to 30)
    private val quickWords = listOf("call", "buy", "pay", "book ", "send", "email", "mail", "text", "message", "submit", "return", "pick up", "order", "renew", "recharge", "reply")
    private val pairWords = listOf("fold", "iron", "laundry", "watch", "listen", "crochet", "knit", "sort")

    /** Whole-word match at the start of a word, so "read" doesn't fire on "thread" and "app" not on "happy". */
    private fun has(text: String, word: String): Boolean = Regex("\\b" + Regex.escape(word.trim())).containsMatchIn(text)

    private fun template(t: String): Template? = templates.firstOrNull { tpl -> tpl.words.any { has(t, it) } }

    fun planFor(title: String): Pair<Boolean, List<TaskStep>> {
        val hit = template(title.lowercase()) ?: return false to emptyList()
        return hit.project to hit.steps
    }

    fun draft(title: String, deadline: LocalDate?, today: LocalDate): Draft {
        val (project, steps) = planFor(title)
        val t = title.lowercase()
        val quick = quickWords.any { has(t, it) && t.trimStart().startsWith(it.trim()) }
        val long = deadline != null && ChronoUnit.DAYS.between(today, deadline) >= 2
        val isProject = !quick && (project || long)
        val pair = template(t)?.pairable ?: pairWords.any { has(t, it) }
        return Draft(title, deadline, if (isProject) TaskKind.PROJECT else TaskKind.ONE_OFF, if (isProject) steps.ifEmpty { general } else emptyList(), pair)
    }

    // ---- Speech → tasks ----

    private val months = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private val monthShort = months.map { it.take(3) }
    private val dayNames = DayOfWeek.entries.associateBy { it.name.lowercase() }

    private val splitters = Regex(
        "(?i)[.;!?\\n]+|,\\s*(?:and\\s+)?(?:then|also)\\s+|\\s+(?:and then|and also|also|another thing(?: is)?|one more thing(?: is)?|next thing(?: is)?|after that|plus)\\s+|,\\s+and\\s+(?=i\\s)",
    )
    private val fillers = Regex(
        "(?i)^(?:so|okay|ok|um+|uh+|like|and|then|also|first(?: thing)?(?: is)?|second(?:ly)?|next|right|well|basically)\\b[,\\s]*",
    )
    private val leads = Regex(
        "(?i)^(?:i\\s+(?:have|need|want|got|gotta|should|must|will|'ll|am going|'m going|have got)(?:\\s+to)?|i've got to|i have got to|i've to|remind me to|don't forget to|to)\\s+",
    )

    /** Splits what you said into tasks and pulls a deadline out of each one. */
    fun parse(speech: String, today: LocalDate): List<Draft> =
        speech.split(splitters).map { it.trim() }.filter { it.isNotBlank() }.mapNotNull { raw ->
            var text = raw
            repeat(4) { text = text.replace(fillers, "").replace(leads, "").trim() }
            val (deadline, rest) = deadlineIn(text, today)
            val title = rest.trim().trimEnd(',', '.', ' ').replace(Regex("\\s+"), " ")
                .replace(Regex("(?i)\\s+(?:before|by|until|till|on|due)$"), "")
                .replaceFirstChar { it.uppercase() }
            if (title.length < 3) null else draft(title, deadline, today)
        }

    /** Finds a deadline phrase, returns the date and the text without it. "Before X" means the day before X. */
    fun deadlineIn(text: String, today: LocalDate): Pair<LocalDate?, String> {
        val low = text.lowercase()
        fun cut(m: MatchResult) = (text.substring(0, m.range.first) + " " + text.substring(m.range.last + 1)).trim()
        fun before(word: String?, d: LocalDate) = if (word?.startsWith("before") == true) d.minusDays(1) else d
        fun dated(month: Int, day: Int): LocalDate? {
            val y = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
            return if (y.isBefore(today)) y.plusYears(1) else y
        }
        val prep = "(before|by|until|till|due(?: on| by)?|on|for)?\\s*(?:the\\s+)?"
        val mon = "(${(months + monthShort).joinToString("|")})"

        Regex("$prep(\\d{1,2})(?:st|nd|rd|th)?(?:\\s+of)?\\s+$mon\\b").find(low)?.let { m ->
            val mi = monthIndex(m.groupValues[3])
            dated(mi, m.groupValues[2].toInt())?.let { return before(m.groupValues[1], it) to cut(m) }
        }
        Regex("$prep$mon\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b").find(low)?.let { m ->
            val mi = monthIndex(m.groupValues[2])
            dated(mi, m.groupValues[3].toInt())?.let { return before(m.groupValues[1], it) to cut(m) }
        }
        Regex("$prep(\\d{1,2})[/.-](\\d{1,2})\\b").find(low)?.let { m ->
            dated(m.groupValues[3].toInt(), m.groupValues[2].toInt())?.let { return before(m.groupValues[1], it) to cut(m) }
        }
        Regex("$prep(today|tonight)\\b").find(low)?.let { return today to cut(it) }
        Regex("$prep(?:day after tomorrow)\\b").find(low)?.let { m -> return before(m.groupValues[1], today.plusDays(2)) to cut(m) }
        Regex("$prep(tomorrow)\\b").find(low)?.let { m -> return before(m.groupValues[1], today.plusDays(1)) to cut(m) }
        Regex("in\\s+(\\d+|a|one|two|three|four|five|six|seven)\\s+(day|days|week|weeks)\\b").find(low)?.let { m ->
            val n = when (val w = m.groupValues[1]) {
                "a", "one" -> 1; "two" -> 2; "three" -> 3; "four" -> 4; "five" -> 5; "six" -> 6; "seven" -> 7
                else -> w.toInt()
            }
            val days = if (m.groupValues[2].startsWith("week")) n * 7L else n.toLong()
            return today.plusDays(days) to cut(m)
        }
        Regex("$prep(?:the\\s+)?end of (?:the\\s+|this\\s+)?(week|month)\\b").find(low)?.let { m ->
            val d = if (m.groupValues[2] == "week") today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)) else today.with(TemporalAdjusters.lastDayOfMonth())
            return d to cut(m)
        }
        Regex("$prep(next week)\\b").find(low)?.let { m ->
            return today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).plusDays(6) to cut(m)
        }
        Regex("$prep(?:this\\s+|next\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b").find(low)?.let { m ->
            val dow = dayNames.getValue(m.groupValues[2])
            var d = today.with(TemporalAdjusters.next(dow))
            if ("next " in m.value && ChronoUnit.DAYS.between(today, d) < 7 && today.dayOfWeek.value < dow.value) d = d.plusWeeks(1)
            return before(m.groupValues[1], d) to cut(m)
        }
        Regex("$prep(\\d{1,2})(?:st|nd|rd|th)\\b").find(low)?.let { m ->
            val day = m.groupValues[2].toInt()
            val thisMonth = runCatching { today.withDayOfMonth(day) }.getOrNull()
            val d = when {
                thisMonth != null && !thisMonth.isBefore(today) -> thisMonth
                else -> runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull()
            }
            if (d != null) return before(m.groupValues[1], d) to cut(m)
        }
        return null to text
    }

    private fun monthIndex(name: String): Int = (months.indexOf(name).takeIf { it >= 0 } ?: monthShort.indexOf(name.take(3))) + 1

    // ---- Planning a day ----

    /** Progress reached before [date] started. */
    fun progressBefore(t: Task, date: LocalDate): Int =
        t.log.filterKeys { it < date }.maxByOrNull { it.key }?.value ?: if (date > LocalDate.now()) t.progress else 0

    fun currentStep(t: Task, progress: Int): TaskStep? {
        var acc = 0
        for (s in t.steps) {
            acc += s.share
            if (progress < acc) return s
        }
        return t.steps.lastOrNull()
    }

    /** What each open task asks of you on [date], most urgent first. */
    fun forDay(data: AppData, date: LocalDate): List<TaskToday> {
        val open = data.tasks.filter { t -> !t.created.isAfter(date) && (t.doneOn == null || t.doneOn == date) }
        val projects = open.filter { it.kind == TaskKind.PROJECT }.map { t ->
            val from = if (date == LocalDate.now()) (t.log[date]?.let { progressBefore(t, date) } ?: t.progress) else progressBefore(t, date)
            val left = (100 - from).coerceAtLeast(0)
            val dl = t.deadline
            val daysLeft = dl?.let { ChronoUnit.DAYS.between(date, it).toInt() + 1 }
            val aim = when {
                dl == null -> (from + maxOf(10, left / 5)).coerceAtMost(100)
                daysLeft!! <= 1 -> 100
                else -> (from + ceil(left.toDouble() / daysLeft).toInt()).coerceAtMost(100)
            }
            val note = when {
                dl == null -> "No deadline. Any progress counts."
                daysLeft!! <= 0 -> "The date has passed. No stress: finish when you can."
                daysLeft == 1 -> "Last day. Aim to finish."
                aim - from >= 40 -> "${aim - from}% today to finish in time. It's tight, but doable."
                else -> "$daysLeft days left · aim for $aim% today"
            }
            TaskToday(t, from, aim, currentStep(t, from), note)
        }
        val oneOffs = open.filter { it.kind == TaskKind.ONE_OFF }
        val dueOneOffs = oneOffs.filter { t -> t.deadline != null && !date.isBefore(t.deadline.minusDays(1)) }
            .map { t -> TaskToday(t, 0, 100, null, if (t.deadline == date) "Due today." else "Due ${t.deadline}.") }
        val scheduledOneOffs = oneOffs.filter { it.deadline != null && date.isBefore(it.deadline.minusDays(1)) }
            .filter { t -> spreadDay(t, data.tasks) == date }
            .map { t -> TaskToday(t, 0, 100, null, "Placed today so it isn't a rush later.") }
        val someday = oneOffs.filter { it.deadline == null }
        val somedayPick = if (someday.isEmpty()) emptyList() else {
            val i = Math.floorMod(date.toEpochDay().toInt(), someday.size)
            listOf(TaskToday(someday[i], 0, 100, null, "Someday pick. Only if you feel like it."))
        }
        val urgent = projects.sortedBy { it.task.deadline ?: LocalDate.MAX }
        return (dueOneOffs + urgent + scheduledOneOffs + somedayPick).distinctBy { it.task.id }
    }

    /** A one-off with a far deadline goes on a day roughly in the middle, so it isn't left to the last minute. */
    private fun spreadDay(t: Task, all: List<Task>): LocalDate {
        val dl = t.deadline ?: return t.created
        val span = ChronoUnit.DAYS.between(t.created, dl.minusDays(1)).coerceAtLeast(0)
        return t.created.plusDays(span / 2)
    }

    /** Task sessions as schedule blocks: up to two a day, plus one someday pick. Times avoid work hours. */
    fun blocks(s: Settings, data: AppData, date: LocalDate, type: DayType): List<Block> {
        if (type == DayType.SICK) return emptyList()
        val today = forDay(data, date)
        val sessions = today.filter { it.task.kind == TaskKind.PROJECT || it.task.deadline != null }.take(2)
        val someday = today.firstOrNull { it.task.kind == TaskKind.ONE_OFF && it.task.deadline == null }
        val dinnerAfter = Program.capPlus(s.meals.dinner, 45)
        val base = when (type) {
            DayType.OFFICE -> Program.capPlus(Program.homeTime(s), 90)
            DayType.WFH -> Program.capPlus(s.workEnd, 60)
            else -> LocalTime.of(16, 0)
        }
        val out = mutableListOf<Block>()
        sessions.forEachIndexed { i, tt ->
            val at = if (tt.task.pairable) Program.capPlus(dinnerAfter, 30L * i) else Program.capPlus(base, 90L * i)
            out += Block(at, "To-do", quest = quest(tt))
        }
        if (someday != null && type != DayType.OFFICE) {
            out += Block(Program.capPlus(base, if (sessions.isEmpty()) 0 else 180), "Someday", quest = quest(someday))
        }
        return out
    }

    fun quest(tt: TaskToday): Quest {
        val t = tt.task
        val title = if (t.kind == TaskKind.PROJECT) "${t.title} · aim ${tt.aim}%" else t.title
        val full = buildString {
            if (t.kind == TaskKind.PROJECT) {
                append("Now at ${tt.from}%.")
                tt.step?.let { append(" Step: ${it.title}.") }
                append(" Sit as long or as little as you like, then tell the System how far you got. ")
            }
            append(tt.note)
            if (t.pairable) append(" Pair it with a show or music.")
        }
        return Quest(
            questId(t), title, full, null, Stat.INT, XP, if (t.kind == TaskKind.PROJECT) "🧩" else "📝",
            optional = true, limitMin = null,
        )
    }

    // ---- Updating ----

    fun newId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) + (100..999).random()

    fun add(data: AppData, drafts: List<Draft>, today: LocalDate): AppData =
        data.copy(tasks = data.tasks + drafts.map { d ->
            Task(newId(), d.title, today, d.deadline, d.kind, d.steps, 0, d.pairable)
        })

    /** Records progress on [date]. 100% (or a one-off ticked) marks it done. */
    fun setProgress(data: AppData, id: String, pct: Int, date: LocalDate): AppData {
        val p = pct.coerceIn(0, 100)
        return data.copy(tasks = data.tasks.map { t ->
            if (t.id != id) t else t.copy(progress = p, log = t.log + (date to p), doneOn = if (p >= 100) date else null)
        })
    }

    fun update(data: AppData, t: Task): AppData = data.copy(tasks = data.tasks.map { if (it.id == t.id) t else it })
    fun delete(data: AppData, id: String): AppData = data.copy(tasks = data.tasks.filter { it.id != id })
}
