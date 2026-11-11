package com.creatora.app.domain.account

/** Finds the adapter for a platform. Platforms without an implementation answer "coming soon". */
class PlatformRegistry(private val adapters: List<PlatformAdapter>) {
    fun adapter(platform: ConnectPlatform): PlatformAdapter =
        adapters.firstOrNull { it.platform == platform } ?: ComingSoonAdapter(platform)
}
