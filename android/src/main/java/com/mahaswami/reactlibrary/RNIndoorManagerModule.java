
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
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
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
import java.util.Arrays;
import java.util.List;
import java.io.InputStream;
import android.content.res.Resources;
import com.indooratlas.android.wayfinding.IARoutingLeg;
import com.indooratlas.android.wayfinding.IAWayfinder;

public class RNIndoorManagerModule extends ReactContextBaseJavaModule {

    private IALocationManager locationManager;
    private IAResourceManager mResourceManager;
    private IALatLng mCenter;
    private IALatLng mBottomLeft;
    private IALatLng mTopRight;
    private IALatLng mTopLeft;
    private String floorPlanId;
    private IAWayfinder mWayfinder;
    private IARoutingLeg[] mCurrentRoute;
    private int mFloor;
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
                String graphJSON = loadGraphJSON();
                String errorMessage = "Could not find wayfinding_graph.json from raw resources folder. Cannot do wayfinding.";
                if (graphJSON == null) {
                    WritableMap params = Arguments.createMap();
                    params.putString("message", errorMessage);
                    sendEvent(getReactApplicationContext(), "indoorAtlasError", params);
                } else {
                    mWayfinder = IAWayfinder.create(getReactApplicationContext(), graphJSON);
                }

        locationManager.registerRegionListener(new IARegion.Listener() {

          @Override
          public void onEnterRegion(IARegion iaRegion) {
            String id = iaRegion.getId();
            WritableMap params = Arguments.createMap();
            params.putString("id", id);
            params.putString("message", "Enter " + (iaRegion.getType() == IARegion.TYPE_VENUE
                                                                                    ? "VENUE "
                                                                                    : "FLOOR_PLAN ") + iaRegion.getName());
            params.putBoolean("isTypeFloorPlan", (iaRegion.getType() == IARegion.TYPE_FLOOR_PLAN) ? true : false);
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
            params.putBoolean("isTypeFloorPlan", (iaRegion.getType() == IARegion.TYPE_FLOOR_PLAN) ? true : false);
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
                mFloor = location.getFloorLevel();
                if (mWayfinder != null) {
                    mWayfinder.setLocation(location.getLatitude() ,location.getLongitude(), mFloor);
                }
                updateRoute();
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
    @ReactMethod
    public void getPolylineCoords(ReadableMap coordinates) {
        if (mWayfinder != null) {
            mWayfinder.setDestination(coordinates.getDouble("latitude"), coordinates.getDouble("longitude"), 3);
        }
        updateRoute();
  }
  /**
   * Load "wayfinding_graph.json" from raw resources folder of the app module
   * @return
   */
  private String loadGraphJSON() {
      try {
          Resources res = getReactApplicationContext().getResources();
          int resourceIdentifier = res.getIdentifier("wayfinding_graph", "raw", getReactApplicationContext().getPackageName());
          InputStream in_s = res.openRawResource(resourceIdentifier);
          byte[] b = new byte[in_s.available()];
          in_s.read(b);
          return new String(b);
      } catch (Exception e) {
          return null;
      }
  }

  private void updateRoute() {
      if (mWayfinder == null) {
          return;
      }
      mCurrentRoute = mWayfinder.getRoute();
      if (mCurrentRoute == null || mCurrentRoute.length == 0) {
          // Wrong credentials or invalid wayfinding graph
          return;
      }
      visualizeRoute(mCurrentRoute);
  }

  /**
   * Visualize the IndoorAtlas Wayfinding path on top of the Google Maps.
   * @param legs Array of IARoutingLeg objects returned from IAWayfinder.getRoute()
   */
  private void visualizeRoute(IARoutingLeg[] legs) {
      // optCurrent will contain the wayfinding path in the current floor and opt will contain the
      // whole path, including parts in other floors.
        WritableMap floorPath = Arguments.createMap();
        WritableArray optCurrent = Arguments.createArray();
        WritableArray opt = Arguments.createArray();
        WritableArray optSteps = Arguments.createArray();

      int floorDiff;
      for (IARoutingLeg leg : legs) {

        WritableMap jsonStart = Arguments.createMap();
        WritableMap jsonEnd = Arguments.createMap();

        floorDiff = leg.getBegin().getFloor() - leg.getEnd().getFloor();
        if (leg.getBegin().getFloor() == mFloor && leg.getEnd().getFloor() == mFloor) {

          jsonStart.putDouble("latitude", leg.getBegin().getLatitude());
          jsonStart.putDouble("longitude", leg.getBegin().getLongitude());

          jsonEnd.putDouble("latitude", leg.getEnd().getLatitude());
          jsonEnd.putDouble("longitude", leg.getEnd().getLongitude());

          optCurrent.pushMap(jsonStart);
          optCurrent.pushMap(jsonEnd);

        }else if(floorDiff == 0){
          jsonStart.putDouble("latitude", leg.getBegin().getLatitude());
          jsonStart.putDouble("longitude", leg.getBegin().getLongitude());

          jsonEnd.putDouble("latitude", leg.getEnd().getLatitude());
          jsonEnd.putDouble("longitude", leg.getEnd().getLongitude());

          opt.pushMap(jsonStart);
          opt.pushMap(jsonEnd);
        }else if((floorDiff == -1 || floorDiff == 1)){
          jsonStart.putDouble("latitude", leg.getBegin().getLatitude());
          jsonStart.putDouble("longitude", leg.getBegin().getLongitude());

          jsonEnd.putDouble("latitude", leg.getEnd().getLatitude());
          jsonEnd.putDouble("longitude", leg.getEnd().getLongitude());

          optSteps.pushMap(jsonStart);
          optSteps.pushMap(jsonEnd);
        }
      }
     if (legs.length > 0) {
          IARoutingLeg leg = legs[legs.length-1];
          WritableMap jsonEnd = Arguments.createMap();
          jsonEnd.putDouble("latitude", leg.getEnd().getLatitude());
          jsonEnd.putDouble("longitude", leg.getEnd().getLongitude());
          //opt.pushMap(jsonEnd);
     }
    floorPath.putArray("currentFloorPath", optCurrent);
    floorPath.putArray("otherFloorPath", opt);
    floorPath.putArray("stairsPath", optSteps);
    sendEvent(getReactApplicationContext(), "getPolylineCoords", floorPath);
  }
}