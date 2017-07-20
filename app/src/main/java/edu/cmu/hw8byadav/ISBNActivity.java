package edu.cmu.hw8byadav;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.vision.barcode.Barcode;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;

public class ISBNActivity extends AppCompatActivity {

    private ConnectivityManager mConnectivityManager = null;
    ProgressDialog mProgress;
    TextView title;
    TextView genre;
    TextView description;
    private String isbnForImage;
    private static final String coverLibraryURL = "http://covers.openlibrary.org/b/isbn/";
    private static final String suffixLarge = "-L.jpg?default=false";
    private static final String suffixMedium = "-M.jpg?default=false";
    private ImageView imageView;
    private boolean getFromCoversLibrary = false;
    private String imageURLString;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_isbn);
        Intent data = getIntent();

        //If barcode detected from camera, get isbn from it
        // else
        // get from user manual entry
        if(false){
//            Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
//            isbnForImage = barcode.displayValue;
        } else {
            Log.v(SecondActivity.manualUseInput, data.getStringExtra(SecondActivity.manualUseInput));
            isbnForImage = data.getStringExtra(SecondActivity.manualUseInput);
        }

        imageView = (ImageView)findViewById(R.id.image);

        title = (TextView) findViewById(R.id.title);
        genre = (TextView) findViewById(R.id.genre);
        description = (TextView) findViewById(R.id.description);
        imageURLString = coverLibraryURL + isbnForImage + suffixLarge;
        new GoogleApiRequest(isbnForImage).execute();
//        setfoc
//        new GoogleApiRequest("1234567890").execute();

    }

    // Received ISBN from Barcode Scanner. Send to GoogleBooks to obtain book information.
    private class GoogleApiRequest extends AsyncTask<String, Object, JSONObject> {
        String isbn;
        GoogleApiRequest(String isbn){
            this.isbn = isbn;
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
            mProgress = new ProgressDialog(ISBNActivity.this);
            mProgress.setMessage("Fetching your data...");
            mProgress.show();
        }
        @Override
        protected JSONObject doInBackground(String... isbns) {
            Log.v("point1", "true");
            // Stop if cancelled
            if(isCancelled()){
                Log.v("cancelled", "in doInBackground");
                return null;
            }

            String apiUrlString = "https://www.googleapis.com/books/v1/volumes?q=isbn:"
                    + this.isbn;
            try{
                HttpURLConnection connection = null;
                // Build Connection.
                try{
                    URL url = new URL(apiUrlString);
                    Log.v("URL called", apiUrlString);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setReadTimeout(60000); // 1 minute
                    connection.setConnectTimeout(60000); // 1 minute
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (ProtocolException e) {
                    e.printStackTrace();
                }
                int responseCode = connection.getResponseCode();
                Log.v("point3", "true");
                if(responseCode != 200){
                    Log.v(getClass().getName(), "GoogleBooksAPI request failed. Response Code: " + responseCode);
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

                isCoverInCoversLibrary();

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
                Log.v("all went well", "in post execute");
                // All went well. Do something with your new JSONObject.
                bookJSONToTitle(responseJson);
                displayCoverArt(responseJson);
            }
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

    private void setTitle(String title){
        try{
            this.title.setText(title, TextView.BufferType.EDITABLE);
        } catch(NullPointerException e){
            e.printStackTrace();
        }
    }
    private void bookJSONToTitle(JSONObject jsonObject){
        try {
            if (jsonObject.length() == 0 || jsonObject.getInt("totalItems") == 0) {
                mProgress.cancel();
                finish();
                return;
            }
//            JSONArray books;
            if(!jsonObject.has("items"))
                throw new JSONException(String.format(getString(R.string.json_missing_key), "items"));
            JSONArray books = jsonObject.getJSONArray("items");
            Log.v("books",books.toString());

            //Set title
            JSONObject firstBook = (JSONObject) books.get(0);
            if(!firstBook.has("volumeInfo"))
                throw new JSONException(String.format(getString(R.string.json_missing_key), "volumeInfo"));
            JSONObject firstBookVolumeInfo = firstBook.getJSONObject("volumeInfo");
            setTitle(firstBookVolumeInfo.getString("title"));
            Log.v("firstBookVolumeInfo", firstBookVolumeInfo.toString());

            //Set Genre
            if(!firstBookVolumeInfo.has("categories")){
                throw new JSONException(String.format(getString(R.string.json_missing_key), "categories"));
            }
            JSONArray jsonCategories = firstBookVolumeInfo.getJSONArray("categories");
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i< jsonCategories.length(); i++){
                sb.append(jsonCategories.get(i).toString());
                if( i != jsonCategories.length() - 1){
                    sb.append(", ");
                }
            }
            this.genre.setText(sb.toString(), TextView.BufferType.EDITABLE);

            //set description
            if(firstBookVolumeInfo.has("description")) {
                String description = firstBookVolumeInfo.getString("description");
                this.description.setText(description, TextView.BufferType.EDITABLE);
            }

            Log.v("firstBookVlIn.getString", firstBookVolumeInfo.getString("title"));
        } catch(JSONException e){
            e.printStackTrace();
        }
    }

    private void displayCoverArt(JSONObject jsonObject){

        if(getFromCoversLibrary == true){
            Picasso.with(getApplicationContext()).load(imageURLString).into(imageView);
        } else {
            try {
                fetchFromGoogleBookThumbnails(jsonObject);
            } catch (JSONException e){
                e.printStackTrace();
            }
        }
    }

    private void isCoverInCoversLibrary() throws IOException {

        Log.v("coverlibraryURL", imageURLString);
        HttpURLConnection connection = null;
        try {
            // Build Connection.
            URL url = new URL(imageURLString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setReadTimeout(10000); // 10 seconds
            connection.setConnectTimeout(10000); // 10 seconds
        } catch (MalformedURLException e) {
            // Impossible: The only two URLs used in the app are taken from string resources.
            e.printStackTrace();
        } catch (ProtocolException e) {
            // Impossible: "GET" is a perfectly valid request method.
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        int responseCode = connection.getResponseCode();
        if(responseCode == 404){
            Log.v(getClass().getName(), "Failed to get book covers from covers library: " + responseCode);
            connection.disconnect();
        }
        if(responseCode == 200)
            getFromCoversLibrary = true;
    }

    private void fetchFromGoogleBookThumbnails(JSONObject jsonObject) throws JSONException {
        JSONArray books = jsonObject.getJSONArray("items");
        JSONObject firstBook = (JSONObject) books.get(0);
        JSONObject firstBookVolumeInfo = firstBook.getJSONObject("volumeInfo");
        JSONObject imageLinksJSON = firstBookVolumeInfo.getJSONObject("imageLinks");
        String thumbnailURL = imageLinksJSON.getString("thumbnail");
        Picasso.with(getApplicationContext()).load(thumbnailURL).into(imageView);
    }
}
