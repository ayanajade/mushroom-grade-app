// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    id("com.android.application") version "8.7.3" apply false  // ✅ Updated from 8.12.3
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false  // ✅ Updated from 1.9.20
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false  // ✅ Updated from 1.9.20-1.0.14
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
