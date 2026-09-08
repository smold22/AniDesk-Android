package ru.anidesk.app.player

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Action
import ru.anidesk.app.R

class DubbersAction(context: Context) : Action(
    R.id.player_action_dubbers.toLong(),
    "Озвучка",
    null,
    ContextCompat.getDrawable(context, R.drawable.ic_dubber),
)