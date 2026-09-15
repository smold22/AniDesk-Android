package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.ui.components.TvKeyRouter
import ru.anidesk.app.ui.components.isTv
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.PlayerRed
import ru.anidesk.app.ui.theme.SecondaryText

@Composable
fun LoginScreen(api: AnixartApi, sessionStore: SessionStore) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var hash by remember { mutableStateOf<String?>(null) }
    var isRegister by remember { mutableStateOf(false) }
    var isRestore by remember { mutableStateOf(false) }
    var restoreHash by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val tv = isTv()
    val loginFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val submitFocusRequester = remember { FocusRequester() }
    var loginFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (tv) {
            delay(150)
            loginFocusRequester.requestFocus()
        }
    }

    if (tv) {
        DisposableEffect(Unit) {
            TvKeyRouter.fieldDpadDown = {
                when {
                    loginFocused -> {
                        passwordFocusRequester.requestFocus()
                        true
                    }
                    passwordFocused -> {
                        submitFocusRequester.requestFocus()
                        true
                    }
                    else -> false
                }
            }
            TvKeyRouter.fieldDpadUp = {
                when {
                    loginFocused -> {
                        submitFocusRequester.requestFocus()
                        true
                    }
                    passwordFocused -> {
                        loginFocusRequester.requestFocus()
                        true
                    }
                    else -> false
                }
            }
            onDispose {
                TvKeyRouter.fieldDpadDown = null
                TvKeyRouter.fieldDpadUp = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AniDesk",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = login,
            onValueChange = {
                login = it
                error = null
            },
            label = { Text("Логин") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (tv) Modifier.focusRequester(loginFocusRequester) else Modifier)
                .onFocusChanged { loginFocused = it.isFocused },
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PlayerRed,
                cursorColor = PlayerRed,
            ),
        )

        if (isRegister && hash == null) {
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    error = null
                },
                label = { Text("Почта") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlayerRed,
                    cursorColor = PlayerRed,
                ),
            )
        }

        Spacer(Modifier.height(14.dp))
        if (!isRestore || restoreHash != null) {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                label = { Text(if (isRestore) "Новый пароль" else "Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (tv) Modifier.focusRequester(passwordFocusRequester) else Modifier)
                    .onFocusChanged { passwordFocused = it.isFocused },
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlayerRed,
                    cursorColor = PlayerRed,
                ),
            )
        }

        if (isRegister && hash == null) {
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    error = null
                },
                label = { Text("Повторите пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlayerRed,
                    cursorColor = PlayerRed,
                ),
            )
        }

        if ((isRegister && hash != null) || (isRestore && restoreHash != null)) {
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    error = null
                },
                label = { Text("Код из письма") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlayerRed,
                    cursorColor = PlayerRed,
                ),
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error!!,
                color = Color(0xFFDD1B1B),
                fontSize = 14.sp,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                error = null
                if (isRestore) {
                    if (restoreHash == null) {
                        if (login.isBlank()) {
                            error = "Введите логин"
                            return@Button
                        }
                        loading = true
                        scope.launch {
                            try {
                                val res = api.restorePassword(login.trim())
                                when (res.code) {
                                    0 -> restoreHash = res.hash.ifBlank { null }
                                    2 -> error = "Профиль не найден"
                                    3 -> error = "Код уже отправлен"
                                    4 -> error = "Не удалось отправить код"
                                    else -> error = "Ошибка восстановления (код ${res.code})"
                                }
                            } catch (e: Exception) {
                                error = "Сеть недоступна: ${e.message}"
                            } finally {
                                loading = false
                            }
                        }
                    } else {
                        if (password.isBlank() || code.isBlank()) {
                            error = "Введите новый пароль и код из письма"
                            return@Button
                        }
                        val currentHash = restoreHash ?: return@Button
                        loading = true
                        scope.launch {
                            try {
                                val res = api.restorePasswordVerify(login.trim(), password, currentHash, code.trim())
                                when (res.code) {
                                    0 -> {
                                        val token = res.profileToken
                                        val profile = res.profile
                                        if (token != null && profile != null) {
                                            sessionStore.save(profile.id, token.token)
                                        } else {
                                            error = "Некорректный ответ сервера"
                                        }
                                    }
                                    2 -> error = "Профиль не найден"
                                    3 -> error = "Некорректный пароль"
                                    4 -> error = "Неверный код"
                                    5 -> error = "Код истёк, начните восстановление заново"
                                    6 -> error = "Неверный хеш, начните восстановление заново"
                                    else -> error = "Ошибка восстановления (код ${res.code})"
                                }
                            } catch (e: Exception) {
                                error = "Сеть недоступна: ${e.message}"
                            } finally {
                                loading = false
                            }
                        }
                    }
                    return@Button
                }
                if (isRegister && hash != null) {
                    if (code.isBlank()) {
                        error = "Введите код из письма"
                        return@Button
                    }
                    val currentHash = hash ?: return@Button
                    loading = true
                    scope.launch {
                        try {
                            val res = api.signUpVerify(login.trim(), email.trim(), password, currentHash, code.trim())
                            when (res.code) {
                                0 -> {
                                    val token = res.profileToken
                                    val profile = res.profile
                                    if (token != null && profile != null) {
                                        sessionStore.save(profile.id, token.token)
                                    } else {
                                        error = "Некорректный ответ сервера"
                                    }
                                }
                                2 -> error = "Некорректный логин"
                                3 -> error = "Некорректная почта"
                                4 -> error = "Некорректный пароль"
                                5 -> error = "Логин уже занят"
                                6 -> error = "Почта уже занята"
                                7 -> error = "Неверный код"
                                8 -> error = "Код истёк, зарегистрируйтесь заново"
                                9 -> error = "Неверный хеш подтверждения"
                                10 -> error = "Почтовый сервис недоступен"
                                11 -> error = "Слишком много регистраций"
                                else -> error = "Ошибка регистрации (код ${res.code})"
                            }
                        } catch (e: Exception) {
                            error = "Сеть недоступна: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                } else if (isRegister) {
                    if (login.isBlank() || email.isBlank() || password.isBlank()) {
                        error = "Заполните логин, почту и пароль"
                        return@Button
                    }
                    if (password != confirmPassword) {
                        error = "Пароли не совпадают"
                        return@Button
                    }
                    loading = true
                    scope.launch {
                        try {
                            val res = api.signUp(login.trim(), email.trim(), password)
                            when (res.code) {
                                0 -> hash = res.hash.ifBlank { null }
                                2 -> error = "Некорректный логин"
                                3 -> error = "Некорректная почта"
                                4 -> error = "Некорректный пароль"
                                5 -> error = "Логин уже занят"
                                6 -> error = "Почта уже занята"
                                7 -> error = "Код уже отправлен"
                                8 -> error = "Не удалось отправить код"
                                9 -> error = "Почтовый сервис недоступен"
                                10 -> error = "Слишком много регистраций"
                                else -> error = "Ошибка регистрации (код ${res.code})"
                            }
                        } catch (e: Exception) {
                            error = "Сеть недоступна: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                } else {
                    if (login.isBlank() || password.isBlank()) {
                        error = "Введите логин и пароль"
                        return@Button
                    }
                    loading = true
                    scope.launch {
                        try {
                            val res = api.signIn(login.trim(), password)
                            when (res.code) {
                                0 -> {
                                    val token = res.profileToken
                                    val profile = res.profile
                                    if (token != null && profile != null) {
                                        sessionStore.save(profile.id, token.token)
                                    } else {
                                        error = "Некорректный ответ сервера"
                                    }
                                }
                                2 -> error = "Неверный логин"
                                3 -> error = "Неверный пароль"
                                else -> error = "Ошибка авторизации (код ${res.code})"
                            }
                        } catch (e: Exception) {
                            error = "Сеть недоступна: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .then(if (tv) Modifier.focusRequester(submitFocusRequester) else Modifier),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PlayerRed),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = when {
                        isRestore -> "Восстановить"
                        isRegister && hash != null -> "Подтвердить"
                        isRegister -> "Зарегистрироваться"
                        else -> "Войти"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        if (isRegister || isRestore) {
            TextButton(
                onClick = {
                    isRegister = false
                    isRestore = false
                    hash = null
                    restoreHash = null
                    code = ""
                    password = ""
                    error = null
                }
            ) {
                Text(
                    text = "← Ко входу",
                    color = SecondaryText,
                    fontSize = 14.sp,
                )
            }
        } else {
            Row {
                TextButton(
                    onClick = {
                        isRegister = true
                        error = null
                    }
                ) {
                    Text(
                        text = "Регистрация",
                        color = SecondaryText,
                        fontSize = 14.sp,
                    )
                }
                TextButton(
                    onClick = {
                        isRestore = true
                        error = null
                    }
                ) {
                    Text(
                        text = "Забыли пароль?",
                        color = SecondaryText,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
