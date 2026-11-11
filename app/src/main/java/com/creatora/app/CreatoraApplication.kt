package com.creatora.app

import android.app.Application
import com.creatora.app.data.cloud.AppCheckSetup
import com.creatora.app.data.notify.NotificationChannels
import com.creatora.app.data.notify.PollScheduler
import com.google.firebase.FirebaseApp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CreatoraApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isNotEmpty()) AppCheckSetup.install()
        NotificationChannels.create(this)
        PollScheduler.schedule(this)
    }

    // Lets AsyncImage show a frame from imported videos as well as images.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
}
