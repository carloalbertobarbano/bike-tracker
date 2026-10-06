package app.pedal.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_TRACKING = "tracking"
    const val CHANNEL_ROUTE = "route_alerts"
    const val ID_TRACKING = 1
    const val ID_ROUTE = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TRACKING, "Ride recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a ride is being recorded"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ROUTE, "Route alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when you leave the route you're following"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
        )
    }
}
