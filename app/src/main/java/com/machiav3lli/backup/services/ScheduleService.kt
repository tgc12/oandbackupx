/*
 * OAndBackupX: open-source apps backup and restore app.
 * Copyright (C) 2020  Antonios Hazim
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.machiav3lli.backup.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.machiav3lli.backup.R
import com.machiav3lli.backup.activities.MainActivityX
import com.machiav3lli.backup.activities.SchedulerActivityX
import com.machiav3lli.backup.classAddress
import com.machiav3lli.backup.dbs.ScheduleDao
import com.machiav3lli.backup.dbs.ScheduleDatabase.Companion.getInstance
import com.machiav3lli.backup.handler.AlarmsHandler
import com.machiav3lli.backup.handler.BackupRestoreHelper.OnBackupRestoreListener
import com.machiav3lli.backup.handler.ScheduledBackupsHandler
import timber.log.Timber

// TODO Migrate to libsu's RootService?
class ScheduleService : Service(), OnBackupRestoreListener {

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val id = intent.getIntExtra(classAddress(".schedule_id"), -1)
        if (id >= 0) {
            val handleAlarms = AlarmsHandler(this)
            val handleScheduledBackups = scheduledBackupsHandler
            handleScheduledBackups.setOnBackupListener(this)
            Thread {
                val scheduleDao = scheduleDao
                val schedule = scheduleDao.getSchedule(id.toLong())
                schedule!!.timePlaced = System.currentTimeMillis()
                scheduleDao.update(schedule)
                // fix the time at which the alarm will be run the next time.
                // it can be wrong when scheduled in BootReceiver#onReceive()
                // to be run after AlarmManager.INTERVAL_FIFTEEN_MINUTES
                handleAlarms.setAlarm(id, schedule.interval, schedule.timeHour, schedule.timeMinute)
                Timber.i(getString(R.string.sched_startingbackup))
                handleScheduledBackups.initiateBackup(id, schedule.mode, schedule.subMode,
                        schedule.excludeSystem, schedule.customList)
            }.start()
        } else {
            Timber.e("got id: $id from $intent")
        }
        return START_NOT_STICKY
    }


    private val scheduledBackupsHandler: ScheduledBackupsHandler
        get() = ScheduledBackupsHandler(this)

    private val scheduleDao: ScheduleDao
        get() = getInstance(this, SchedulerActivityX.SCHEDULES_DB_NAME).scheduleDao

    override fun onCreate() {
        val channelId = classAddress("ScheduleService")
        // Do some initialization
        MainActivityX.initShellHandler()
        val notificationChannel = NotificationChannel(channelId, channelId,
                NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(notificationChannel)
        } else {
            Timber.w("Unable to create notification channel")
            Toast.makeText(this, getString(
                    R.string.error_creating_notification_channel), Toast.LENGTH_LONG).show()
        }
        val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_app)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        startForeground(ID, notification)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        stopForeground(true)
    }

    override fun onBackupRestoreDone() {
        stopSelf()
    }

    companion object {
        private const val ID = 2
    }
}