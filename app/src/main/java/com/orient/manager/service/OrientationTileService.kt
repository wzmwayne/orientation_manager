package com.orient.manager.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.orient.manager.R
import com.orient.manager.core.OrientationController
import com.orient.manager.core.OrientationMode
import com.orient.manager.pref.Prefs

class OrientationTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val target = if (OrientationController.isAutoRotate(this)) {
            OrientationMode.PORTRAIT
        } else {
            OrientationMode.AUTO
        }
        OrientationController.apply(this, target)
        Prefs(this).mode = target
        refresh()
    }

    private fun refresh() {
        val auto = OrientationController.isAutoRotate(this)
        qsTile?.apply {
            state = if (auto) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            updateTile()
        }
    }
}
