package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.ui.components.TvKeyRouter
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.PlayerRed
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.ThirdText

private enum class AuthStep { Main, Login, Register, Code }

private enum class Field { None, Login, Email, Password, Code }

@Composable
fun AccountScreen(
    api: AnixartApi,
    sessionStore: SessionStore,
    onLoggedIn: () -> Unit,
    onSkip: () -> Unit,
) {
    var step by remember { mutableStateOf(AuthStep.Main) }
    var login by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var hash by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var codeError by remember { mutableStateOf<String?>(null) }
    var focusedField by remember { mutableStateOf(Field.None) }

    val scope = rememberCoroutineScope()

    val loginFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }
    val firstOptionFocus = remember { FocusRequester() }

    val loginValid = login.isNotBlank()
    val emailValid = email.contains("@") && email.contains(".")
    val passwordValid = password.length >= 6
    val codeValid = code.isNotBlank()

    fun resetErrors() {
        serverError = null
        loginError = null
        emailError = null
        passwordError = null
        codeError = null
    }

    fun goTo(next: AuthStep) {
        hash = null
        resetErrors()
        step = next
    }

    DisposableEffect(step) {
        when (step) {
            AuthStep.Main -> {
                TvKeyRouter.fieldDpadDown = null
                TvKeyRouter.fieldDpadUp = null
            }
            AuthStep.Login -> {
                TvKeyRouter.fieldDpadDown = {
                    when (focusedField) {
                        Field.Login -> {
                            if (!login.isNotBlank()) {
                                loginError = "Поле не может быть пустым"
                                true
                            } else {
                                passwordFocus.requestFocus()
                                true
                            }
                        }
                        Field.Password -> {
                            if (!password.isNotBlank()) {
                                passwordError = "Поле не может быть пустым"
                                true
                            } else {
                                submitFocus.requestFocus()
                                true
                            }
                        }
                        else -> false
                    }
                }
                TvKeyRouter.fieldDpadUp = {
                    when (focusedField) {
                        Field.Login -> {
                            if (!login.isNotBlank()) {
                                loginError = "Поле не может быть пустым"
                                true
                            } else {
                                submitFocus.requestFocus()
                                true
                            }
                        }
                        Field.Password -> {
                            if (!password.isNotBlank()) {
                                passwordError = "Поле не может быть пустым"
                                true
                            } else {
                                loginFocus.requestFocus()
                                true
                            }
                        }
                        else -> false
                    }
                }
            }
            AuthStep.Register -> {
                TvKeyRouter.fieldDpadDown = {
                    when (focusedField) {
                        Field.Login -> {
                            if (!login.isNotBlank()) {
                                loginError = "Логин не может быть пустым"
                                true
                            } else {
                                emailFocus.requestFocus()
                                true
                            }
                        }
                        Field.Email -> {
                            if (!email.contains("@") || !email.contains(".")) {
                                emailError = "Введите корректный email"
                                true
                            } else {
                                passwordFocus.requestFocus()
                                true
                            }
                        }
                        Field.Password -> {
                            if (password.length < 6) {
                                passwordError = "Пароль должен быть не короче 6 символов"
                                true
                            } else {
                                submitFocus.requestFocus()
                                true
                            }
                        }
                        else -> false
                    }
                }
                TvKeyRouter.fieldDpadUp = {
                    when (focusedField) {
                        Field.Login -> {
                            if (!login.isNotBlank()) {
                                loginError = "Логин не может быть пустым"
                                true
                            } else {
                                submitFocus.requestFocus()
                                true
                            }
                        }
                        Field.Email -> {
                            if (!email.contains("@") || !email.contains(".")) {
                                emailError = "Введите корректный email"
                                true
                            } else {
                                loginFocus.requestFocus()
                                true
                            }
                        }
                        Field.Password -> {
                            if (password.length < 6) {
                                passwordError = "Пароль должен быть не короче 6 символов"
                                true
                            } else {
                                emailFocus.requestFocus()
                                true
                            }
                        }
                        else -> false
                    }
                }
            }
            AuthStep.Code -> {
                TvKeyRouter.fieldDpadDown = {
                    if (focusedField == Field.Code) {
                        if (!code.isNotBlank()) {
                            codeError = "Код не может быть пустым"
                            true
                        } else {
                            submitFocus.requestFocus()
                            true
                        }
                    } else {
                        false
                    }
                }
                TvKeyRouter.fieldDpadUp = TvKeyRouter.fieldDpadDown
            }
        }
        onDispose {
            TvKeyRouter.fieldDpadDown = null
            TvKeyRouter.fieldDpadUp = null
        }
    }

    LaunchedEffect(step) {
        delay(150)
        when (step) {
            AuthStep.Main -> firstOptionFocus.requestFocus()
            AuthStep.Login -> loginFocus.requestFocus()
            AuthStep.Register -> loginFocus.requestFocus()
            AuthStep.Code -> codeFocus.requestFocus()
        }
    }

    fun submitLogin() {
        serverError = null
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
                            onLoggedIn()
                        } else {
                            serverError = "Некорректный ответ сервера"
                        }
                    }
                    2 -> serverError = "Неверный логин"
                    3 -> serverError = "Неверный пароль"
                    else -> serverError = "Ошибка авторизации (код ${res.code})"
                }
            } catch (e: Exception) {
                serverError = "Сеть недоступна: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun submitRegister() {
        serverError = null
        loading = true
        scope.launch {
            try {
                val res = api.signUp(login.trim(), email.trim(), password)
                when (res.code) {
                    0 -> {
                        hash = res.hash.ifBlank { null }
                        if (hash == null) {
                            onSkip()
                        } else {
                            resetErrors()
                            step = AuthStep.Code
                        }
                    }
                    2 -> serverError = "Некорректный логин"
                    3 -> serverError = "Некорректная почта"
                    4 -> serverError = "Некорректный пароль"
                    5 -> serverError = "Логин уже занят"
                    6 -> serverError = "Почта уже занята"
                    7 -> serverError = "Код уже отправлен"
                    8 -> serverError = "Не удалось отправить код"
                    9 -> serverError = "Почтовый сервис недоступен"
                    10 -> serverError = "Слишком много регистраций"
                    else -> serverError = "Ошибка регистрации (код ${res.code})"
                }
            } catch (e: Exception) {
                serverError = "Сеть недоступна: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun submitVerify() {
        serverError = null
        loading = true
        scope.launch {
            try {
                val currentHash = hash ?: return@launch
                val res = api.signUpVerify(login.trim(), email.trim(), password, currentHash, code.trim())
                when (res.code) {
                    0 -> {
                        val token = res.profileToken
                        val profile = res.profile
                        if (token != null && profile != null) {
                            sessionStore.save(profile.id, token.token)
                            onLoggedIn()
                        } else {
                            serverError = "Некорректный ответ сервера"
                        }
                    }
                    2 -> serverError = "Некорректный логин"
                    3 -> serverError = "Некорректная почта"
                    4 -> serverError = "Некорректный пароль"
                    5 -> serverError = "Логин уже занят"
                    6 -> serverError = "Почта уже занята"
                    7 -> serverError = "Неверный код"
                    8 -> serverError = "Код истёк, зарегистрируйтесь заново"
                    9 -> serverError = "Неверный хеш подтверждения"
                    10 -> serverError = "Почтовый сервис недоступен"
                    11 -> serverError = "Слишком много регистраций"
                    else -> serverError = "Ошибка регистрации (код ${res.code})"
                }
            } catch (e: Exception) {
                serverError = "Сеть недоступна: ${e.message}"
            } finally {
                loading = false
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
        Box(Modifier.width(440.dp)) {
            when (step) {
                AuthStep.Main -> MainStep(
                    firstOptionFocus = firstOptionFocus,
                    onLogin = { goTo(AuthStep.Login) },
                    onRegister = { goTo(AuthStep.Register) },
                    onSkip = {
                        scope.launch {
                            sessionStore.setAuthSkipped(true)
                            onSkip()
                        }
                    },
                )
                AuthStep.Login -> FormStep(
                    title = "Вход",
                    description = "Введите логин и пароль от вашего аккаунта Anixart.",
                    submitTitle = "Войти",
                    enabled = loginValid && passwordValid && !loading,
                    loading = loading,
                    serverError = serverError,
                    submitFocus = submitFocus,
                    onSubmit = ::submitLogin,
                    onBack = { goTo(AuthStep.Main) },
                ) {
                    AuthField(
                        value = login,
                        onValueChange = {
                            login = it
                            loginError = null
                            serverError = null
                        },
                        label = "Логин или email",
                        error = loginError,
                        keyboardType = KeyboardType.Text,
                        focusRequester = loginFocus,
                        onFocusChanged = { focusedField = if (it.isFocused) Field.Login else Field.None },
                    )
                    AuthField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                            serverError = null
                        },
                        label = "Пароль",
                        error = passwordError,
                        keyboardType = KeyboardType.Password,
                        password = true,
                        focusRequester = passwordFocus,
                        onFocusChanged = { focusedField = if (it.isFocused) Field.Password else Field.None },
                    )
                }
                AuthStep.Register -> FormStep(
                    title = "Регистрация",
                    description = "Заполните данные для создания аккаунта Anixart.",
                    submitTitle = "Зарегистрироваться",
                    enabled = loginValid && emailValid && passwordValid && !loading,
                    loading = loading,
                    serverError = serverError,
                    submitFocus = submitFocus,
                    onSubmit = ::submitRegister,
                    onBack = { goTo(AuthStep.Main) },
                ) {
                    AuthField(
                        value = login,
                        onValueChange = {
                            login = it
                            loginError = null
                            serverError = null
                        },
                        label = "Логин",
                        error = loginError,
                        keyboardType = KeyboardType.Text,
                        focusRequester = loginFocus,
                        onFocusChanged = { focusedField = if (it.isFocused) Field.Login else Field.None },
                    )
                    AuthField(
                        value = email,
                        onValueChange = {
                            email = it
                            emailError = null
                            serverError = null
                        },
                        label = "Email",
                        error = emailError,
                        keyboardType = KeyboardType.Email,
                        focusRequester = emailFocus,
                        onFocusChanged = { focusedField = if (it.isFocused) Field.Email else Field.None },
                    )
                    AuthField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                            serverError = null
                        },
                        label = "Пароль",
                        error = passwordError,
                        keyboardType = KeyboardType.Password,
                        password = true,
                        focusRequester = passwordFocus,
                        onFocusChanged = { focusedField = if (it.isFocused) Field.Password else Field.None },
                    )
                }
                AuthStep.Code -> FormStep(
                    title = "Подтверждение регистрации",
                    description = "На вашу почту отправлен код подтверждения. Введите его ниже.",
                    submitTitle = "Подтвердить",
                    enabled = codeValid && !loading,
                    loading = loading,
                    serverError = serverError,
                    submitFocus = submitFocus,
                    onSubmit = ::submitVerify,
                    onBack = { goTo(AuthStep.Main) },
                ) {
                    AuthField(
                        value = code,
                        onValueChange = {
                            code = it
                            codeError = null
                            serverError = null
                        },
                        label = "Код подтверждения",
                        error = codeError,
                        keyboardType = KeyboardType.Number,
                        focusRequester = codeFocus,
                        onFocusChanged = { focusedField = if (it.isFocused) Field.Code else Field.None },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainStep(
    firstOptionFocus: FocusRequester,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Авторизация",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MainText,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Войдите в свой аккаунт Anixart или зарегистрируйтесь.\nДля просмотра можно пропустить этот шаг.",
            fontSize = 15.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(34.dp))
        ChoiceButton(
            title = "Вход",
            description = "Войти в существующий аккаунт",
            focusRequester = firstOptionFocus,
            onClick = onLogin,
        )
        Spacer(Modifier.height(12.dp))
        ChoiceButton(
            title = "Регистрация",
            description = "Создать новый аккаунт",
            onClick = onRegister,
        )
        Spacer(Modifier.height(12.dp))
        ChoiceButton(
            title = "Пропустить",
            description = null,
            onClick = onSkip,
        )
    }
}

@Composable
private fun ChoiceButton(
    title: String,
    description: String?,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) PlayerRed.copy(alpha = 0.15f) else AltBackground)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickableDpad(onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MainText,
        )
        if (description != null) {
            Text(
                text = description,
                fontSize = 13.sp,
                color = ThirdText,
            )
        }
    }
}

private fun Modifier.clickableDpad(onClick: () -> Unit): Modifier =
    this.then(clickable(onClick = onClick))

@Composable
private fun FormStep(
    title: String,
    description: String,
    submitTitle: String,
    enabled: Boolean,
    loading: Boolean,
    serverError: String?,
    submitFocus: FocusRequester,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MainText,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = fields,
        )
        if (serverError != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = serverError,
                color = Color(0xFFDD1B1B),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .focusRequester(submitFocus),
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
                    text = submitTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text(
                text = "← Ко входу",
                color = SecondaryText,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType,
    focusRequester: FocusRequester,
    onFocusChanged: (androidx.compose.ui.focus.FocusState) -> Unit,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged(onFocusChanged),
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PlayerRed,
            cursorColor = PlayerRed,
            errorBorderColor = Color(0xFFDD1B1B),
            errorSupportingTextColor = Color(0xFFDD1B1B),
        ),
    )
}