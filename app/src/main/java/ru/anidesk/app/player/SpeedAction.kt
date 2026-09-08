package ru.anidesk.app.player

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Action
import ru.anidesk.app.R

class SpeedAction(context: Context) : Action(
    R.id.player_action_speed.toLong(),
    "1x",
    null,
    ContextCompat.getDrawable(context, R.drawable.ic_play_speed),
)