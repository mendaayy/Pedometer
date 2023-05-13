package com.example.stepcounter

import android.content.Context
import android.widget.Button
import java.util.Calendar
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(), SensorEventListener{
    private var magnitudePreviousStep = 0.0
    private var stepsThisMorning: Int = 0
    private var previousTotalSteps = 0f
    private var totalSteps = 0f
    private var running: Boolean = false
    private var sensorManager: SensorManager? = null
    private val ACTIVITY_RECOGNITION_REQUEST_CODE: Int = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Check if permission is granted for activity recognition

        if (isPermissionGranted()){
            requestPermission()
        }


        // Loading saved data and resetting step counter
        loadData()
        resetSteps()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

    }

    //button function for resetting step count
    private fun resetSteps() {
        val stepTaken = findViewById<TextView>(R.id.step_current)
        val stepMorning = findViewById<TextView>(R.id.step_morning)
        val resetButton = findViewById<Button>(R.id.reset_button)
        val cirbar = findViewById<CircularProgressBar>(R.id.progress_circular)


        resetButton.setOnClickListener {
            previousTotalSteps = totalSteps
            stepsThisMorning = 0
            stepMorning.text = "Steps Taken This morning: 0"
            stepTaken.text = "0"
            cirbar.setProgressWithAnimation(0f)
            saveData()
            saveMorningSteps()
        }

    }

    //function that saves the current step count
    private fun saveData() {
        val sharedPreferences: SharedPreferences = getSharedPreferences("step",
            Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat("currentstep", previousTotalSteps)
        editor.apply()
    }


    //function that saves the morning step count
    private fun saveMorningSteps() {
        val sharedPreferences: SharedPreferences = getSharedPreferences("step",
            Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("morningsteps", stepsThisMorning)
        editor.apply()
    }


    //function that loads saved step count
    private fun loadData() {
        val sharedPreferences: SharedPreferences = getSharedPreferences("step", Context.MODE_PRIVATE)
        previousTotalSteps = sharedPreferences.getFloat("currentstep", previousTotalSteps)
        stepsThisMorning = sharedPreferences.getInt("morningsteps", stepsThisMorning)
    }


    override fun onSensorChanged(event: SensorEvent?) {
        var steptaken = findViewById<TextView>(R.id.step_current)
        var cirbar = findViewById<CircularProgressBar>(R.id.progress_circular)
        var stepMorning = findViewById<TextView>(R.id.step_morning)


        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // if android step counter is present
        if (event!!.sensor.type == Sensor.TYPE_STEP_COUNTER){

            totalSteps = event!!.values[0]
            val currentSteps = (totalSteps - previousTotalSteps).toInt()
            steptaken.text = currentSteps.toString()

            cirbar.apply {
                setProgressWithAnimation(currentSteps.toFloat())
            }

            // only updates steps morning if condition hour satisfied
            if (currentHour in 15..17) {
                stepsThisMorning = currentSteps
                saveMorningSteps()
            }

            stepMorning.text = "Steps Taken This morning: $stepsThisMorning"

        // if samsung sensor is present
        } else if (event!!.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            //magnitude detection
            val xaccel: Float = event.values[0]
            val yaccel: Float = event.values[1]
            val zaccel: Float = event.values[2]
            val magnitude: Double = sqrt((xaccel * xaccel + yaccel * yaccel + zaccel * zaccel).toDouble())

            var magnitudeDelta: Double = magnitude - magnitudePreviousStep
            magnitudePreviousStep = magnitude

            // check if magnitudeDelta is greater than 6
            // if it is, increment totalSteps
            if (magnitudeDelta > 1){
                totalSteps++

                if (currentHour in 15..17) {
                    stepsThisMorning++
                    stepMorning.text = "Steps Taken This morning: $stepsThisMorning"
                    saveMorningSteps()
                }

            }
            val step: Int = totalSteps.toInt()
            steptaken.text = step.toString()


            // set the progress bar to animate to the current step count
            cirbar.apply {
                setProgressWithAnimation(step.toFloat())
            }

        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        //
    }


    //register movement from sensor
    override fun onResume() {
        super.onResume()

        running = true

        //Sensor Manager for phone compatibility
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        //Recent Android Phones Sensor
        val countSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        //ETC
        val detectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        //Samsung Sensor
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        when {
            countSensor != null -> {
                sensorManager.registerListener(this, countSensor, SensorManager.SENSOR_DELAY_UI)
            }
            detectorSensor != null -> {
                sensorManager.registerListener(this, detectorSensor, SensorManager.SENSOR_DELAY_UI)
            }
            accelerometer != null -> {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }
            else -> {
                Toast.makeText(this, "Device is not Compatible", Toast.LENGTH_LONG).show()
            }
        }
    }

    //register no movement from sensor
    override fun onPause() {
        super.onPause()
        running = false
        sensorManager?.unregisterListener(this)
    }


    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION),
                ACTIVITY_RECOGNITION_REQUEST_CODE)
        }
    }

    private fun isPermissionGranted(): Boolean {
        //Check if permission is enabled
        return ContextCompat.checkSelfPermission(this,
            android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            ACTIVITY_RECOGNITION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)){
                    //Continue with code after permission is granted
                }
            }
        }
    }

}