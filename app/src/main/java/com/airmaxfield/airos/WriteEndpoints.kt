package com.airmaxfield.airos

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Адреса записи конфигурации. НЕ ПРОВЕРЕНЫ на живых пушках — угаданы по веб-интерфейсу
 * (план этапа 4, таблица «Неподтверждённые эндпоинты»). Правка — только здесь.
 */
object WriteEndpoints {
    /** Загрузка system.cfg (airOS 6 и 8): multipart, поле [UPLOAD_FIELD]. */
    const val UPLOAD = "/system.cgi"
    const val UPLOAD_FIELD = "cfgfile"

    /** Тест-режим / подтверждение. */
    const val APPLY = "/apply.cgi"

    /** Откат = перезагрузка: тестовая конфигурация не сохранена во флеш. */
    const val REBOOT = "/reboot.cgi"

    /** У airOS 6 таймер теста фиксирован (≈180 с). */
    const val V6_TEST_SECONDS = 180

    fun uploadBody(cfg: String): MultipartBody = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart(UPLOAD_FIELD, "system.cfg", cfg.toRequestBody("application/octet-stream".toMediaType()))
        .build()
}
