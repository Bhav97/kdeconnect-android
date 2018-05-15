package org.kde.kdeconnect.Plugins.LockPlugin;

import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;


public class LockPlugin extends Plugin {

    public final static String PACKET_TYPE_LOCK = "kdeconnect.lock";
    private final static String TAG = LockPlugin.class.getSimpleName();

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_lock);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_lock_desc);
    }

    public void unlockDevice() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_LOCK);
        device.sendPacket(np);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        return false;
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.lock);
    }

    /* Should it be available via UI?
    @Override
    public void startMainActivity(Activity activity) {
        unlockDevice();
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public boolean displayInContextMenu() {
        return false;
    }
    */

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_LOCK};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_LOCK};
    }

}
