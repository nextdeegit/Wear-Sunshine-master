package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
  private static final Typeface NORMAL_TYPEFACE =
      Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

  private static final String TAG = MyWatchFace.class.getSimpleName();

  /**
   * Update rate in milliseconds for interactive mode. We update once a second since seconds are
   * displayed in interactive mode.
   */
  private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

  /**
   * Handler message id for updating the time periodically in interactive mode.
   */
  private static final int MSG_UPDATE_TIME = 0;

  private GoogleApiClient mGoogleApiClient;

  static String sHighTemp = null;

  static String sLowTemp = null;

  static Bitmap sWeatherIcon = null;

  @Override
  public Engine onCreateEngine() {
    return new Engine();
  }

  private class Engine extends CanvasWatchFaceService.Engine {
    final Handler mUpdateTimeHandler = new EngineHandler(this);

    final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        mTime.clear(intent.getStringExtra("time-zone"));
        mTime.setToNow();
      }
    };

    boolean mRegisteredTimeZoneReceiver = false;

    Paint mBackgroundPaint;

    Paint mTimeTextPaint;
    Paint mDateTextPaint;

    boolean mAmbient;

    Time mTime;

    float mXOffset;

    float mYOffset;

    /**
     * Whether the display supports fewer bits for each color in ambient mode. When true, we
     * disable anti-aliasing in ambient mode.
     */
    boolean mLowBitAmbient;

    @Override
    public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);

      SharedPreferences sharedPreferences = getApplicationContext()
          .getSharedPreferences(WeatherChangeService.class.getSimpleName(), MODE_PRIVATE);
      sHighTemp = sharedPreferences.getString("high", "");
      sLowTemp = sharedPreferences.getString("low", "");

      mGoogleApiClient = new GoogleApiClient.Builder(getBaseContext())
          .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle connectionHint) {
              Log.d(TAG, "onConnected: " + connectionHint);

              Log.d(TAG, "getting previous data items");
              PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(mGoogleApiClient);
              results.setResultCallback(new ResultCallback<DataItemBuffer>() {
                @Override
                public void onResult(DataItemBuffer dataItems) {
                  Log.d(TAG, "got previous data items");
                  if (dataItems.getCount() != 0) {
                    Log.d(TAG, "there were items");
                    for (int i = 0; i < dataItems.getCount(); i++) {
                      DataItem item = dataItems.get(i);
                      if (item.getUri().getPath().compareTo("/data") == 0) {
                        Log.d(TAG, "found the previous data");
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        String high = dataMap.getString("high");
                        String low = dataMap.getString("low");
                        MyWatchFace.sHighTemp = high;
                        MyWatchFace.sLowTemp = low;

                        Log.d(TAG, "saved high: " + high);
                        Log.d(TAG, "saved low: " + low);

                        final Asset profileAsset = dataMap.getAsset("icon");
                        if (profileAsset == null) {
                          Log.d(TAG, "asset was null");
                        } else {
                          Log.d(TAG, "asset not null");
                        }

                        new Thread(new Runnable() {
                          @Override
                          public void run() {
                            MyWatchFace.sWeatherIcon = loadBitmapFromAsset(profileAsset);
                            if (MyWatchFace.sWeatherIcon == null) {
                              Log.d(TAG, "weatherIcon null");
                            } else {
                              Log.d(TAG, "weatherIcon null");
                            }
                          }
                        }).start();
                      }
                    }
                  }

                  dataItems.release();
                }
              });
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
      mGoogleApiClient.connect();

      setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
          .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
          .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
          .setShowSystemUiTime(false)
          .build());
      Resources resources = MyWatchFace.this.getResources();

      mBackgroundPaint = new Paint();
      mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

      mTimeTextPaint = new Paint();
      mTimeTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

      mDateTextPaint = new Paint();
      mDateTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

      mTime = new Time();
    }

    @Override
    public void onDestroy() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      super.onDestroy();
    }

    private Paint createTextPaint(int textColor) {
      Paint paint = new Paint();
      paint.setColor(textColor);
      paint.setTypeface(NORMAL_TYPEFACE);
      paint.setAntiAlias(true);
      return paint;
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);

      if (visible) {
        registerReceiver();

        // Update time zone in case it changed while we weren't visible.
        mTime.clear(TimeZone.getDefault().getID());
        mTime.setToNow();
      } else {
        unregisterReceiver();
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer();
    }

    private void registerReceiver() {
      if (mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = true;
      IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
      MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
    }

    private void unregisterReceiver() {
      if (!mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = false;
      MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
    }

    @Override
    public void onApplyWindowInsets(WindowInsets insets) {
      super.onApplyWindowInsets(insets);

      // Load resources that have alternate values for round watches.
      Resources resources = MyWatchFace.this.getResources();
      boolean isRound = insets.isRound();
      mXOffset = resources.getDimension(isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
      mYOffset = resources.getDimension(isRound ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset_square);

      float textSize = resources.getDimension(isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
      mTimeTextPaint.setTextSize(textSize);

      float dateTextSize = resources.getDimension(R.dimen.date_text_size);
      mDateTextPaint.setTextSize(dateTextSize);
    }

    @Override
    public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);
      mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
    }

    @Override
    public void onTimeTick() {
      super.onTimeTick();
      invalidate();
    }

    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);
      if (mAmbient != inAmbientMode) {
        mAmbient = inAmbientMode;
        if (mLowBitAmbient) {
          mDateTextPaint.setAntiAlias(!inAmbientMode);
        }
        invalidate();
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer();
    }

    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
      // Draw the background.
      canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

      // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
      mTime.setToNow();
      String text = mAmbient
          ? String.format("%d:%02d", mTime.hour, mTime.minute)
          : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);

      float timeTextWidth = mTimeTextPaint.measureText(text);
      int timeX = (int) (canvas.getWidth()/2 - timeTextWidth/2);

      canvas.drawText(text, timeX, mYOffset, mTimeTextPaint);

      if (!mAmbient) {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("E, LLL d yyyy");
        String date = simpleDateFormat.format(new Date(System.currentTimeMillis()));
        float dateTextWidth = mDateTextPaint.measureText(date);
        int dateX = (int) (canvas.getWidth()/2 - dateTextWidth/2);
        canvas.drawText(date, dateX, mYOffset + dipToPixels(16), mDateTextPaint);

        String highLow = sHighTemp + " / " + sLowTemp;
        float tempTextWidth = mDateTextPaint.measureText(highLow);
        int tempX = (int) (canvas.getWidth()/2 - tempTextWidth/2);
        canvas.drawText(highLow, tempX, mYOffset + dipToPixels(32), mDateTextPaint);

        if (sWeatherIcon != null) {
          canvas.drawBitmap(
              sWeatherIcon,
              canvas.getWidth() / 2 - sWeatherIcon.getWidth() / 2,
              mYOffset + dipToPixels(34),
              mDateTextPaint);
        }
      }
    }

    private float dipToPixels(float dipValue) {
      DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
      return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }

    /**
     * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
     * or stops it if it shouldn't be running but currently is.
     */
    private void updateTimer() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      if (shouldTimerBeRunning()) {
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
      }
    }

    /**
     * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
     * only run when we're visible and in interactive mode.
     */
    private boolean shouldTimerBeRunning() {
      return isVisible() && !isInAmbientMode();
    }

    /**
     * Handle updating the time periodically in interactive mode.
     */
    private void handleUpdateTimeMessage() {
      invalidate();
      if (shouldTimerBeRunning()) {
        long timeMs = System.currentTimeMillis();
        long delayMs = INTERACTIVE_UPDATE_RATE_MS
            - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
        mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
      }
    }
  }

  private static class EngineHandler extends Handler {
    private final WeakReference<MyWatchFace.Engine> mWeakReference;

    public EngineHandler(MyWatchFace.Engine reference) {
      mWeakReference = new WeakReference<>(reference);
    }

    @Override
    public void handleMessage(Message msg) {
      MyWatchFace.Engine engine = mWeakReference.get();
      if (engine != null) {
        switch (msg.what) {
          case MSG_UPDATE_TIME:
            engine.handleUpdateTimeMessage();
            break;
        }
      }
    }
  }

  public Bitmap loadBitmapFromAsset(Asset asset) {

    if (asset == null) {
      throw new IllegalArgumentException("Asset must be non-null");
    }

    // convert asset into a file descriptor and block until it's ready
    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
        mGoogleApiClient, asset).await().getInputStream();

    if (assetInputStream == null) {
      Log.w(TAG, "Requested an unknown Asset.");
      return null;
    }

    // decode the stream into a bitmap
    return BitmapFactory.decodeStream(assetInputStream);
  }
}
