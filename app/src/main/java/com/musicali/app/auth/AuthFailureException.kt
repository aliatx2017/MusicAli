package com.musicali.app.auth

import java.io.IOException

/**
 * Thrown by AuthInterceptor when getValidToken() fails for any auth reason
 * (no refresh token, invalid_grant, AppAuth error, etc.).
 *
 * Extends IOException so OkHttp accepts it, but is a distinct subtype so
 * GeneratePlaylistUseCase can catch it before the generic IOException handler
 * and emit GenerationError.AuthExpired instead of GenerationError.NetworkError.
 */
class AuthFailureException(message: String, cause: Throwable? = null) : IOException(message, cause)
