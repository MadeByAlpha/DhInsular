package com.oasisfeng.island.analytics

import android.os.Bundle
import android.util.Log
import androidx.annotation.CheckResult
import androidx.annotation.Size
import com.oasisfeng.island.IslandApplication
import org.intellij.lang.annotations.Pattern

/**
 * Abstraction for analytics service
 *
 * Created by Oasis on 2016/5/26.
 */
@Deprecated("We do not collect analytics data")
interface Analytics {

    @Deprecated("We do not collect analytics data")
	interface Event {
        @Deprecated("We do not collect analytics data")
		@CheckResult fun with(key: Param, value: String?) = withRaw(key.key, value)
        @Deprecated("We do not collect analytics data")
		@CheckResult fun withRaw(key: String, value: String?): Event
        @Deprecated("We do not collect analytics data")
		fun send()
	}

    @Deprecated("We do not collect analytics data")
	enum class Param(@param:Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") val key: String) {
        @Deprecated("We do not collect analytics data")
		ITEM_ID(""),
		/** ITEM_CATEGORY and ITEM_NAME cannot be used together (limitation in Google Analytics implementation)  */
        @Deprecated("We do not collect analytics data")
		ITEM_NAME(""),
        @Deprecated("We do not collect analytics data")
		ITEM_CATEGORY(""),
        @Deprecated("We do not collect analytics data")
		LOCATION(""),
        @Deprecated("We do not collect analytics data")
		CONTENT("");
	}

    @Deprecated("We do not collect analytics data")
	@CheckResult fun event(@Size(min = 1, max = 40) @Pattern("^[a-zA-Z][a-zA-Z0-9_]*$") event: String): Event
    @Deprecated("We do not collect analytics data")
	fun reportEvent(event: String, params: Bundle)
    @Deprecated("We do not collect analytics data")
	fun trace(key: String, value: String): Analytics
    @Deprecated("We do not collect analytics data")
	fun trace(key: String, value: Int): Analytics
    @Deprecated("We do not collect analytics data")
	fun trace(key: String, value: Boolean): Analytics
    @Deprecated("We do not collect analytics data")
	fun report(t: Throwable)
    @Deprecated("We do not collect analytics data")
	fun report(message: String, t: Throwable)
    @Deprecated("We do not collect analytics data")
	fun logAndReport(tag: String, message: String, t: Throwable) { Log.e(tag, message, t); report(message, t) }

    @Deprecated("We do not collect analytics data")
	enum class Property(val key: String) {
        @Deprecated("We do not collect analytics data")
		DeviceOwner("device_owner"),
        @Deprecated("We do not collect analytics data")
		IslandSetup("island_setup"),
        @Deprecated("We do not collect analytics data")
		RemoteConfigAvailable("remote_config_avail"),
	}

    @Deprecated("We do not collect analytics data")
	fun setProperty(property: Property, @Size(max = 36) value: String)
    @Deprecated("We do not collect analytics data")
	fun setProperty(property: Property, value: Boolean): Analytics = this.also { setProperty(property, value.toString()) }

    @Deprecated("We do not collect analytics data")
	companion object {
        @Deprecated("We do not collect analytics data")
		@JvmStatic fun log(tag: String, message: String) { Log.i(tag, message); CrashReport.log("[$tag] $message") }
        @Deprecated("We do not collect analytics data")
		@JvmStatic @Suppress("FunctionName") fun `$`() = impl
        @Deprecated("We do not collect analytics data")
		operator fun invoke() = impl
		private val impl: Analytics = AnalyticsImpl(IslandApplication.get())
	}
}

@Deprecated("We do not collect analytics data")
fun analytics(): Analytics = Analytics()
