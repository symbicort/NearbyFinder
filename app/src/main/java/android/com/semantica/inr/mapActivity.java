package android.com.semantica.inr;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class mapActivity extends ActionBarActivity {
    private GoogleMap map;
    ProgressDialog pDialog;
    dbAdapter helper = new dbAdapter(this);
    ListView lv;
    ArrayList<LatLng> mMarkerPoints;
    double mLatitude = 0;
    double mLongitude = 0;
    GPSTracker gps;
    int i = 0;
    protected LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Harita");
        final RadioButton walking = (RadioButton) findViewById(R.id.walking);
        final RadioButton driving = (RadioButton) findViewById(R.id.driving);
        gps = new GPSTracker(this);

        if (gps.canGetLocation()) {
            Log.d("Your Location", "latitude:" + gps.getLatitude() + ", longitude: " + gps.getLongitude());
        } else {
            gps.showSettingsAlert();
            return;
        }

        lv = (ListView) findViewById(R.id.mapList);


        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.fragment)).getMap();

        mMarkerPoints = new ArrayList<LatLng>();

        pDialog = new ProgressDialog(mapActivity.this);
        pDialog.setMessage("Bilgiler yükleniyor ...");
        pDialog.setIndeterminate(false);
        pDialog.setCancelable(false);
        pDialog.show();

        mLatitude = gps.getLatitude();
        mLongitude = gps.getLongitude();
        CameraUpdate update = CameraUpdateFactory.newLatLngZoom(new LatLng(mLatitude, mLongitude), 13);
        map.animateCamera(update);
        map.addMarker(new MarkerOptions().position(new LatLng(mLatitude, mLongitude)));
        String type = getIntent().getExtras().getString("type");


        StringBuilder sb = new StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json?");
        sb.append("location=" + mLatitude + "," + mLongitude);
        sb.append("&radius=1500");
        sb.append("&types=" + type);
        sb.append("&rankBy=distance");
        sb.append("&sensor=true");
        sb.append("&key=AIzaSyCRLa4LQZWNQBcjCYcIVYA45i9i8zfClqc");

       
        PlacesTask placesTask = new PlacesTask();
        placesTask.execute(sb.toString());


        walking.setChecked(true);
        walking.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                driving.setChecked(false);

            }
        });
        driving.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                walking.setChecked(false);

            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                map.clear();
                TextView lat = (TextView) view.findViewById(R.id.latitude);
                TextView lng = (TextView) view.findViewById(R.id.longitude);
                TextView name = (TextView) view.findViewById(R.id.listName);
                MarkerOptions originOp = new MarkerOptions();
                MarkerOptions destOp = new MarkerOptions();
                LatLng origin = new LatLng(mLatitude, mLongitude);
                LatLng dest = new LatLng(Double.parseDouble(lat.getText().toString()), Double.parseDouble(lng.getText().toString()));
                pDialog = new ProgressDialog(mapActivity.this);
                pDialog.setMessage("Yol tarifi alınıyor ...");
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(false);
                pDialog.show();
                originOp.position(origin);
                destOp.position(dest);
                destOp.title(name.getText().toString());
                if (getIntent().getExtras().getString("type").equals("hospital")) {
                    destOp.icon(BitmapDescriptorFactory.fromResource(R.drawable.hospitalmarker));
                } else {
                    destOp.icon(BitmapDescriptorFactory.fromResource(R.drawable.pharmacymarker));
                }
                originOp.icon(BitmapDescriptorFactory.fromResource(R.drawable.house));
                map.addMarker(originOp);
                map.addMarker(destOp);
               
                String url = getDirectionsUrl(origin, dest);


                DownloadTask downloadTask = new DownloadTask();

     
                downloadTask.execute(url);

            }
        });

    }

    private class PlacesTask extends AsyncTask<String, Integer, String> {

        String data = null;

        @Override
        protected String doInBackground(String... url) {
            try {
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        
        @Override
        protected void onPostExecute(String result) {
            ParserTask parserTask = new ParserTask();
            parserTask.execute(result);
        }

    }


    private class ParserTask extends AsyncTask<String, List<HashMap<String, String>>, List<HashMap<String, String>>> {

        JSONObject jObject;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }
        @Override
        protected List<HashMap<String, String>> doInBackground(String... jsonData) {

            List<HashMap<String, String>> places = null;
            PlaceJSONParser placeJsonParser = new PlaceJSONParser();


            try {
                jObject = new JSONObject(jsonData[0]);

         
                places = placeJsonParser.parse(jObject);


            } catch (Exception e) {
                Log.d("Exception", e.toString());
            }
            return places;
        }


    
        @Override
        protected void onPostExecute(List<HashMap<String, String>> list) {
          
            pDialog.dismiss();
            map.clear();
            String[] nameList = new String[list.size()];
            String[] address = new String[list.size()];
            String[] lati = new String[list.size()];
            String[] longi = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {

               
                MarkerOptions markerOptions = new MarkerOptions();

                
                HashMap<String, String> hmPlace = list.get(i);

                
                double lat = Double.parseDouble(hmPlace.get("lat"));

          
                double lng = Double.parseDouble(hmPlace.get("lng"));

    
                String name = hmPlace.get("place_name");

                nameList[i] = hmPlace.get("place_name");
                address[i] = hmPlace.get("vicinity");
                lati[i] = hmPlace.get("lat");
                longi[i] = hmPlace.get("lng");
               
                String vicinity = hmPlace.get("vicinity");


                LatLng latLng = new LatLng(lat, lng);
             
                markerOptions.position(latLng);

               
                markerOptions.title(name + " : " + vicinity);
                if (getIntent().getExtras().getString("type").equals("hospital")) {
                    markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.hospitalmarker));
                } else {
                    markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.pharmacymarker));
                }


                map.addMarker(markerOptions);
            }
            map.addMarker(new MarkerOptions().position(new LatLng(gps.getLatitude(),gps.getLongitude()))
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.house)));
            mapListadapter adapter = new mapListadapter(mapActivity.this, nameList, address, lati, longi);
            lv = (ListView) findViewById(R.id.mapList);
            lv.setAdapter(adapter);

        }
    }

    private String downloaddrawUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);


            urlConnection = (HttpURLConnection) url.openConnection();

          
            urlConnection.connect();

           
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            Log.d("Exception while downloading url", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {
        RadioButton walking = (RadioButton) findViewById(R.id.walking);
        String mode = null;
        if (walking.isChecked()) {
            mode = "mode=walking";
        } else {
            mode = "mode=driving";
        }
        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

        // Sensor enabled
        String sensor = "sensor=false";

        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + mode;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        return url;
    }


    private class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... url) {

        
            String data = "";

            try {
                
                data = downloaddrawUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            drawParserTask parserTask = new drawParserTask();

           
            parserTask.execute(result);
        }
    }

    private class drawParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

               
                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        
        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList<LatLng> points = null;
            PolylineOptions lineOptions = null;
            String distance = "";
            String duration = "";
            
            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList<LatLng>();
                lineOptions = new PolylineOptions();

              
                List<HashMap<String, String>> path = result.get(i);

              
                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);
                    if (j == 0) {    // Get distance from the list
                        distance = (String) point.get("distance");
                        continue;
                    } else if (j == 1) { // Get duration from the list
                        duration = (String) point.get("duration");
                        continue;
                    }
                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));

                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                // Adding all the points in the route to LineOptions
                lineOptions.addAll(points);
                lineOptions.width(6);
                lineOptions.color(Color.BLUE);
            }
            map.addPolyline(lineOptions);
            pDialog.dismiss();
        }
    }

    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

          
            urlConnection = (HttpURLConnection) url.openConnection();

          
            urlConnection.connect();

         
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            Log.d("Exception while downloading url", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }

        return data;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        getMenuInflater().inflate(R.menu.menu_index, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      
        int id = item.getItemId();

  
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == android.R.id.home) {
            finish();
        }

        return super.onOptionsItemSelected(item);
    }
}
