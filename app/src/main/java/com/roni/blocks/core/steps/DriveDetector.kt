package com.roni.blocks.core.steps

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.roni.blocks.core.alarms.DriveTransitionReceiver
import com.roni.blocks.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers Android's activity-transition updates for "in a car" (3.4.0) so [DriveTransitionReceiver]
 * can log the steps counted during a drive. Rules: only registers when the activity-recognition
 * permission is granted, and every failure is logged, never thrown, so a phone without Play Services
 * simply reports no drives.
 *
 * Talks to: [DriveTransitionReceiver], [AppLogger].
 */
@Singleton
class DriveDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) {
    fun register() {
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            logger.info("DriveDetector: no activity-recognition permission; drives are off")
            return
        }
        try {
            val transitions = listOf(
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
            )
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, DriveTransitionReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent)
                .addOnFailureListener { e -> logger.error("DriveDetector: registration failed", e) }
        } catch (t: Throwable) {
            logger.error("DriveDetector: registration failed", t)
        }
    }
}
