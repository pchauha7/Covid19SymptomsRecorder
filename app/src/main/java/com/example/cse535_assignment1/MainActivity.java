package com.example.cse535_assignment1;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ID = 1;
    private String heartRate = "0";
    private String respiratoryRate = "0";
    private HashMap<String, String> symptomsHashMap;
    DatabaseHelper myDB;

    private final VideoService cameraService = new VideoService(this);
    private EntryStorage store;

    private final int measurementInterval = 45;
    private final int measurementLength = 50000;
    private final int clipLength = 3500;

    private int detectedValleys = 0;
    private int ticksPassed = 0;

    private CopyOnWriteArrayList<Long> valleys;

    private CountDownTimer timer;

    private SensorManager accelManage;
    private Sensor senseAccel;
    final private int MAX_COUNT = 220;
    float accelValuesX[] = new float[MAX_COUNT];
    float accelValuesY[] = new float[MAX_COUNT];
    float accelValuesZ[] = new float[MAX_COUNT];
    long time1;
    long time2;
    int total_time = 0;
    int index =0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //Initialize DB instance
        myDB = new DatabaseHelper(this);

        initialize_HashMap();

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_ID);

        //canging view from MainActivity to Symptoms Activity
        Button button_symptoms = (Button) findViewById(R.id.button_symptoms);
        button_symptoms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSymptomsView();
            }
        });



        //receiving and processing the symptoms from Symptoms Activity
        Intent intent = getIntent();
        String symptomsString = (String) intent.getStringExtra("EXTRA_TEXT");
        if(symptomsString != null){
            symptomsString = symptomsString.substring(1);
            symptomsString = symptomsString.substring(0, symptomsString.length() - 1);
            Log.d(TAG, "symptoms"+ symptomsString);
            String[] pairs = symptomsString.split(",");
            for (int i=0;i<pairs.length;i++) {
                String pair = pairs[i].trim();
                String[] keyValue = pair.split("=");
                symptomsHashMap.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }

        Button heartbeatBtn = (Button) findViewById(R.id.button_id_3);


        if(!hasCamera()){
            heartbeatBtn.setEnabled(false);
        }

        final String finalSymptomsString1 = symptomsString;
        heartbeatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(finalSymptomsString1 != null){

                    startCameraService();
                }
                else {
                    Toast.makeText(getApplicationContext(), "Please record other Symptoms first", Toast.LENGTH_LONG).show();
                }

            }
        });

        Button resp_button = (Button) findViewById(R.id.button_id_4);
        resp_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(finalSymptomsString1 != null){
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    ((TextView) findViewById(R.id.text_id2)).setText("Calculating...");
                    //Toast.makeText(MainActivity.this, "Service Started " , Toast.LENGTH_LONG).show();
                    accelManage = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                    senseAccel = accelManage.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                    accelManage.registerListener(MainActivity.this, senseAccel, SensorManager.SENSOR_DELAY_NORMAL);


                }
                else {
                    Toast.makeText(getApplicationContext(), "Please record other Symptoms first", Toast.LENGTH_LONG).show();
                }

            }
        });


        Button upload_signs_button = (Button) findViewById(R.id.upload_signs_button);
        final String finalSymptomsString = symptomsString;
        upload_signs_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(finalSymptomsString !=null){
                    symptomsHashMap.put("Heart Rate", heartRate);
                    symptomsHashMap.put("Respiratory Rate", respiratoryRate);
                    boolean flag = myDB.insertData(symptomsHashMap);
                    if (flag)
                        Toast.makeText(getApplicationContext(), "Successfully inserted data", Toast.LENGTH_LONG).show();
                }

                Intent intent = new Intent(MainActivity.this, MainActivity.class);
                startActivity(intent);

            }
        });

    }

    private void initialize_HashMap(){
        symptomsHashMap = new HashMap<>();
        symptomsHashMap.put("Heart Rate","");
        symptomsHashMap.put("Respiratory Rate","");
        symptomsHashMap.put("Nausea", "");
        symptomsHashMap.put("Headache", "");
        symptomsHashMap.put("Diarrhoea", "");
        symptomsHashMap.put("Soar Throat", "");
        symptomsHashMap.put("Fever", "");
        symptomsHashMap.put("Muscle Ache", "");
        symptomsHashMap.put("Loss of smell or taste", "");
        symptomsHashMap.put("Cough", "");
        symptomsHashMap.put("Shortness of breath", "");
        symptomsHashMap.put("Feeling Tired", "");
    }
    private boolean hasCamera() {
        if (getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY)){
            return true;
        } else {
            return false;
        }
    }

    public void openSymptomsView(){
        Intent intent = new Intent(this, Symptoms.class);
        startActivity(intent);
    }



    private boolean detectValley() {
        final int valleyDetectionWindowSize = 13;
        CopyOnWriteArrayList<DataPoint<Integer>> subList = store.getLastStdValues(valleyDetectionWindowSize);
        if (subList.size() < valleyDetectionWindowSize) {
            return false;
        } else {
            Integer referenceValue = subList.get((int) Math.ceil(valleyDetectionWindowSize / 2)).measurement;

            for (DataPoint<Integer> measurement : subList) {
                if (measurement.measurement < referenceValue) return false;
            }

            // filter out consecutive measurements due to too high measurement rate
            return (!subList.get((int) Math.ceil(valleyDetectionWindowSize / 2)).measurement.equals(
                    subList.get((int) Math.ceil(valleyDetectionWindowSize / 2) - 1).measurement));
        }
    }

    void measurePulse(final TextureView textureView, final VideoService cameraService) {

        // 20 times a second, get the amount of red on the picture.
        // detect local minimums, calculate pulse.

        store = new EntryStorage();

        detectedValleys = 0;

        timer = new CountDownTimer(measurementLength, measurementInterval) {
            @Override
            public void onTick(long millisUntilFinished) {
                // skip the first measurements, which are broken by exposure metering
                if (clipLength > (++ticksPassed * measurementInterval)) return;

                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run(){
                        Bitmap currentBitmap = textureView.getBitmap();
                        int pixelCount = textureView.getWidth() * textureView.getHeight();
                        int measurement = 0;
                        int[] pixels = new int[pixelCount];

                        currentBitmap.getPixels(pixels, 0, textureView.getWidth(), 0, 0, textureView.getWidth(), textureView.getHeight());

                        // extract the red component
                        // https://developer.android.com/reference/android/graphics/Color.html#decoding
                        for (int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
                            measurement += (pixels[pixelIndex] >> 16) & 0xff;
                        }
                        // max int is 2^31 (2147483647) , so width and height can be at most 2^11,
                        // as 2^8 * 2^11 * 2^11 = 2^30, just below the limit

                        store.add(measurement);

                        if (detectValley()) {
                            detectedValleys = detectedValleys + 1;
                            valleys.add(store.getLastTimestamp().getTime());
                        }
                    }
                });
                thread.start();
            }

            @Override
            public void onFinish() {
                CopyOnWriteArrayList<DataPoint<Float>> stdValues = store.getStdValues();

                float heartbeat = (60f * (detectedValleys - 1) / (Math.max(1, (valleys.get(valleys.size() - 1) - valleys.get(0)) / 1000f)));
                int heart_beat = Math.round(heartbeat);
                heartRate = String.valueOf(heart_beat);
                // clip the interval to the first till the last one - on this interval, there were detectedValleys - 1 periods
                String currentValue = String.format( Locale.getDefault(),"HEART_BEAT: " + heartRate);
                ((TextView) findViewById(R.id.text_id)).setText(currentValue);

                cameraService.stop();
            }
        };

        timer.start();
    }

    void stop() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private void startCameraService() {

        ((TextView) findViewById(R.id.text_id)).setText("Calculating...");

        valleys = new CopyOnWriteArrayList<>();

        TextureView cameraTextureView = findViewById(R.id.textureView2);

        SurfaceTexture previewSurfaceTexture = cameraTextureView.getSurfaceTexture();
        if (previewSurfaceTexture != null) {
            // this first appears when we close the application and switch back - TextureView isn't quite ready at the first onResume.
            Surface previewSurface = new Surface(previewSurfaceTexture);

            cameraService.start(previewSurface);
            measurePulse(cameraTextureView, cameraService);
        }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor mySensor = event.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (index==0)
                time1 = System.currentTimeMillis();
            index++;
            accelValuesX[index] = event.values[0];
            accelValuesY[index] = event.values[1];
            accelValuesZ[index] = event.values[2];
            if(index >= MAX_COUNT-1){
                time2 = System.currentTimeMillis();
                index = 0;
                total_time = (int) ((time2-time1)/1000);
                accelManage.unregisterListener(this);

                int breathRate = calculateHeartRate();

                respiratoryRate = String.valueOf(breathRate);
                Toast.makeText(getApplicationContext(), "BREATH_RATE: " + respiratoryRate, Toast.LENGTH_LONG).show();

                String currentValue = String.format( Locale.getDefault(),"RESPIRATORY_RATE: " + respiratoryRate);
                ((TextView) findViewById(R.id.text_id2)).setText(currentValue);


            }
        }
    }

    public int calculateHeartRate(){
        if (total_time==0)
            return 0;
        float xSum=0, ySum=0, zSum=0;
        for (int i =0; i<MAX_COUNT; i++){
            xSum += accelValuesX[i];
            ySum += accelValuesY[i];
            zSum += accelValuesZ[i];
        }
        float xAvg = xSum/MAX_COUNT, yAvg = ySum/MAX_COUNT, zAvg = zSum/MAX_COUNT;
        int xCount=0, yCount=0, zCount =0;
        for (int i =1; i<MAX_COUNT; i++){
            if ((accelValuesX[i-1] <= xAvg && xAvg<= accelValuesX[i]) || (accelValuesX[i-1] >= xAvg && xAvg>= accelValuesX[i]))
                xCount++;
            if ((accelValuesY[i-1] <= yAvg && yAvg<= accelValuesY[i]) || (accelValuesY[i-1] >= yAvg && yAvg>= accelValuesY[i]))
                yCount++;
            if ((accelValuesZ[i-1] <= zAvg && zAvg<= accelValuesZ[i]) || (accelValuesZ[i-1] >= zAvg && zAvg>= accelValuesZ[i]))
                zCount++;
        }
        int max_count = Math.max(xCount, Math.max(yCount, zCount));

        return max_count*30/total_time;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
