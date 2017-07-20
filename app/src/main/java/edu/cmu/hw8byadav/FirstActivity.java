package edu.cmu.hw8byadav;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.CommonStatusCodes;
import edu.cmu.hw8byadav.camera.CameraSource;
import edu.cmu.hw8byadav.camera.CameraSourcePreview;
import edu.cmu.hw8byadav.camera.GraphicOverlay;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Activity for the multi-tracker app.  This app detects barcodes and displays the value with the
 * rear facing camera. During detection overlay graphics are drawn to indicate the position,
 * size, and ID of each barcode.
 */
public final class FirstActivity extends AppCompatActivity implements BarcodeGraphicTracker.BarcodeDetectorListener {
    private static final String TAG = "HW8";

    // intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    // constants used to pass extra data in the intent
    public static final String AutoFocus = "AutoFocus";
    public static final String UseFlash = "UseFlash";
    public static final String AutoCapture = "AutoCapture";
    public static final String BarcodeObject = "Barcode";

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    // helper objects for detecting taps and pinches.
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private TextView scanResult;
    private Button btnTweet;
    private Button btnSensorAPI;
    private String decodedQRCode;
    public static String TWITTER_CONSUMER_KEY = "ozX8AZUOf19wEb7JviZeEFHR1";
    public static String TWITTER_CONSUMER_SECRET = "548wmkcRj37L9DzOcmT4TVwFTSWtNIMjn2FeeS5l3u6qpTshmI";
    public static String PREFERENCE_TWITTER_LOGGED_IN="TWITTER_LOGGED_IN";
    Dialog auth_dialog;
    WebView web;
    SharedPreferences pref;
    twitter4j.Twitter twitter;
    RequestToken requestToken;
    AccessToken accessToken;
    String oauth_url, oauth_verifier, profile_url;
    ProgressDialog mProgress;
    private ConnectivityManager mConnectivityManager = null;
    String tweetToSend;

    /**
     * Initializes the UI and creates the detector pipeline.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_first);

        pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = pref.edit();
        edit.putString("CONSUMER_KEY", TWITTER_CONSUMER_KEY);
        edit.putString("CONSUMER_SECRET", TWITTER_CONSUMER_SECRET);
        edit.commit();

        twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(pref.getString("CONSUMER_KEY", ""), pref.getString("CONSUMER_SECRET", ""));

        mPreview = (CameraSourcePreview) findViewById(R.id.scan_camera);
        mGraphicOverlay = (GraphicOverlay<BarcodeGraphic>) findViewById(R.id.graphicOverlay);

        // read parameters from the intent used to launch the activity.
        boolean autoFocus = true;
        boolean useFlash = false;
        boolean autoCapture = true;

        if(autoCapture)
            BarcodeGraphicTracker.mBarcodeDetectorListener = this;

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(autoFocus, useFlash);
        } else {
            requestCameraPermission();
        }

        gestureDetector = new GestureDetector(this, new CaptureGestureListener());
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        Snackbar.make(mGraphicOverlay, "Hold camera steady. Barcode will be captured automatically.",
                Snackbar.LENGTH_LONG)
                .show();

        scanResult = (TextView)findViewById(R.id.scan_result);
        btnTweet = (Button)findViewById(R.id.tweet);
        btnSensorAPI = (Button)findViewById(R.id.sensor_api);

        btnSensorAPI.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(scanResult.getText().length() == 0){
                    Toast.makeText(FirstActivity.this, "Can't call Sensor API with empty string! Scan QR code first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                new FirstActivity.FetchFromAPI(scanResult.getText().toString()).execute();
            }
        });

        btnTweet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(scanResult.getText().length() == 0){
                    Toast.makeText(FirstActivity.this, "Can't tweet empty! Scan QR code first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                sendOutTweet();
            }
        });
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean b = scaleGestureDetector.onTouchEvent(e);

        boolean c = gestureDetector.onTouchEvent(e);

        return b || c || super.onTouchEvent(e);
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     *
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource(boolean autoFocus, boolean useFlash) {
        Context context = getApplicationContext();

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay);
        barcodeDetector.setProcessor(
                new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.low_storage_error));
            }
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        CameraSource.Builder builder = new CameraSource.Builder(getApplicationContext(), barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(metrics.heightPixels, metrics.widthPixels)
                .setRequestedFps(30.0f);

        // make sure that auto focus is an available option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            builder = builder.setFocusMode(
                    autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);
        }

        mCameraSource = builder
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .build();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            boolean autoFocus = getIntent().getBooleanExtra(AutoFocus,false);
            boolean useFlash = getIntent().getBooleanExtra(UseFlash, false);

            createCameraSource(autoFocus, useFlash);
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Multitracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
                return;
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    /**
     * onTap is called to capture the oldest barcode currently detected and
     * return it to the caller.
     *
     * @param rawX - the raw position of the tap
     * @param rawY - the raw position of the tap.
     * @return true if the activity is ending.
     */
    private boolean onTap(float rawX, float rawY) {

        //TODO: use the tap position to select the barcode.
        BarcodeGraphic graphic = mGraphicOverlay.getFirstGraphic();
        Barcode barcode = null;
        if (graphic != null) {
            barcode = graphic.getBarcode();
            if (barcode != null) {
                Intent data = new Intent();
                data.putExtra(BarcodeObject, barcode);
                setResult(CommonStatusCodes.SUCCESS, data);
                Log.v("ON TAP barcode!","ON TAP barcode detected");
                finish();
            }
            else {
                Log.d(TAG, "barcode data is null");
            }
        }
        else {
            Log.d(TAG,"no barcode detected");
        }
        return barcode != null;
    }

    @Override
    public void onObjectDetected(final Barcode data) {
        //do something with the barcode data here
//        Intent mIntent = new Intent(this, .class);
//        mIntent.putExtra(BarcodeObject, data);
//        setResult(CommonStatusCodes.SUCCESS, mIntent);
//        Log.v("barcode detected!","barcode detected");
//        startActivity(mIntent);
//        finish();
        decodedQRCode = data.displayValue;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setScanResult(data);
            }
        });
    }

    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {

            return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

        /**
         * Responds to scaling events for a gesture in progress.
         * Reported by pointer motion.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should consider this event
         * as handled. If an event was not handled, the detector
         * will continue to accumulate movement until an event is
         * handled. This can be useful if an application, for example,
         * only wants to update scaling factors if the change is
         * greater than 0.01.
         */
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return false;
        }

        /**
         * Responds to the beginning of a scaling gesture. Reported by
         * new pointers going down.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should continue recognizing
         * this gesture. For example, if a gesture is beginning
         * with a focal point outside of a region where it makes
         * sense, onScaleBegin() may return false to ignore the
         * rest of the gesture.
         */
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        /**
         * Responds to the end of a scale gesture. Reported by existing
         * pointers going up.
         * <p/>
         * Once a scale has ended, {@link ScaleGestureDetector#getFocusX()}
         * and {@link ScaleGestureDetector#getFocusY()} will return focal point
         * of the pointers remaining on the screen.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         */
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mCameraSource.doZoom(detector.getScaleFactor());
        }
    }

    private void sendOutTweet(){
        if (!pref.getBoolean(PREFERENCE_TWITTER_LOGGED_IN,false)){
            new FirstActivity.TokenGet().execute(); //no Token obtained, first time use
        }else{
            new FirstActivity.PostTweet().execute(); //when Tokens are obtained , ready to Post
        }
    }

    private class PostTweet extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected String doInBackground(String... args) {

            ConfigurationBuilder builder = new ConfigurationBuilder();
            builder.setOAuthConsumerKey(pref.getString("CONSUMER_KEY", ""));
            builder.setOAuthConsumerSecret(pref.getString("CONSUMER_SECRET", ""));

            AccessToken accessToken = new AccessToken(pref.getString("ACCESS_TOKEN", ""), pref.getString("ACCESS_TOKEN_SECRET", ""));
            twitter4j.Twitter twitter = new TwitterFactory(builder.build()).getInstance(accessToken);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm aa");
            String currentDateTime = sdf.format(new Date());
            String status = "@08723Mapp byadav " +
                    tweetToSend + " at " + currentDateTime.toString();
            Log.v("tweet text", status);
            twitter4j.Status response = null;
            try {
                if(tweetToSend.length() != 0)
                    response = twitter.updateStatus(status);
            } catch (twitter4j.TwitterException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return (response != null) ? response.toString() : "";
        }

        protected void onPostExecute(String res) {
            if (res != null) {
                // progress.dismiss();
                Toast.makeText(getApplicationContext(), "Tweet successfully Posted", Toast.LENGTH_SHORT).show();

            } else {
                //progress.dismiss();
                Toast.makeText(getBaseContext(), "Error while tweeting !", Toast.LENGTH_SHORT).show();

            }
        }
    }

    private class TokenGet extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... args) {
            try {
                requestToken = twitter.getOAuthRequestToken();
                oauth_url = requestToken.getAuthorizationURL();
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return oauth_url;
        }

        @Override
        protected void onPostExecute(String oauth_url) {
            if(oauth_url != null){
                auth_dialog = new Dialog(FirstActivity.this);
                auth_dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                auth_dialog.setContentView(R.layout.oauth_webview);

                web = (WebView)auth_dialog.findViewById(R.id.webViewOAuth);
                web.getSettings().setJavaScriptEnabled(true);
                web.loadUrl(oauth_url);
                web.setWebViewClient(new WebViewClient() {
                    boolean authComplete = false;

                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon){
                        super.onPageStarted(view, url, favicon);                 }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        if (url.contains("oauth_verifier") && authComplete == false){
                            authComplete = true;
                            Uri uri = Uri.parse(url);
                            oauth_verifier = uri.getQueryParameter("oauth_verifier");
                            auth_dialog.dismiss();
                            new FirstActivity.AccessTokenGet().execute();
                        }else if(url.contains("denied")){
                            auth_dialog.dismiss();
                            Toast.makeText(getBaseContext(), "Sorry !, Permission Denied", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                Log.d("Debug", auth_dialog.toString());
                auth_dialog.show();
                auth_dialog.setCancelable(true);
            }else{
                Toast.makeText(getBaseContext(), "Sorry !, Error or Invalid Credentials", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class AccessTokenGet extends AsyncTask<String, String, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(String... args) {
            try {
                accessToken = twitter.getOAuthAccessToken(requestToken, oauth_verifier);
                SharedPreferences.Editor edit = pref.edit();
                edit.putString("ACCESS_TOKEN", accessToken.getToken());
                edit.putString("ACCESS_TOKEN_SECRET", accessToken.getTokenSecret());
                edit.putBoolean(PREFERENCE_TWITTER_LOGGED_IN, true);

                User user = twitter.showUser(accessToken.getUserId());
                profile_url = user.getOriginalProfileImageURL();
                edit.putString("NAME", user.getName());
                edit.putString("IMAGE_URL", user.getOriginalProfileImageURL());
                edit.commit();
            } catch (TwitterException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean response) {
            if(response){
                //progress.hide(); after login, tweet Post right away
                new FirstActivity.PostTweet().execute();
            }
        }
    }

    private void setScanResult(Barcode data){
        scanResult.setText(data.displayValue);
    }

    private class FetchFromAPI extends AsyncTask<String, Object, JSONObject>{
        String decodedQR;
        FetchFromAPI(String decodedQR){
            this.decodedQR = decodedQR;
        }

        @Override
        protected void onPreExecute() {
            // Check network connection.
            if(isNetworkConnected() == false){
                // Cancel request.
                Log.v(getClass().getName(), "Not connected to the internet");
                cancel(true);
                return;
            }
            mProgress = new ProgressDialog(FirstActivity.this);
            mProgress.setMessage("Fetching your data...");
            mProgress.show();
        }

        @Override
        protected JSONObject doInBackground(String... strings) {
            Log.v("point1", "true");
            // Stop if cancelled
            if(isCancelled()){
                Log.v("cancelled", "in doInBackground");
                return null;
            }

            String apiUrlString = "http://35.164.249.96:8080/Beacon/getLocation";
            try{
                HttpURLConnection connection = null;
                // Build Connection.
                try{
                    URL url = new URL(apiUrlString);
                    Log.v("URL called", apiUrlString);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setReadTimeout(60000); // 1 minute
                    connection.setConnectTimeout(60000); // 1 minute
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setDoInput(true);
                    connection.setDoOutput(true);

                    String str = "{\"uid\": \"B92E1BCC-BE26-45AB-ACA2-27F9AF627306\",\"accuracy\":\"fine\"}";
                    byte[] outputInBytes = str.getBytes("UTF-8");
                    OutputStream os = connection.getOutputStream();
                    os.write( outputInBytes );
                    os.close();

                } catch (MalformedURLException e) {
                    // Impossible: The only two URLs used in the app are taken from string resources.
                    e.printStackTrace();
                } catch (ProtocolException e) {
                    // Impossible: "GET" is a perfectly valid request method.
                    e.printStackTrace();
                }
                int responseCode = connection.getResponseCode();
                Log.v("point3", "true");
                if(responseCode != 200){
                    Log.v(getClass().getName(), "Webservice API request failed. Response Code: " + responseCode);
                    connection.disconnect();
                    return null;
                }

                // Read data from response.
                StringBuilder builder = new StringBuilder();
                BufferedReader responseReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = responseReader.readLine();
                while (line != null){
                    builder.append(line);
                    line = responseReader.readLine();
                }
                String responseString = builder.toString();
                Log.v(getClass().getName(), "Response String: " + responseString);
                JSONObject responseJson = new JSONObject(responseString);
                // Close connection and return response code.
                connection.disconnect();

//                isCoverInCoversLibrary();

                return responseJson;
            } catch (SocketTimeoutException e) {
                Log.v(getClass().getName(), "Connection timed out. Returning null");
                return null;
            } catch(IOException e){
                Log.v(getClass().getName(), "IOException when connecting to Google Books API.");
                e.printStackTrace();
                return null;
            } catch (JSONException e) {
                Log.v(getClass().getName(), "JSONException when connecting to Google Books API.");
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(JSONObject responseJson) {
            if(isCancelled()){
                // Request was cancelled due to no network connection.
                showNetworkDialog();
            } else if(responseJson == null){
                showSimpleDialog(getResources().getString(R.string.dialog_null_response));
            }
            else{
                mProgress.hide();
                Log.v("allGoodtweetrcvdJSON", "in post execute");
                // All went well. Tweet received JSON.
                setTweetMessage(responseJson);
            }
        }
    }

    protected boolean isNetworkConnected(){

        // Instantiate mConnectivityManager if necessary
        if(mConnectivityManager == null){
            mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        // Is device connected to the Internet?
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        if(networkInfo != null && networkInfo.isConnected()){
            return true;
        } else {
            return false;
        }
    }

    private void showNetworkDialog() {
        try {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();

            alertDialog.setTitle("Info");
            alertDialog.setMessage(getString(R.string.internet_unavailable));
            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
            alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();

                }
            });

            alertDialog.show();
        }catch(Exception e) {
            Log.v("Network", "Show Dialog: "+e.getMessage());
        }
    }
    private void showSimpleDialog(String showString) {
        try {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();

            alertDialog.setTitle("Info");
            alertDialog.setMessage(showString);
            alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
            alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();

                }
            });

            alertDialog.show();
        }catch(Exception e) {
            Log.v("Network", "Show Dialog: "+e.getMessage());
        }
    }

    private void setTweetMessage(JSONObject jsonObject){
        if (jsonObject.has("message")){
            try{
                tweetToSend = jsonObject.getString("message");
            }catch (JSONException e){
                e.printStackTrace();
            }
        }
    }

}
