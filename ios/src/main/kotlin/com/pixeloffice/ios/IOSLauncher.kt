package com.pixeloffice.ios

import com.badlogic.gdx.backends.iosrobovm.IOSApplication
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration
import com.pixeloffice.PixelOfficeGame
import org.robovm.apple.foundation.NSAutoreleasePool
import org.robovm.apple.uikit.UIApplication

class IOSLauncher : IOSApplication.Delegate() {
    override fun createApplication(): IOSApplication {
        val config = IOSApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            allowIpod = true
        }
        return IOSApplication(PixelOfficeGame(), config)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val pool = NSAutoreleasePool()
            UIApplication.main<UIApplication, IOSLauncher>(args, null, IOSLauncher::class.java)
            pool.close()
        }
    }
}
