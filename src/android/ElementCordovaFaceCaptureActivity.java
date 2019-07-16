package com.element.camera;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.element.plugin.ElementFaceMatchingSDK;
import com.element.plugin.FaceMatchingTask;
import com.google.gson.Gson;

import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;

import okhttp3.Response;

import static com.element.plugin.ElementFaceMatchingSDK.CALLBACK_TO_JS;

public class ElementCordovaFaceCaptureActivity extends ElementFaceCaptureActivity {
	public final static String EXTRA_CAPTURE_RESULT = "com.element.EXTRA_CAPTURE_RESULT";
	private boolean success;
	private String message;
	private boolean resultSet;

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		// defaults return if finish() without setResults()
		message = "User cancelled.";
	}

	@Override
	public void onImageCaptured(Capture[] captures, String s) {
		if (resultSet) {
			return;
		}

		if (captures.length == 0) {
			setResult(false, "No clear captures taken.");
			return;
		}

		if (CALLBACK_TO_JS) {
			handleDirectJsCallback(captures);
		} else {
			handleServerPost(captures);
		}
	}

	private void handleDirectJsCallback(Capture[] captures) {
		ArrayList<String> list = new ArrayList<String>();
		for (Capture capture : captures) {
			byte[] imageBytes = capture.data;
			String filename = capture.tag;
			FileOutputStream outputStream;

			try {
				outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
				outputStream.write(imageBytes);
				outputStream.close();

				list.add(filename);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		setResult(true, new Gson().toJson(list));
	}

	private void handleServerPost(Capture[] captures) {
		toastMessage("Checking with server. Please wait.");
		String userId = getIntent().getStringExtra(EXTRA_ELEMENT_USER_ID);
		new FaceMatchingTask(faceMatchingTaskCallback).execute(userId, captures);
	}

	private FaceMatchingTask.FaceMatchingTaskCallback faceMatchingTaskCallback = new FaceMatchingTask.FaceMatchingTaskCallback() {
		@Override
		public Context getContext() {
			return getBaseContext();
		}

		@Override
		public void onResult(Response response) throws Exception {
			if (response.code() == HttpURLConnection.HTTP_OK) {
				FaceMatchingTask.FmResponse fmResponse = new Gson().fromJson(response.body().string(), FaceMatchingTask.FmResponse.class);
				setResult(true, fmResponse.displayMessage);
			} else {
				FaceMatchingTask.ServerMessage serverMessage = new Gson().fromJson(response.body().string(), FaceMatchingTask.ServerMessage.class);
				setResult(false, serverMessage.message);
			}
		}

		@Override
		public void onException(String message) {
			setResult(false, message);
		}
	};

	@Override
	protected void showResult(String message, int iconResId, boolean reCapture) {
		setResult(false, message);
	}

	@NonNull
	@Override
	ApiController createApiController() {
		return new CordovaFaceCaptureApiController();
	}

	class CordovaFaceCaptureApiController extends FaceCaptureApiController {
		@Override
		public void wrapUpResult(@NonNull IntentAgent.IntentClient intentClient, @Nullable UserInfo userInfo) {
			Intent resultIntent = new Intent();
			if (userInfo != null) {
				resultIntent.putExtra(EXTRA_USER_APP_ID, userInfo.appId);
				resultIntent.putExtra(EXTRA_ELEMENT_USER_ID, userInfo.userId);
			}
			resultIntent.putExtra(EXTRA_CAPTURE_RESULT, message);
			int resultCode = success ? RESULT_OK : RESULT_CANCELED;
			ElementSDK.getIntentAgent().processResult(intentClient, resultCode, resultIntent);
		}
	}

	private void setResult(boolean success, String message) {
		resultSet = true;
		this.success = success;
		this.message = message;
		finish();
	}
}
