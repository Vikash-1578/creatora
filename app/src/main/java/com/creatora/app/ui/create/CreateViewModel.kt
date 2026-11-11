package com.creatora.app.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val projects: ProjectRepository,
) : ViewModel() {

    /** Creates a project in [format] and passes its id to [onCreated] so the wizard can open. */
    fun start(format: VideoFormat, title: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(projects.save(Project(title = title.trim(), format = format)))
        }
    }
}
