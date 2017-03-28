package com.sindhura.healthtracker.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.BuildConfig;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResolvingResultCallbacks;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.fitness.result.DataSourcesResult;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.sindhura.healthtracker.R;
import com.sindhura.healthtracker.logs.LogWrapper;
import com.sindhura.healthtracker.logs.MessageOnlyLogFilter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by sxk159231 on 3/10/2017.
 */

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 56;
    private GoogleApiClient mClient = null;
    private final String TAG = getClass().getName();
    // TextView tvData;
    GraphView graph, graph2;

    OnDataPointListener mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // tvData = (TextView) findViewById(R.id.tvData);
        graph = (GraphView) findViewById(R.id.graphView);
        graph2 = (GraphView) findViewById(R.id.graphView2);
        initializeLogging();
        if (!checkPermissions()) {
            requestPermissions();
        }
    }


    private void buildFitnessClient() {
        if (mClient == null && checkPermissions()) {
            mClient = new GoogleApiClient.Builder(this)
                    .addApi(Fitness.SENSORS_API)
                    .addApi(Fitness.HISTORY_API)
                    .addApi(Fitness.RECORDING_API)
                    .addApi(Fitness.SESSIONS_API)
                    .addScope(new Scope(Scopes.FITNESS_LOCATION_READ))
                    .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            Log.i(TAG, "Connected!!!");
                            findFitnessDataSources();
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                            } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                            }
                        }
                    })
                    .enableAutoManage(MainActivity.this, 0, new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult result) {
                            Log.i(TAG, "Google Play services connection failed. Cause: " + result.toString());
                            Snackbar.make(MainActivity.this.findViewById(R.id.main_activity_view), "Exception while connecting to Google Play services: " + result.getErrorMessage(),
                                    Snackbar.LENGTH_INDEFINITE).show();
                        }
                    }).build();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void findDataSource(final DataType dataType) {
        Fitness.SensorsApi.findDataSources(mClient, new DataSourcesRequest.Builder()
                .setDataTypes(dataType).setDataSourceTypes(DataSource.TYPE_RAW).build())
                .setResultCallback(new ResultCallback<DataSourcesResult>() {
                    @Override
                    public void onResult(@NonNull DataSourcesResult dataSourcesResult) {
                        for (DataSource dataSource : dataSourcesResult.getDataSources()) {
                            Log.i(TAG, "Data source found: " + dataSource.toString());
                            Log.i(TAG, "Data Source type: " + dataSource.getDataType().getName());
                            registerFitnessDataListener(dataSource, dataType);
                        }
                    }
                });
    }

    private void findFitnessDataSources() {
        DataType[] dataTypes = new DataType[]{DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA, DataType.TYPE_ACTIVITY_SAMPLE,
                DataType.AGGREGATE_ACTIVITY_SUMMARY, DataType.TYPE_CALORIES_EXPENDED, DataType.TYPE_SPEED};
        for (DataType dataType : dataTypes) {
            findDataSource(dataType);
        }
        recordData();
        getHistory();
    }


    private void getHistory() {

        final PendingResult<DataReadResult> pendingResult = Fitness.HistoryApi.readData(
                mClient,
                new DataReadRequest.Builder()
//                        .read(DataType.TYPE_ACTIVITY_SAMPLE)
//                        .read(DataType.TYPE_CALORIES_EXPENDED)
//                        .read(DataType.AGGREGATE_ACTIVITY_SUMMARY)
//                        .read(DataType.TYPE_DISTANCE_DELTA)
//                        .read(DataType.AGGREGATE_CALORIES_EXPENDED)
                        .read(DataType.AGGREGATE_STEP_COUNT_DELTA)
//                        .read(DataType.TYPE_SPEED)
////                        .aggregate(DataType.TYPE_ACTIVITY_SAMPLE, DataType.AGGREGATE_ACTIVITY_SUMMARY)
//                        .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
//                        .bucketByTime(1, TimeUnit.MINUTES)
                        .setTimeRange(System.currentTimeMillis() - 604800000L, System.currentTimeMillis() - (17 * 60 * 60 * 1000), TimeUnit.MILLISECONDS)
                        .build());

     /*   Fitness.HistoryApi.readDailyTotal(mClient, DataType.AGGREGATE_STEP_COUNT_DELTA).setResultCallback(new ResultCallback<DailyTotalResult>() {
            @Override
            public void onResult(@NonNull DailyTotalResult dailyTotalResult) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy H:mm a");
                StringBuilder builder = new StringBuilder();

//                for (DataSet dataSet : dailyTotalResult.getTotal().getDataSets()) {
                    for (com.google.android.gms.fitness.data.DataPoint dp : dailyTotalResult.getTotal().getDataPoints()) {
                        builder.append("Data Point:\n\tType: ").append(dp.getDataType().getName());
                        builder.append("\n\tStart time: ").append(dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                        builder.append("\n\tEnd time: ").append(dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                        for (Field field : dp.getDataType().getFields()) {
                            builder.append("\n\tField: ").append(field.getName()).append("\tValue: ").append(dp.getValue(field));
                        }
                        builder.append("\n\n");
                    }

               // tvData.setText(tvData.getText() + builder.toString());
            }
        });*/

        pendingResult.setResultCallback(new ResolvingResultCallbacks<DataReadResult>(this, 1) {
            @Override
            public void onSuccess(@NonNull DataReadResult dataReadResult) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy H:mm a");
                StringBuilder builder = new StringBuilder();
                int i = 0;
                List<DataPoint> points = new ArrayList<DataPoint>();
                HashMap<Date, Float> map = new HashMap<Date, Float>();
                for (DataSet dataSet : dataReadResult.getDataSets()) {
                    for (com.google.android.gms.fitness.data.DataPoint dp : dataSet.getDataPoints()) {
                        builder.append("Data Point:\n\tType: ").append(dp.getDataType().getName());
                        builder.append("\n\tStart time: ").append(dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                        builder.append("\n\tEnd time: ").append(dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                        Date key = null;
                        try {
                            key = new SimpleDateFormat("MM/dd/yyyy").parse(dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                            float f = 0;
                            for (Field field : dp.getDataType().getFields()) {
                                builder.append("\n\tField: ").append(field.getName()).append("\tValue: ").append(dp.getValue(field));
                                f = f + Float.parseFloat(dp.getValue(field).toString());
                            }
                            boolean found = false;
                            for (Date key2 : map.keySet()) {
                                if (key2.compareTo(key) == 0) {
                                    map.put(key, map.get(key) + f);
                                    found = true;
                                }
                            }
                            if (!found) map.put(key, f);

                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        builder.append("\n\n");
                    }
                }
                //  DataPoint[] dpoints = new DataPoint[points.size()];
                DataPoint[] dpoints = new DataPoint[]{new DataPoint(new Date(), 0)};
                if (map.size() > 0) {
                    dpoints = new DataPoint[map.size()];
                    i = 0;
                    for (Date key : map.keySet()) {
                        dpoints[i] = new DataPoint(i, map.get(key));
                        i++;
                    }
                }
                LineGraphSeries<DataPoint> series = new LineGraphSeries<DataPoint>(dpoints);
                BarGraphSeries<DataPoint> series2 = new BarGraphSeries<DataPoint>(dpoints);
                //   graph2.getViewport().setXAxisBoundsManual(true);
                graph2.addSeries(series2);
                graph.addSeries(series);
                graph.getGridLabelRenderer().setNumHorizontalLabels(7);
                graph2.getGridLabelRenderer().setNumHorizontalLabels(7);
                //  graph.getGridLabelRenderer().setLabelFormatter(new DateAsXAxisLabelFormatter(MainActivity.this));
                //graph2.getGridLabelRenderer().setLabelFormatter(new DateAsXAxisLabelFormatter(MainActivity.this));
                graph.getViewport().setScrollable(true); // enables horizontal scrolling
                graph.getViewport().setScrollableY(true); // enables vertical scrolling
                graph.getViewport().setScalable(true); // enables horizontal zooming and scrolling
                graph.getViewport().setScalableY(true); // enables vertical zooming and scrolling
                graph2.getViewport().setScrollable(true); // enables horizontal scrolling
                graph2.getViewport().setScrollableY(true); // enables vertical scrolling
                graph2.getViewport().setScalable(true); // enables horizontal zooming and scrolling
                graph2.getViewport().setScalableY(true); // enables vertical zooming and scrolling
                //  graph.invalidate();
                // tvData.setText(tvData.getText() + builder.toString());

            }

            @Override
            public void onUnresolvableFailure(@NonNull Status status) {
                Log.e(TAG, status.getStatus() + " : " + status.getStatusMessage());
            }

        });


    }

    private void recordData() {
        Fitness.RecordingApi.subscribe(mClient, DataType.TYPE_DISTANCE_DELTA);
        Fitness.RecordingApi.subscribe(mClient, DataType.AGGREGATE_CALORIES_EXPENDED);
        Fitness.RecordingApi.subscribe(mClient, DataType.AGGREGATE_ACTIVITY_SUMMARY);
        Fitness.RecordingApi.subscribe(mClient, DataType.AGGREGATE_CALORIES_EXPENDED);
        Fitness.RecordingApi.subscribe(mClient, DataType.TYPE_CALORIES_EXPENDED);
        Fitness.RecordingApi.subscribe(mClient, DataType.TYPE_SPEED);
        Fitness.RecordingApi.subscribe(mClient, DataType.TYPE_ACTIVITY_SAMPLE)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(TAG, "Existing subscription for activity detected.");
                            } else {
                                Log.i(TAG, "Successfully subscribed!");
                            }
                        } else {
                            Log.i(TAG, "There was a problem subscribing.");
                        }
                    }
                });
    }

    List<OnDataPointListener> listeners = new ArrayList<>();

    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {
        OnDataPointListener listener = new OnDataPointListener() {
            @Override
            public void onDataPoint(com.google.android.gms.fitness.data.DataPoint dataPoint) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    Value val = dataPoint.getValue(field);
                    Log.i(TAG, "Detected DataPoint field: " + field.getName());
                    Log.i(TAG, "Detected DataPoint value: " + val);
                }
            }
        };
        Fitness.SensorsApi.add(mClient, new SensorRequest.Builder()
                .setDataSource(dataSource) // Optional but recommended for custom data sets.
                .setDataType(dataType) // Can't be omitted.
                .setSamplingRate(1, TimeUnit.DAYS)
                .build(), listener).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (status.isSuccess()) {
                    Log.i(TAG, "Listener registered!");
                } else {
                    Log.i(TAG, "Listener not registered.");
                }
            }
        });
        listeners.add(listener);
    }


    private void unregisterFitnessDataListener() {
        if (mListener == null) {
            // This code only activates one listener at a time.  If there's no listener, there's
            // nothing to unregister.
            return;
        }

        // [START unregister_data_listener]
        // Waiting isn't actually necessary as the unregister call will complete regardless,
        // even if called from within onStop, but a callback can still be added in order to
        // inspect the results.
        Fitness.SensorsApi.remove(
                mClient,
                mListener)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Listener was removed!");
                        } else {
                            Log.i(TAG, "Listener was not removed.");
                        }
                    }
                });
    }

    private void initializeLogging() {
        LogWrapper logWrapper = new LogWrapper();
        com.sindhura.healthtracker.logs.Log.setLogNode(logWrapper);
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
/*        LogView logView = (LogView) findViewById(R.id.sample_logview);

        logView.setBackgroundColor(Color.WHITE);
        msgFilter.setNext(logView);
        Log.i(TAG, "Ready");*/
    }

    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(
                    findViewById(R.id.main_activity_view), R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_PERMISSIONS_REQUEST_CODE);
                }
            }).show();
        } else {
            Log.i(TAG, "Requesting permission");
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted.
                buildFitnessClient();
            } else {
                Snackbar.make(
                        findViewById(R.id.main_activity_view),
                        "Sorry, permission wasn't granted",
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction("Settings", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Build intent that displays the App settings screen.
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package",
                                        BuildConfig.APPLICATION_ID, null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        }).show();
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        buildFitnessClient();
    }

}
