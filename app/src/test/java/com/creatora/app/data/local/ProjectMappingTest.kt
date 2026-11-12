package com.creatora.app.data.local

import com.creatora.app.domain.model.CreateMode
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.StartSource
import com.creatora.app.domain.model.StepState
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectMappingTest {
    @Test
    fun theCreateFields_surviveTheRoundTripToTheDatabaseRow() {
        val project = Project(
            id = 3, title = "Dal", format = VideoFormat.SHORTS_REELS, mode = CreateMode.AUTO, startedFrom = StartSource.LINK,
            sourceLink = "https://youtu.be/x", concept = "Lentils", angle = "Busy cooks", tone = "warm", audience = "beginners",
            targetSeconds = 25, stepStates = mapOf(WizardStep.BRIEF to StepState.APPROVED, WizardStep.SCRIPT to StepState.AI_DRAFT), counted = true,
        )

        assertEquals(project, project.toEntity().toModel())
    }

    @Test
    fun anOldRowWithoutTheNewColumns_readsAsAStepByStepProject() {
        val entity = Project(title = "Old").toEntity().copy(mode = null, startedFrom = null, sourceLink = null, stepStates = null)

        val project = entity.toModel()

        assertEquals(CreateMode.STEP_BY_STEP, project.mode)
        assertEquals(null, project.startedFrom)
        assertEquals("", project.sourceLink)
        assertEquals(emptyMap<WizardStep, StepState>(), project.stepStates)
    }
}
