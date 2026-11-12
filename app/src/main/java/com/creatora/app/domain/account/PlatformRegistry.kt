package com.creatora.app.domain.account

/** Finds the adapter for a platform. Platforms without an implementation answer "not available". */
class PlatformRegistry(private val adapters: List<PlatformAdapter>) {
    fun adapter(platform: ConnectPlatform): PlatformAdapter =
        adapters.firstOrNull { it.platform == platform } ?: UnavailableAdapter(platform)
}
