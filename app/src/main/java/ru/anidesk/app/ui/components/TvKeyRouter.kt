package ru.anidesk.app.ui.components

/**
 * Перехват DPAD-клавиш на уровне Activity для экранов с текстовыми полями.
 * Когда текстовое поле сфокусировано, IME/поле перехватывает DPAD для движения
 * курсора, и событие не доходит до Compose. Activity видит все события, поэтому
 * экран регистрирует здесь свои обработчики.
 */
object TvKeyRouter {

    @Volatile
    var fieldDpadDown: (() -> Boolean)? = null

    @Volatile
    var fieldDpadUp: (() -> Boolean)? = null

    /** Ставится при первом нажатии любой клавиши — защита от повторного перехвата фокуса. */
    @Volatile
    var userInteracted: Boolean = false
}