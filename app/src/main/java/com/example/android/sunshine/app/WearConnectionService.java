package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

public class WearConnectionService extends WearableListenerService {
  private static final String TAG = WearConnectionService.class.getSimpleName();

  public void onPeerConnected(Node peer) {
    super.onPeerConnected(peer);
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "onPeerConnected: " + peer.getDisplayName());
    }
    BusProvider.getInstance().post(new SendDataToWearEvent());
  }
}
