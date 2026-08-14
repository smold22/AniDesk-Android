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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.SessionStore
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
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
        Text(
            text = "Неофициальный клиент Anixart",
            fontSize = 14.sp,
            color = SecondaryText,
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlayerRed,
                    cursorColor = PlayerRed,
                ),
            )
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PlayerRed,
                cursorColor = PlayerRed,
            ),
        )

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
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlayerRed,
                    cursorColor = PlayerRed,
                ),
            )
        }

        if (isRegister && hash != null) {
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
                shape = RoundedCornerShape(12.dp),
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
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
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
        Row {
            TextButton(
                onClick = {
                    isRegister = !isRegister
                    hash = null
                    error = null
                }
            ) {
                Text(
                    text = if (isRegister) "← Ко входу" else "Регистрация",
                    color = SecondaryText,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
