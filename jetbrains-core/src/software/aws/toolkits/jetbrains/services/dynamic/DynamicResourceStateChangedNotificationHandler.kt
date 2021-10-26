// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import software.amazon.awssdk.services.cloudcontrol.model.Operation
import software.amazon.awssdk.services.cloudcontrol.model.OperationStatus
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.explorer.ExplorerToolWindow
import software.aws.toolkits.jetbrains.services.dynamic.DynamicResourceTelemetryResources.addOperationToTelemetry
import software.aws.toolkits.jetbrains.services.dynamic.explorer.DynamicResourceNode
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.DynamicresourceTelemetry
import software.aws.toolkits.telemetry.Result
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

class DynamicResourceStateChangedNotificationHandler(private val project: Project) : DynamicResourceStateMutationHandler {
    private val refreshRequired = AtomicBoolean(false)
    override fun mutationStatusChanged(state: ResourceMutationState) {
        if (state.status == OperationStatus.SUCCESS) {
            if (state.operation == Operation.UPDATE) {
                refreshViewEditableDynamicResourceVirtualFile(state)
            }
            notifyInfo(
                message(
                    "dynamic_resources.operation_status_notification_title",
                    state.resourceIdentifier ?: state.resourceType,
                    state.operation.name.toLowerCase()
                ),
                message(
                    "dynamic_resources.operation_status_success",
                    state.resourceIdentifier ?: state.resourceType,
                    state.operation.name.toLowerCase()
                ),
                project
            )
            DynamicresourceTelemetry.mutateResource(
                project,
                Result.Succeeded,
                state.resourceType,
                addOperationToTelemetry(state.operation),
                ChronoUnit.MILLIS.between(state.startTime, DynamicResourceTelemetryResources.getCurrentTime()).toDouble()
            )
        } else if (state.status == OperationStatus.FAILED) {
            if (state.message.isNullOrBlank()) {
                displayErrorMessage(
                    state,
                    message(
                        "dynamic_resources.operation_status_failed_no_message",
                        state.resourceIdentifier ?: state.resourceType,
                        state.operation.name.toLowerCase()
                    )
                )
            } else {
                displayErrorMessage(
                    state,
                    message(
                        "dynamic_resources.operation_status_failed",
                        state.resourceIdentifier ?: state.resourceType,
                        state.operation.name.toLowerCase(),
                        state.message
                    )
                )
            }
            DynamicresourceTelemetry.mutateResource(
                project,
                Result.Failed,
                state.resourceType,
                addOperationToTelemetry(state.operation),
                ChronoUnit.MILLIS.between(state.startTime, DynamicResourceTelemetryResources.getCurrentTime()).toDouble()
            )
        }
        AwsResourceCache.getInstance().clear(CloudControlApiResources.listResources(state.resourceType), state.connectionSettings)
        refreshRequired.set(true)
    }

    private fun refreshViewEditableDynamicResourceVirtualFile(state: ResourceMutationState) {
        val file = state.resourceIdentifier?.let { DynamicResourceIdentifier(state.connectionSettings, state.resourceType, it) }?.let {
            CloudControlApiResourcesUtils.getResourceFile(project,
                it
            )
        } as? ViewEditableDynamicResourceVirtualFile ?: return
        file.isWritable = true
        runInEdt {
            val psiFile = PsiManager.getInstance(project).findFile(file)
            psiFile?.text?.let { file.setContent(this, it, true) }
        }
    }

    private fun displayErrorMessage(state: ResourceMutationState, errorMessage: String) {
        notifyError(
            message(
                "dynamic_resources.operation_status_notification_title",
                state.resourceIdentifier ?: state.resourceType,
                state.operation.name.toLowerCase()
            ),
            errorMessage,
            project
        )
    }

    override fun statusCheckComplete() {
        runInEdt {
            if (refreshRequired.getAndSet(false)) {
                ExplorerToolWindow.getInstance(project).invalidateTree()
            }
        }
    }
}
