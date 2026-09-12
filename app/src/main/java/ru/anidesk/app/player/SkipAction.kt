package ru.anidesk.app.player

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Action
import ru.anidesk.app.R

class SkipAction(context: Context) : Action(
    R.id.player_action_skip.toLong(),
    "Пропуск",
    null,
    ContextCompat.getDrawable(context, R.drawable.ic_skip_forward),
)
