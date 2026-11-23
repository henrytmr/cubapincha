package com.cubapincha.job.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

/** Estado de red y escucha de cambios de conectividad. */
public final class NetworkUtil {
    private NetworkUtil() {}

    public static boolean isOnline(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                return info != null && info.isConnected();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public interface ConnectivityListener {
        void onConnectivityChanged(boolean online);
    }

    public static ConnectivityManager.NetworkCallback registerCallback(Context ctx, ConnectivityListener listener) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;
            ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    // Recalcular el estado real en cada evento: onAvailable
                    // y onLost llegan POR RED. Al perder el WiFi con datos
                    // móviles activos, onLost mentía "sin conexión": el
                    // banner offline se atascaba y el sync se duplicaba.
                    listener.onConnectivityChanged(isOnline(ctx.getApplicationContext()));
                }

                @Override
                public void onLost(Network network) {
                    listener.onConnectivityChanged(isOnline(ctx.getApplicationContext()));
                }
            };
            cm.registerNetworkCallback(new NetworkRequest.Builder().build(), cb);
            return cb;
        } catch (Exception e) {
            return null;
        }
    }

    public static void unregisterCallback(Context ctx, ConnectivityManager.NetworkCallback cb) {
        if (cb == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(cb);
        } catch (Exception ignored) { }
    }
}
