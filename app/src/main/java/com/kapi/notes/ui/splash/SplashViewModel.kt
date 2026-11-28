package com.kapi.notes.ui.splash

import androidx.lifecycle.ViewModel
import com.kapi.notes.data.repository.ApiAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val apiAuthRepository: ApiAuthRepository
) : ViewModel() {
    suspend fun checkConnectivity() {
        apiAuthRepository.checkConnectivity()
    }
}
