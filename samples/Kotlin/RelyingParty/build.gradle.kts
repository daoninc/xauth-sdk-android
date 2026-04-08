// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(apps.plugins.android.application) version "8.13.0" apply false
    alias(apps.plugins.hilt.android) apply false
    alias(apps.plugins.google.services) apply false
    alias(apps.plugins.compose.compiler) apply false
}

tasks.create<Delete>("clean") { delete(rootProject.layout.buildDirectory) }
