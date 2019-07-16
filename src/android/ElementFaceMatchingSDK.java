package com.element.plugin;

import org.apache.cordova.*;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONException;

import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.element.camera.ElementCordovaFaceCaptureActivity;
import com.element.camera.ElementFaceSDK;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import static com.element.camera.ElementCordovaFaceCaptureActivity.EXTRA_CAPTURE_RESULT;

public class ElementFaceMatchingSDK extends CordovaPlugin {

	public static final boolean CALLBACK_TO_JS = false;
	public static final String CAMERA = Manifest.permission.CAMERA;
	public static final String ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
	public static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;

	public static final int CAPTURE_REQ_CODE = 12802;

	private CallbackContext latestCallback;
	private boolean permissionsAccepted;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
	    super.initialize(cordova, webView);
		ElementFaceSDK.initSDK(cordova.getActivity().getApplication());
		requestPermission();
	}

	private void requestPermission() {
		if (cordova.hasPermission(CAMERA) &&
				cordova.hasPermission(ACCESS_FINE_LOCATION) &&
				cordova.hasPermission(ACCESS_COARSE_LOCATION)) {
			permissionsAccepted = true;
		} else {
			String[] permissions = {CAMERA, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION};
			cordova.requestPermissions(this, 0, permissions);
		}
	}

	// called from element.js
	@Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    	Log.e("ElementFaceMatchingSDK", action);
		latestCallback = callbackContext;

		// need permissions to do anything but list
    	if (!permissionsAccepted) {
    		handlePermissionAction();
    		return false;
		}

	    if (action.equals("capture")) {
	    	String id = args.getString(0);
	        handleCreateAction(id);
	        return true;
	    }
	    return false;
	}

	private void handlePermissionAction() {
		requestPermission();
		latestCallback.error("Must accept permissions first.");
	}

	private void handleCreateAction(String userId) {
		Activity activity = cordova.getActivity();

		Intent intent = new Intent(activity, ElementCordovaFaceCaptureActivity.class);
		intent.putExtra(ElementCordovaFaceCaptureActivity.EXTRA_ELEMENT_USER_ID, userId);
		intent.putExtra(ElementCordovaFaceCaptureActivity.EXTRA_USER_APP_ID, cordova.getActivity().getPackageName());
		cordova.startActivityForResult(this, intent, CAPTURE_REQ_CODE);
	}

	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
    	for(int r : grantResults) {
	        if(r == PackageManager.PERMISSION_DENIED) {
	            Toast.makeText(cordova.getActivity().getApplicationContext(), "Permission Denied", Toast.LENGTH_LONG).show();
	            return;
	        }
	    }
		permissionsAccepted = true;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CAPTURE_REQ_CODE) {
			String response = data == null ? "Unknown Error" : data.getStringExtra(EXTRA_CAPTURE_RESULT);
			if (resultCode == Activity.RESULT_OK) {
				if (CALLBACK_TO_JS) {
					latestCallback.success(buildJsonFromResponse(response));
				} else {
					latestCallback.success(response);
				}
			} else {
				latestCallback.error(response);
			}
		}
	}

	private String buildJsonFromResponse(String response) {
		// in this one case, the message is actually a Json List of captures
		List<String> fileList = new Gson().fromJson(response, new TypeToken<List<String>>() {
		}.getType());
		List<String> encodedImages = new ArrayList<String>();
		for (String filename : fileList) {
			FileInputStream inputStream;
			try {
				inputStream = cordova.getActivity().openFileInput(filename);

				byte[] imageBytes = new byte[inputStream.available()];
				inputStream.read(imageBytes);
				inputStream.close();
				cordova.getActivity().deleteFile(filename);

				encodedImages.add(Base64.encodeToString(imageBytes, Base64.DEFAULT));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return new Gson().toJson(encodedImages);
	}
}
