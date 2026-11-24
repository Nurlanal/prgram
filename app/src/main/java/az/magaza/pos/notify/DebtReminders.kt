package az.magaza.pos.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import az.magaza.pos.MainActivity
import az.magaza.pos.PosApp
import az.magaza.pos.R
import az.magaza.pos.data.DebtSchedule
import az.magaza.pos.util.Dates
import az.magaza.pos.util.Money
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Borc xatırlatmaları: ödəniş günü gələn (və gecikmiş) borclar üçün telefona bildiriş.
 * İnternet tələb etmir — WorkManager telefonda lokal işləyir və telefon yenidən başladılanda da davam edir.
 */
object DebtReminders {
    const val CHANNEL = "debt_reminders"
    const val EXTRA_CUSTOMER = "open_customer_id"
    private const val WORK = "debt-reminder-check"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Borc xatırlatmaları", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Müştəri borcunun ödəniş günü gələndə bildiriş"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    /** Hər saat yoxlayır; bildiriş yalnız seçilmiş saatdan sonra və gündə bir dəfə göndərilir. */
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<DebtReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Proqram açılanda dərhal bir dəfə yoxla. */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$WORK-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<DebtReminderWorker>().build()
        )
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    data class Reminder(val customerId: Long, val customerName: String, val amount: Long, val daysLate: Int, val keys: List<String>)

    /** Bu gün bildiriş göndərilməli olan borcları hesablayır (test üçün ayrıca). */
    suspend fun due(app: PosApp, now: Long = System.currentTimeMillis()): List<Reminder> {
        val dao = app.repo.dao
        val today = Dates.startOfDay(now)
        val customers = dao.allCustomers().associateBy { it.id }
        return DebtSchedule.openByCustomer(dao.allDebtTx()).mapNotNull { (cid, open) ->
            val dueNow = open.filter { it.dueAt != null && Dates.startOfDay(it.dueAt!!) <= today }
            val keys = dueNow.map { "${it.tx.id}@$today" }.filterNot { app.settings.wasReminded(it) }
            if (keys.isEmpty()) return@mapNotNull null
            Reminder(
                customerId = cid,
                customerName = customers[cid]?.name ?: "Müştəri",
                amount = dueNow.sumOf { it.remaining },
                daysLate = dueNow.maxOf { -(it.daysLeft(now) ?: 0) },
                keys = keys,
            )
        }
    }

    suspend fun run(context: Context, now: Long = System.currentTimeMillis()): Int {
        val app = context.applicationContext as PosApp
        val s = app.settings.state.value
        if (!s.debtReminders) return 0
        if (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) < s.reminderHour) return 0
        if (!canNotify(context)) return 0
        createChannel(context)
        val list = due(app, now)
        list.forEach { notify(context, it) }
        app.settings.markReminded(list.flatMap { it.keys }, Dates.startOfDay(now))
        return list.size
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun notify(context: Context, r: Reminder) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CUSTOMER, r.customerId)
        }
        val pi = PendingIntent.getActivity(context, r.customerId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (r.daysLate > 0) "Gecikmiş borc: ${r.customerName}" else "Bu gün borc ödəniş günüdür"
        val text = if (r.daysLate > 0) "${Money.format(r.amount)} — ${r.daysLate} gün gecikib"
        else "${r.customerName}: ${Money.format(r.amount)} bu gün ödənilməlidir"
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(10_000 + r.customerId.toInt(), n)
    }
}

class DebtReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = runCatching { DebtReminders.run(applicationContext) }
        .fold({ Result.success() }, { Result.retry() })
}
