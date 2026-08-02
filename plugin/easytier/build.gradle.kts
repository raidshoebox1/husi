plugins {
    id("com.android.application")
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    defaultConfig {
        applicationId = "fr.husi.plugin.easytier"
    }
    namespace = "fr.husi.plugin.easytier"
}

setupPlugin("easytier")
