// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeFlavor

plugins {
    id("toolkit-intellij-subplugin")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IC)
}

dependencies {
    implementation(project(":plugin-amazonq:shared:jetbrains-community"))

    compileOnly(project(":plugin-core:jetbrains-community"))

    // delete when fully split
    // hack so we can compile while they break the cross-module dependency
    compileOnly(project(":plugin-amazonq:codetransform:jetbrains-community"))

    testImplementation(testFixtures(project(":plugin-core:jetbrains-community")))
}
