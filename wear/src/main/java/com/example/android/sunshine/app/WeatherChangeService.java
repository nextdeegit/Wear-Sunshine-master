package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WeatherChangeService extends WearableListenerService {
  private static final String TAG = WeatherChangeService.class.getSimpleName();

  private GoogleApiClient mGoogleApiClient;

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "onCreate");
  }

  @Override
  public void onPeerConnected(Node peer) {
    super.onPeerConnected(peer);
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "onPeerConnected: " + peer.getDisplayName());
    }
  }

  @Override
  public void onPeerDisconnected(Node peer) {
    super.onPeerDisconnected(peer);
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "onPeerDisconnected: " + peer.getDisplayName());
    }
  }

  @Override
  public void onMessageReceived(MessageEvent messageEvent) {
    super.onMessageReceived(messageEvent);
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "onMessageReceived: " + messageEvent.getPath());
    }
  }

  @Override
  public void onDataChanged(DataEventBuffer dataEvents) {
    super.onDataChanged(dataEvents);
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "onDataChanged: " + dataEvents);
    }

    for (DataEvent event : dataEvents) {
      if (event.getType() == DataEvent.TYPE_CHANGED) {
        // DataItem changed
        DataItem item = event.getDataItem();
        if (item.getUri().getPath().compareTo("/data") == 0) {
          DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
          String high = dataMap.getString("high");
          String low = dataMap.getString("low");
          MyWatchFace.sHighTemp = high;
          MyWatchFace.sLowTemp = low;

//          SharedPreferences sharedPreferences = getApplicationContext()
//              .getSharedPreferences(WeatherChangeService.class.getSimpleName(), MODE_PRIVATE);
//          sharedPreferences.edit().putString("high", high)
//              .putString("low", low).apply();

          Asset profileAsset = dataMap.getAsset("icon");
          if (profileAsset == null) {
            Log.d(TAG, "asset was null");
          } else {
            Log.d(TAG, "asset not null");
          }
          MyWatchFace.sWeatherIcon = loadBitmapFromAsset(profileAsset);
          if (MyWatchFace.sWeatherIcon == null) {
            Log.d(TAG, "weatherIcon null");
          } else {
            Log.d(TAG, "weatherIcon null");
          }
        }
      } else if (event.getType() == DataEvent.TYPE_DELETED) {
        // DataItem deleted
      }
    }
  }

  public Bitmap loadBitmapFromAsset(Asset asset) {
    createApiCliene();

    if (asset == null) {
      throw new IllegalArgumentException("Asset must be non-null");
    }
    ConnectionResult result =
        mGoogleApiClient.blockingConnect(3000, TimeUnit.MILLISECONDS);
    if (!result.isSuccess()) {
      return null;
    }
    // convert asset into a file descriptor and block until it's ready
    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
        mGoogleApiClient, asset).await().getInputStream();
    mGoogleApiClient.disconnect();

    if (assetInputStream == null) {
      Log.w(TAG, "Requested an unknown Asset.");
      return null;
    }

    // decode the stream into a bitmap
    return BitmapFactory.decodeStream(assetInputStream);
  }

  private void createApiCliene() {
    if (mGoogleApiClient != null) {
      return;
    }

    mGoogleApiClient = new GoogleApiClient.Builder(getBaseContext())
        .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
          @Override
          public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected: " + connectionHint);
          }

          @Override
          public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
          }
        })
        .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
          @Override
          public void onConnectionFailed(ConnectionResult result) {
            Log.d(TAG, "onConnectionFailed: " + result);
          }
        })
            // Request access only to the Wearable API
        .addApiIfAvailable(Wearable.API)
        .build();
  }

}
