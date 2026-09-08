package ru.anidesk.app.player

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Action
import ru.anidesk.app.R

class EpisodesAction(context: Context) : Action(
    R.id.player_action_episodes.toLong(),
    "Серии",
    null,
    ContextCompat.getDrawable(context, R.drawable.ic_playlist_play_black_24dp),
)