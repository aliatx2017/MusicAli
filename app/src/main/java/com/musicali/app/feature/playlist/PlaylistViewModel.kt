package com.musicali.app.feature.playlist

import androidx.lifecycle.ViewModel
import com.musicali.app.domain.repository.ArtistHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val artistHistoryRepository: ArtistHistoryRepository
) : ViewModel()
