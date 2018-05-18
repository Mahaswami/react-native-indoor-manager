
package com.mahaswami.reactlibrary;

import android.os.Bundle;
import android.support.annotation.Nullable;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;
import com.indooratlas.android.sdk.resources.IAFloorPlan;
import com.indooratlas.android.sdk.resources.IAResourceManager;
import com.indooratlas.android.sdk.resources.IAResult;
import com.indooratlas.android.sdk.resources.IAResultCallback;
import com.indooratlas.android.sdk.resources.IATask;
import com.indooratlas.android.sdk.IARegion;
import com.indooratlas.android.sdk.resources.IALatLng;
import android.os.Looper;

public class RNIndoorManagerModule extends ReactContextBaseJavaModule {

    private IALocationManager locationManager;
    private IAResourceManager mResourceManager;
    private IALatLng mCenter;
    private IALatLng mBottomLeft;
    private IALatLng mTopRight;
    private IALatLng mTopLeft;
    private String floorPlanId;
  public RNIndoorManagerModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable WritableMap params) {
    reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
  }

  @ReactMethod
  public void initService(String apiKeyId, String apiKeySecret) {
    getCurrentActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        locationManager = IALocationManager.create(getReactApplicationContext());
        mResourceManager = IAResourceManager.create(getReactApplicationContext());
        locationManager.registerRegionListener(new IARegion.Listener() {

          @Override
          public void onEnterRegion(IARegion iaRegion) {
            String id = iaRegion.getId();
            WritableMap params = Arguments.createMap();
            params.putString("id", id);
            params.putString("message", "Enter " + (iaRegion.getType() == IARegion.TYPE_VENUE
                                                                                    ? "VENUE "
                                                                                    : "FLOOR_PLAN ") + iaRegion.getName());
            sendEvent(getReactApplicationContext(), "enterRegion", params);
             if (iaRegion.getType() == IARegion.TYPE_FLOOR_PLAN) {
                final String newId = iaRegion.getId();

                // Are we entering a new floor plan or coming back the floor plan we just left?
                if(newId != null && floorPlanId != newId)
                    fetchFloorPlan(newId);

                floorPlanId = newId;
             }
          }

          @Override
          public void onExitRegion(IARegion iaRegion) {
            String id = iaRegion.getId();
            WritableMap params = Arguments.createMap();
            params.putString("id", id);
            params.putString("message", "Exit " + (iaRegion.getType() == IARegion.TYPE_VENUE
                                                                        ? "VENUE "
                                                                        : "FLOOR_PLAN ") + iaRegion.getName());
            if(iaRegion.getType() == IARegion.TYPE_FLOOR_PLAN && floorPlanId == iaRegion.getId()){
                floorPlanId = "";
            }

            sendEvent(getReactApplicationContext(), "exitRegion", params);
          }
        });

        locationManager.requestLocationUpdates(IALocationRequest.create(), new IALocationListener() {
          @Override
          public void onLocationChanged(IALocation location) {
            WritableMap params = Arguments.createMap();
            try{
                params.putInt("floor", location.getFloorLevel());
                params.putDouble("lat", location.getLatitude());
                params.putDouble("lng", location.getLongitude());
                if(locationManager.getExtraInfo() != null)
                    params.putString("traceId", locationManager.getExtraInfo().traceId);
                if(location.getRegion() != null)
                    params.putString("atlasId", location.getRegion().getId());
                sendEvent(getReactApplicationContext(), "locationChanged", params);
            } catch(Exception e) {
                e.printStackTrace();
            }
          }

          @Override
          public void onStatusChanged(String s, int i, Bundle bundle) {
            WritableMap params = Arguments.createMap();
            params.putInt("status", i);
            sendEvent(getReactApplicationContext(), "providerStatusChange", params);
          }
        });
      }
    });

  }

  @Override
  public String getName() {
    return "RNIndoorManager";
  }

  /**
   * Fetches floor plan data from IndoorAtlas server.
   */
  private void fetchFloorPlan(String id) {
      final IATask<IAFloorPlan> task = mResourceManager.fetchFloorPlanWithId(id);
      task.setCallback(new IAResultCallback<IAFloorPlan>() {

          @Override
          public void onResult(IAResult<IAFloorPlan> result) {
              final IAFloorPlan iaFloorPlan = result.getResult();
              if (result.isSuccess() && iaFloorPlan != null) {
                  WritableMap params = Arguments.createMap();
                  mCenter = iaFloorPlan.getCenter();
                  mBottomLeft = iaFloorPlan.getBottomLeft();
                  mTopRight = iaFloorPlan.getTopRight();
                  mTopLeft = iaFloorPlan.getTopLeft();

                  params.putString("url", iaFloorPlan.getUrl());
                  params.putString("id", iaFloorPlan.getId());
                  params.putString("name", iaFloorPlan.getName());
                  params.putInt("floor", iaFloorPlan.getFloorLevel());
                  params.putDouble("bottomLeftLatitude", mBottomLeft.latitude );
                  params.putDouble("bottomLeftLongitude", mBottomLeft.longitude );
                  params.putDouble("topRightLatitude", mTopRight.latitude);
                  params.putDouble("topRightLongitude", mTopRight.longitude);
                  params.putDouble("topLeftLatitude", mTopLeft.latitude);
                  params.putDouble("topLeftLongitude", mTopLeft.longitude);
                  params.putDouble("centerLatitude", mCenter.latitude);
                  params.putDouble("centerLongitude", mCenter.longitude);
                  params.putDouble("height", iaFloorPlan.getHeightMeters());
                  params.putDouble("width", iaFloorPlan.getWidthMeters());
                  params.putDouble("bearing", iaFloorPlan.getBearing());
                  sendEvent(getReactApplicationContext(), "getFloorPlan", params);
              }
          }
      }, Looper.getMainLooper()); // deliver callbacks using main looper
  }
}