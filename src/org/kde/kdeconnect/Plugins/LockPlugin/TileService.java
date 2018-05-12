package org.kde.kdeconnect.Plugins.LockPlugin;

import android.annotation.TargetApi;
import android.graphics.drawable.Icon;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.service.quicksettings.Tile;

import org.kde.kdeconnect_tp.R;

@TargetApi(Build.VERSION_CODES.N)
public class TileService extends android.service.quicksettings.TileService {

    private LockHelper mLockHelper = null;

    @Override
    public void onClick() {
        super.onClick();
        if (mLockHelper == null)
            mLockHelper = new LockHelper(this);
        if(isLocked()) {
            unlockAndRun(new Runnable() {
                @Override
                public void run() {
                    mLockHelper.unlockComputer();
                }
            });
        } else {
            if (mLockHelper.shouldAuth()) {
                showDialog(mLockHelper.getDialog());
                mLockHelper.pleaseAuthenticate((FingerprintManager)
                        getSystemService(FINGERPRINT_SERVICE));
            }
        }
    }

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_fingerprint_black_24dp));
        tile.setLabel(getString(R.string.label_qs_unlock));
        tile.setContentDescription(getString(R.string.desc_qs_unlock));
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();

    }
}
