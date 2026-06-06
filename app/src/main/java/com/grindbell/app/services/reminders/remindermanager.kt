package com.grindbell.app.services.reminders

import android.util.Log
import com.grindbell.app.data.local.dao.ReminderDao
import com.grindbell.app.data.local.dao.TaskDao
import com.grindbell.app.data.local.dao.HabitDao
import com.grindbell.app.data.local.dao.AnalyticsDao
import com.grindbell.app.data.local.entity.AnalyticsEntity
import com.grindbell.app.data.local.entity.HabitEntity
import com.grindbell.app.data.local.entity.ReminderEntity
import com.grindbell.app.services.NotificationHelper
import com.grindbell.app.services.alarms.AlarmScheduler
import com.grindbell.app.domain.model.ReminderMode
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ReminderManager @Inject constructor(
    private val reminderDao: ReminderDao,
    private val taskDao: TaskDao,
    private val habitDao: HabitDao,
    private val analyticsDao: AnalyticsDao,
    private val notificationHelper: NotificationHelper,
    private val alarmScheduler: AlarmScheduler,
    @Named("appScope") private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ReminderManager"
        private const val HABIT_REPEAT_INTERVAL_MIN = 1  // Nag interval for missed habit reminders
    }

    /**
     * Per-reminder mutex to serialize alarm scheduling.
     * Prevents the race condition where deliverReminder's handleMissedReminder coroutine
     * and markReminderDone's coroutine both call cancelReminder() + scheduleReminder()
     * concurrently on the same reminderId, causing intermittent alarm loss.
     */
    private val schedulingLocks = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()

    // ── FORMATTING ──

    private fun formatTime(millis: Long): String =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(millis))

    // ── SCHEDULING ──

    fun scheduleTaskReminder(
        taskId: Long,
        title: String,
        message: String,
        scheduledAt: Long,
        reminderMode: ReminderMode,
        intervalMinutes: Int = 30
    ) = scope.launch {
        val isCritical = reminderMode == ReminderMode.CRITICAL
        val maxRepeats = when (reminderMode) {
            ReminderMode.AGGRESSIVE -> 20
            ReminderMode.PERSISTENT -> 50
            else -> 0
        }

        val reminder = ReminderEntity(
            taskId = taskId,
            title = title,
            message = message,
            scheduledAt = scheduledAt,
            reminderMode = reminderMode.name,
            intervalMinutes = intervalMinutes,
            isCritical = isCritical,
            maxRepeats = maxRepeats,
            lastTriggeredAt = null
        )
        val id = reminderDao.insertReminder(reminder)
        alarmScheduler.scheduleReminder(
            reminderId = id, title = title, message = message,
            triggerAtMillis = scheduledAt, reminderMode = reminderMode,
            isCritical = isCritical, taskId = taskId
        )
        Log.d(TAG, "Scheduled task reminder $id: $title at $scheduledAt (mode=$reminderMode)")
    }

    /**
     * Schedule a habit reminder. Creates a reminder entity that persists across
     * the entire day — NOT a one-shot. The same entity is reused for every
     * scheduled occurrence throughout the active window.
     */
    fun scheduleHabitReminder(
        habitId: Long,
        title: String,
        message: String,
        scheduledAt: Long,
        intervalMinutes: Int,
        reminderMode: ReminderMode = ReminderMode.AGGRESSIVE
    ) = scope.launch {
        val now = System.currentTimeMillis()
        Log.d(TAG, "[HABIT-SCHEDULE-START] time=${formatTime(now)} Habit $habitId: scheduling at ${formatTime(scheduledAt)}, frequency=$intervalMinutes min, mode=$reminderMode")

        val habit = habitDao.getHabitById(habitId)
        val progressMsg = habit?.let {
            "Target: ${it.todayCompletions} / ${it.dailyTargetCount} — $message"
        } ?: message

        val isCritical = reminderMode == ReminderMode.CRITICAL
        val repeatInterval = if (reminderMode == ReminderMode.AGGRESSIVE) {
            HABIT_REPEAT_INTERVAL_MIN
        } else {
            intervalMinutes.coerceAtLeast(HABIT_REPEAT_INTERVAL_MIN)
        }
        val maxRepeats = when (reminderMode) {
            ReminderMode.AGGRESSIVE -> 100
            ReminderMode.PERSISTENT -> 50
            else -> 0
        }

        val reminder = ReminderEntity(
            habitId = habitId,
            title = title,
            message = progressMsg,
            scheduledAt = scheduledAt,
            reminderMode = reminderMode.name,
            intervalMinutes = repeatInterval,
            isCritical = isCritical,
            maxRepeats = maxRepeats,
            lastTriggeredAt = null
        )
        val id = reminderDao.insertReminder(reminder)
        Log.d(TAG, "[HABIT-SCHEDULE-DB] time=${formatTime(now)} Habit $habitId: entity id=$id, nagInterval=${repeatInterval}min, freq=${intervalMinutes}min")

        alarmScheduler.scheduleReminder(
            reminderId = id, title = title, message = progressMsg,
            triggerAtMillis = scheduledAt, reminderMode = reminderMode,
            isCritical = isCritical, habitId = habitId
        )

        Log.d(TAG, "[HABIT-SCHEDULE-COMPLETE] time=${formatTime(now)} Habit $habitId '$title': ✅ reminder $id at ${formatTime(scheduledAt)}")
    }

    // ── ACTIONS ──

    /**
     * Mark reminder as DONE.
     *
     * TASKS: Deactivate immediately — one-and-done completion.
     * HABITS: Increment completion count, check target.
     *   - Target reached → stop reminders for today
     *   - Target NOT reached → schedule next occurrence at now + frequencyMinutes
     *
     * The SAME ReminderEntity is reused for the entire day.
     * is_active stays true until target reached or habit deleted.
     */
    fun markReminderDone(reminderId: Long, taskId: Long?, habitId: Long?) = scope.launch {
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now().toString()
        val timeStr = formatTime(now)

        // ── TASK path: deactivate immediately ──
        if (taskId != null) {
            reminderDao.deactivateReminder(reminderId)
            notificationHelper.cancelReminderNotification(reminderId)
            alarmScheduler.cancelReminder(reminderId)
            taskDao.markTaskCompleted(taskId, now, now)
            recordTaskCompletion(todayStr)
            Log.d(TAG, "[TASK-DONE] time=$timeStr Task $taskId completed — chain stopped")
            return@launch
        }

        // ── HABIT path: increment count, check target, schedule next ──
        if (habitId != null) {
            notificationHelper.cancelReminderNotification(reminderId)

            // Increment completion count (DB handles daily reset via CASE WHEN)
            habitDao.markHabitCompleted(habitId, todayStr, now, now)
            recordHabitCompletion(todayStr)

            // Reload to get fresh counts
            val habit = habitDao.getHabitById(habitId)
            if (habit == null) {
                reminderDao.deactivateReminder(reminderId)
                alarmScheduler.cancelReminder(reminderId)
                Log.w(TAG, "[HABIT-DONE-ERR] Habit $habitId deleted — cleanup")
                return@launch
            }

            val todayStr = LocalDate.now().toString()
            // markHabitCompleted already handled daily reset via SQL
            val completions = habit.todayCompletions
            val target = habit.dailyTargetCount
            Log.d(TAG, "[HABIT-DONE] time=$timeStr Habit '${habit.name}': $completions / $target completions (pressed DONE)")

            if (completions >= target) {
                // 🎯 TARGET REACHED — schedule next for TOMORROW (do NOT deactivate!)
                // Keeping is_active=true ensures the daily restart works.
                // Tomorrow, the DB query resets todayCompletions to 0 automatically.
                val tomorrowZone = LocalDate.now().plusDays(1)
                val startTime = try { LocalTime.parse(habit.startTime) } catch (_: Exception) { LocalTime.of(8, 0) }
                val tomorrowStart = LocalDateTime.of(tomorrowZone, startTime)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                // Load the reminder entity to get its mode/critical settings
                val reminderEntity = reminderDao.getReminderById(reminderId)
                val mode = reminderEntity?.reminderMode ?: "AGGRESSIVE"
                val critical = reminderEntity?.isCritical ?: false

                alarmScheduler.cancelReminder(reminderId)
                reminderDao.rescheduleReminder(reminderId, tomorrowStart, now)
                alarmScheduler.scheduleReminder(
                    reminderId = reminderId, title = habit.name,
                    message = "Target: $completions / $target — Time for: ${habit.name}",
                    triggerAtMillis = tomorrowStart,
                    reminderMode = ReminderMode.valueOf(mode),
                    isCritical = critical,
                    taskId = null, habitId = habitId
                )
                Log.d(TAG, "[HABIT-TARGET-REACHED] 🎯 Habit '${habit.name}': $completions/$target target reached — NO MORE reminders today, next at tomorrow ${formatTime(tomorrowStart)}")
                return@launch
            }

            // Schedule next occurrence at now + frequency
            scheduleNextHabitOccurrence(reminderId, habit, now, incrementCount = false)
        }
    }

    /**
     * Snooze a reminder.
     *
     * TASKS: Push forward by snoozeMinutes, keep repeating.
     * HABITS: Skip this occurrence — schedule next at now + frequencyMinutes.
     *   Does NOT increment completion count. Only DONE counts.
     *   If snooze would push past endTime, schedule for tomorrow's startTime.
     */
    fun snoozeReminder(reminderId: Long, snoozeMinutes: Int = 15) = scope.launch {
        val now = System.currentTimeMillis()
        val timeStr = formatTime(now)
        val reminder = reminderDao.getReminderById(reminderId) ?: return@launch

        notificationHelper.cancelReminderNotification(reminderId)
        alarmScheduler.cancelReminder(reminderId)

        // ── HABIT path: skip to next frequency interval ──
        if (reminder.habitId != null) {
            val habit = habitDao.getHabitById(reminder.habitId)
            if (habit != null) {
                val frequencyMs = habit.frequencyMinutes * 60 * 1000L
                var nextTime = now + frequencyMs
                Log.d(TAG, "[HABIT-SNOOZE] time=$timeStr Habit '${habit.name}': skipping this occurrence, next raw=${formatTime(nextTime)} (freq=${habit.frequencyMinutes}min)")

                // Check window boundary
                val withinWindow = isWithinActiveWindow(habit, nextTime)
                if (!withinWindow.first) {
                    nextTime = withinWindow.second  // Tomorrow's start
                    Log.d(TAG, "[HABIT-SNOOZE-TOMORROW] Snooze outside window → scheduling for tomorrow ${formatTime(nextTime)}")
                }

                reminderDao.snoozeReminder(reminderId, nextTime, now)
                alarmScheduler.scheduleReminder(
                    reminderId = reminderId, title = reminder.title,
                    message = "Target: ${habit.todayCompletions} / ${habit.dailyTargetCount} — Time for: ${habit.name}",
                    triggerAtMillis = nextTime,
                    reminderMode = ReminderMode.valueOf(reminder.reminderMode),
                    isCritical = reminder.isCritical,
                    taskId = null, habitId = reminder.habitId
                )
                Log.d(TAG, "[HABIT-SNOOZE-COMPLETE] Habit '${habit.name}' snoozed → next at ${formatTime(nextTime)} (progress: ${habit.todayCompletions}/${habit.dailyTargetCount}, count NOT incremented)")
                return@launch
            }
        }

        // ── TASK path: fixed snooze duration ──
        val nextTime = now + (snoozeMinutes * 60 * 1000L)
        reminderDao.snoozeReminder(reminderId, nextTime, now)
        alarmScheduler.scheduleReminder(
            reminderId = reminderId, title = reminder.title,
            message = reminder.message, triggerAtMillis = nextTime,
            reminderMode = ReminderMode.valueOf(reminder.reminderMode),
            isCritical = reminder.isCritical,
            taskId = reminder.taskId, habitId = reminder.habitId
        )
        Log.d(TAG, "[SNOOZE] time=$timeStr Task Reminder $reminderId snoozed: next at ${formatTime(nextTime)} (+${snoozeMinutes}min)")
    }

    /**
     * Handle an ignored/missed reminder: reschedule automatically for repeat-until-done.
     *
     * For habits: nags at repeatInterval (1 min for AGGRESSIVE) until responded to.
     * Before rescheduling, checks:
     *   1. Is the habit still within the active window?
     *   2. Has the daily target been reached?
     * If either check fails → stop reminding for today.
     */
    fun handleMissedReminder(reminderId: Long) = scope.launch {
        val now = System.currentTimeMillis()
        val timeStr = formatTime(now)

        val reminder = reminderDao.getReminderById(reminderId)
        if (reminder == null) {
            Log.e(TAG, "[HABIT-FIRED-ERR] time=$timeStr reminder $reminderId NOT FOUND — chain BROKEN")
            return@launch
        }

        val isHabit = reminder.habitId != null
        val isTask = reminder.taskId != null
        val originalScheduledAt = reminder.scheduledAt  // Snapshot for race guard
        Log.d(TAG, "[${if (isHabit) "HABIT" else "TASK"}-FIRED] time=$timeStr id=$reminderId title=${reminder.title} habitId=${reminder.habitId} taskId=${reminder.taskId} interval=${reminder.intervalMinutes}min missedCount=${reminder.missedCount} scheduledAt=${formatTime(originalScheduledAt)}")

        // ── TASK: check if already completed ──
        if (isTask) {
            val isTaskDone = reminder.taskId?.let { tid ->
                taskDao.getTaskById(tid)?.isCompleted ?: false
            } ?: false

            if (isTaskDone) {
                reminderDao.deactivateReminder(reminderId)
                alarmScheduler.cancelReminder(reminderId)
                notificationHelper.cancelReminderNotification(reminderId)
                Log.d(TAG, "[TASK-FIRED] time=$timeStr Task already done — chain stopped")
                return@launch
            }
        }

        // ── HABIT: check target AND window before rescheduling ──
        if (isHabit) {
            val habitId = reminder.habitId!!
            val habit = habitDao.getHabitById(habitId)
            if (habit == null) {
                reminderDao.deactivateReminder(reminderId)
                alarmScheduler.cancelReminder(reminderId)
                notificationHelper.cancelReminderNotification(reminderId)
                Log.w(TAG, "[HABIT-FIRED-ERR] Habit $habitId deleted — cleanup")
                return@launch
            }

            // Check 1: target already reached? (account for daily reset)
            val todayStr = LocalDate.now().toString()
            val effectiveCompletions = if (habit.completionDate == todayStr) {
                habit.todayCompletions
            } else {
                // New day — yesterday's count is stale, treat as 0
                Log.d(TAG, "[HABIT-NEW-DAY] Habit '${habit.name}': new day detected (completionDate=${habit.completionDate}, today=$todayStr) — resetting count to 0")
                0
            }

            if (effectiveCompletions >= habit.dailyTargetCount) {
                // Schedule for tomorrow — do NOT deactivate (needed for daily restart)
                val tomorrowZone = LocalDate.now().plusDays(1)
                val startTime = try { LocalTime.parse(habit.startTime) } catch (_: Exception) { LocalTime.of(8, 0) }
                val tomorrowStart = LocalDateTime.of(tomorrowZone, startTime)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                alarmScheduler.cancelReminder(reminderId)
                notificationHelper.cancelReminderNotification(reminderId)
                reminderDao.rescheduleReminder(reminderId, tomorrowStart, now)
                alarmScheduler.scheduleReminder(
                    reminderId = reminderId, title = reminder.title,
                    message = "Target: ${habit.todayCompletions} / ${habit.dailyTargetCount} — Time for: ${habit.name}",
                    triggerAtMillis = tomorrowStart,
                    reminderMode = ReminderMode.valueOf(reminder.reminderMode),
                    isCritical = reminder.isCritical,
                    taskId = null, habitId = habitId
                )
                Log.d(TAG, "[HABIT-TARGET-REACHED] 🎯 Habit '${habit.name}': ${effectiveCompletions}/${habit.dailyTargetCount} reached — scheduled for tomorrow ${formatTime(tomorrowStart)}")
                return@launch
            }

            // Check 2: within active window?
            val withinWindow = isWithinActiveWindow(habit, now)
            if (!withinWindow.first) {
                // Outside window → schedule for tomorrow's start
                val tomorrowStart = withinWindow.second
                reminderDao.markReminderMissed(reminderId, now)
                alarmScheduler.cancelReminder(reminderId)
                reminderDao.rescheduleReminder(reminderId, tomorrowStart, now)
                alarmScheduler.scheduleReminder(
                    reminderId = reminderId, title = reminder.title,
                    message = "Target: ${habit.todayCompletions} / ${habit.dailyTargetCount} — ${reminder.message}",
                    triggerAtMillis = tomorrowStart,
                    reminderMode = ReminderMode.valueOf(reminder.reminderMode),
                    isCritical = reminder.isCritical,
                    taskId = null, habitId = habitId
                )
                Log.d(TAG, "[HABIT-OUTSIDE-WINDOW] time=$timeStr Outside window (${habit.startTime}-${habit.endTime}) → scheduled for tomorrow ${formatTime(tomorrowStart)}")
                return@launch
            }
        }

        // ── RACE GUARD: Check if another action (markReminderDone/snoozeReminder) ──
        // already rescheduled this reminder. deliverReminder launches handleMissedReminder
        // concurrently with user actions from the popup. If the user pressed DONE/SNOOZE,
        // scheduled_at will have changed — we must NOT overwrite their schedule.
        if (isHabit) {
            val currentState = reminderDao.getReminderById(reminderId)
            if (currentState != null && currentState.scheduledAt != originalScheduledAt) {
                Log.w(TAG, "[HABIT-RACE-GUARD] time=$timeStr Reminder $reminderId: already rescheduled by concurrent action (scheduled_at: ${formatTime(originalScheduledAt)} → ${formatTime(currentState.scheduledAt)}) — aborting nag, user action wins")
                return@launch
            }
        }

        // ── Record as missed + reschedule nag ──
        reminderDao.markReminderMissed(reminderId, now)

        val repeatInterval = reminder.intervalMinutes * 60 * 1000L
        val nextTime = now + repeatInterval

        // Check window for the nag time too
        if (isHabit) {
            val habit = habitDao.getHabitById(reminder.habitId!!)
            if (habit != null) {
                val withinWindow = isWithinActiveWindow(habit, nextTime)
                if (!withinWindow.first) {
                    val tomorrowStart = withinWindow.second
                    alarmScheduler.cancelReminder(reminderId)
                    reminderDao.rescheduleReminder(reminderId, tomorrowStart, now)
                    alarmScheduler.scheduleReminder(
                        reminderId = reminderId, title = reminder.title,
                        message = "⏰ ${reminder.title} (Overdue: ${(reminder.missedCount + 1) * reminder.intervalMinutes} min)",
                        triggerAtMillis = tomorrowStart,
                        reminderMode = ReminderMode.valueOf(reminder.reminderMode),
                        isCritical = reminder.isCritical,
                        taskId = null, habitId = reminder.habitId
                    )
                    Log.d(TAG, "[HABIT-NAG-OUTSIDE] Nag would be outside window → tomorrow ${formatTime(tomorrowStart)}")
                    return@launch
                }
            }
        }

        val overdueMsg = buildOverdueMessage(reminder)

        alarmScheduler.cancelReminder(reminderId)
        Log.d(TAG, "[${if (isHabit) "HABIT" else "TASK"}-NEXT-CANCEL] Cancelled old alarm")

        reminderDao.rescheduleReminder(reminderId, nextTime, now)

        alarmScheduler.scheduleReminder(
            reminderId = reminderId, title = reminder.title,
            message = overdueMsg,
            triggerAtMillis = nextTime,
            reminderMode = ReminderMode.valueOf(reminder.reminderMode),
            isCritical = reminder.isCritical,
            taskId = reminder.taskId, habitId = reminder.habitId
        )

        Log.d(TAG, "[${if (isHabit) "HABIT" else "TASK"}-NEXT-SCHEDULED] ✅ Next alarm at ${formatTime(nextTime)} (+${reminder.intervalMinutes}min) | $overdueMsg")

        // Update notification content
        notificationHelper.updateNotificationContent(
            reminderId = reminderId,
            newTitle = reminder.title,
            newMessage = overdueMsg,
            taskId = reminder.taskId,
            habitId = reminder.habitId
        )
    }

    /**
     * Schedule the NEXT occurrence of a habit after a DONE action.
     * Called from markReminderDone when target not yet reached.
     *
     * Calculates next time as: now + habit.frequencyMinutes
     * If next time > endTime: schedule for tomorrow's startTime
     * If now < startTime: schedule for today's startTime
     */
    private suspend fun scheduleNextHabitOccurrence(
        reminderId: Long,
        habit: HabitEntity,
        now: Long,
        incrementCount: Boolean
    ) {
        val timeStr = formatTime(now)
        val frequencyMs = habit.frequencyMinutes * 60 * 1000L
        var nextTime = now + frequencyMs

        Log.d(TAG, "[HABIT-NEXT-CALC] Habit '${habit.name}': now=${formatTime(now)}, frequency=${habit.frequencyMinutes}min, rawNext=${formatTime(nextTime)}")

        // Check window: is nextTime within startTime–endTime today?
        val withinWindow = isWithinActiveWindow(habit, nextTime)
        if (!withinWindow.first) {
            nextTime = withinWindow.second  // Tomorrow's start time
            Log.d(TAG, "[HABIT-NEXT-TOMORROW] Next occurrence outside window → scheduling for tomorrow ${formatTime(nextTime)}")
        }

        // Build progress message
        val progressMsg = "Target: ${habit.todayCompletions} / ${habit.dailyTargetCount} — Time for: ${habit.name}"

        // Cancel old alarm + update DB
        alarmScheduler.cancelReminder(reminderId)
        reminderDao.rescheduleReminder(reminderId, nextTime, now)

        // Schedule new alarm
        val reminder = reminderDao.getReminderById(reminderId) ?: run {
            Log.e(TAG, "[HABIT-NEXT-ERR] Reminder $reminderId disappeared from DB")
            return
        }

        alarmScheduler.scheduleReminder(
            reminderId = reminderId,
            title = reminder.title,
            message = progressMsg,
            triggerAtMillis = nextTime,
            reminderMode = ReminderMode.valueOf(reminder.reminderMode),
            isCritical = reminder.isCritical,
            taskId = null,
            habitId = habit.id
        )

        Log.d(TAG, "[HABIT-NEXT-SCHEDULED] ✅ Habit '${habit.name}': next at ${formatTime(nextTime)} (${habit.todayCompletions}/${habit.dailyTargetCount})")
    }

    /**
     * Check whether a given time falls within the habit's active window today.
     * Returns Pair(isWithinWindow, nextValidTime).
     *
     * - If millis is within [startTime, endTime] today → (true, millis)
     * - If millis is before startTime today → (false, today's startTime)
     * - If millis is after endTime today → (false, tomorrow's startTime)
     */
    private fun isWithinActiveWindow(habit: HabitEntity, millis: Long): Pair<Boolean, Long> {
        val today = LocalDate.now()
        val startTime = try { LocalTime.parse(habit.startTime) } catch (_: Exception) { LocalTime.of(8, 0) }
        val endTime = try { LocalTime.parse(habit.endTime) } catch (_: Exception) { LocalTime.of(22, 0) }

        val zone = ZoneId.systemDefault()
        val startMillis = LocalDateTime.of(today, startTime).atZone(zone).toInstant().toEpochMilli()
        val endMillis = LocalDateTime.of(today, endTime).atZone(zone).toInstant().toEpochMilli()

        return when {
            millis < startMillis -> Pair(false, startMillis)                          // Before window → wait for start
            millis > endMillis -> {
                val tomorrowStart = LocalDateTime.of(today.plusDays(1), startTime)
                    .atZone(zone).toInstant().toEpochMilli()
                Pair(false, tomorrowStart)                                            // After window → tomorrow
            }
            else -> Pair(true, millis)                                                // Within window
        }
    }

    /**
     * Build a dynamic overdue message that escalates with each missed repeat.
     */
    private fun buildOverdueMessage(reminder: ReminderEntity): String {
        val totalMissed = reminder.missedCount + 1
        val overdueMinutes = totalMissed * reminder.intervalMinutes
        return "⏰ ${reminder.title} (Overdue: $overdueMinutes min)"
    }

    // ── BOOT / RECOVERY ──

    /**
     * Reschedule all active reminders after device boot.
     * For habits: respects the active window — if outside, schedules for next window start.
     */
    fun rescheduleAllActiveReminders() = scope.launch {
        val now = System.currentTimeMillis()
        val activeReminders = reminderDao.getDueReminders(Long.MAX_VALUE)
        Log.d(TAG, "[BOOT-RESCHEDULE] Rescheduling ${activeReminders.size} active reminders after boot")

        activeReminders.forEach { reminder ->
            var nextTime = if (reminder.scheduledAt <= now) {
                now + 3000  // Due now → fire in 3 seconds
            } else {
                reminder.scheduledAt  // Future → keep as-is
            }

            // For habits: check active window and target
            if (reminder.habitId != null) {
                val habit = habitDao.getHabitById(reminder.habitId)
                if (habit != null) {
                    val todayStr = LocalDate.now().toString()
                    val effectiveCompletions = if (habit.completionDate == todayStr) {
                        habit.todayCompletions
                    } else {
                        0  // New day — reset
                    }

                    // Check daily target
                    if (effectiveCompletions >= habit.dailyTargetCount) {
                        Log.d(TAG, "[BOOT-RESCHEDULE] Habit '${habit.name}': target ${effectiveCompletions}/${habit.dailyTargetCount} reached — skipping")
                        return@forEach
                    }

                    // Check window
                    val withinWindow = isWithinActiveWindow(habit, nextTime)
                    if (!withinWindow.first) {
                        nextTime = withinWindow.second
                        Log.d(TAG, "[BOOT-RESCHEDULE] Habit '${habit.name}': outside window → scheduling for ${formatTime(nextTime)}")
                    }
                }
            }

            if (reminder.scheduledAt <= now) {
                reminderDao.rescheduleReminder(reminder.id, nextTime, now)
            }

            alarmScheduler.scheduleReminder(
                reminderId = reminder.id, title = reminder.title,
                message = reminder.message, triggerAtMillis = nextTime,
                reminderMode = ReminderMode.valueOf(reminder.reminderMode),
                isCritical = reminder.isCritical,
                taskId = reminder.taskId, habitId = reminder.habitId
            )
        }
    }

    // ── ANALYTICS RECORDING ──

    private suspend fun recordTaskCompletion(today: String) {
        val existing = analyticsDao.getAnalyticsForDate(today)
        val entity = if (existing != null) {
            existing.copy(tasksCompleted = existing.tasksCompleted + 1)
        } else {
            AnalyticsEntity(date = today, tasksCompleted = 1)
        }
        analyticsDao.insertOrUpdateAnalytics(entity)
    }

    private suspend fun recordHabitCompletion(today: String) {
        val existing = analyticsDao.getAnalyticsForDate(today)
        val entity = if (existing != null) {
            existing.copy(habitsCompleted = existing.habitsCompleted + 1)
        } else {
            AnalyticsEntity(date = today, habitsCompleted = 1)
        }
        analyticsDao.insertOrUpdateAnalytics(entity)
    }
}
