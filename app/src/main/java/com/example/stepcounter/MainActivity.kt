package com.example.stepcounter

import android.content.Context
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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import java.util.Calendar
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {
    private var magnitudePreviousStep = 0.0
    private var stepsMorning: Int = 0
    private var prevSteps = 0f
    private var totalSteps = 0f
    private var isRunning: Boolean = false
    private var sensorManager: SensorManager? = null
    private val ACTIVITY_RECOGNITION_REQUEST_CODE: Int = 100

    // Views
    private lateinit var stepCurrent: TextView
    private lateinit var stepMorning: TextView
    private lateinit var stepReboot: TextView
    private lateinit var resetButton: Button
    private lateinit var progressBar: CircularProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()

        // Permission for activity recognition
        if (isPermissionGranted()) {
            requestPermission()
        }

        loadData()
        btnResetClicked()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // Function to initialize views from XML layout
    private fun initializeViews() {
        stepCurrent = findViewById(R.id.step_current)
        stepMorning = findViewById(R.id.step_morning)
        stepReboot = findViewById(R.id.step_since_reboot)
        resetButton = findViewById(R.id.btn_reset)
        progressBar = findViewById(R.id.progress_circular)
    }

    // Function for step count reset
    private fun btnResetClicked() {
        resetButton.setOnClickListener {
            prevSteps = totalSteps
            stepsMorning = 0
            stepMorning.text = "Steps Taken This morning: 0"
            stepCurrent.text = "0"
            progressBar.setProgressWithAnimation(0f)
            saveData()
            saveMorningSteps()
        }
    }

    // Function to save current step count
    private fun saveData() {
        val sharedPreferences: SharedPreferences = getSharedPreferences(
            "step",
            Context.MODE_PRIVATE
        )
        val editor = sharedPreferences.edit()
        editor.putFloat("currentstep", prevSteps)
        editor.apply()
    }

    // Function to save morning step count
    private fun saveMorningSteps() {
        val sharedPreferences: SharedPreferences = getSharedPreferences(
            "step",
            Context.MODE_PRIVATE
        )
        val editor = sharedPreferences.edit()
        editor.putInt("morningsteps", stepsMorning)
        editor.apply()
    }

    // Function to load saved data
    private fun loadData() {
        val sharedPreferences: SharedPreferences = getSharedPreferences(
            "step",
            Context.MODE_PRIVATE
        )
        prevSteps = sharedPreferences.getFloat("currentstep", prevSteps)
        stepsMorning = sharedPreferences.getInt("morningsteps", stepsMorning)
    }

    // Method to handle sensor events
    override fun onSensorChanged(event: SensorEvent?) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (event != null) {
            when (event.sensor.type) {
                // Android sensor: step counter
                Sensor.TYPE_STEP_COUNTER -> handleStepCounterSensorEvent(event, currentHour)
                // Samsung sensor: accelorometer
                Sensor.TYPE_ACCELEROMETER -> handleAccelerometerSensorEvent(event, currentHour)
                else -> {
                    // Unsupported sensor type
                    Log.d("MainActivity", "Unsupported sensor type")
                }
            }
        }
    }

    // Function to handle step counter sensor events
    private fun handleStepCounterSensorEvent(event: SensorEvent, currentHour: Int) {
        // Calculate current steps
        totalSteps = event.values[0]
        val currentSteps = (totalSteps - prevSteps).toInt()
        updateStepCountViews(currentSteps)
        animateProgressBar(currentSteps.toFloat())

        // Only updates steps morning if condition hour satisfied
        if (currentHour in 15..17) {
            stepsMorning = currentSteps
            saveMorningSteps()
        }
        stepMorning.text = "Steps Taken This morning: $stepsMorning"
    }

    // Function to handle accelerometer sensor events
    private fun handleAccelerometerSensorEvent(event: SensorEvent, currentHour: Int) {
        val xaccel: Float = event.values[0]
        val yaccel: Float = event.values[1]
        val zaccel: Float = event.values[2]
        val magnitude: Double = sqrt((xaccel * xaccel + yaccel * yaccel + zaccel * zaccel).toDouble())

        var magnitudeDelta: Double = magnitude - magnitudePreviousStep
        magnitudePreviousStep = magnitude

        // if greater than treshold of 1 = 1 step
        if (magnitudeDelta > 1) {
            totalSteps++

            if (currentHour in 15..17) {
                stepsMorning++
                saveMorningSteps()
            }
            stepMorning.text = "Steps Taken This morning: $stepsMorning"
        }

        val step: Int = totalSteps.toInt()
        updateStepCountViews(step)
        animateProgressBar(step.toFloat())
    }

    // Function to update step count views
    private fun updateStepCountViews(steps: Int) {
        // Set current step count
        stepCurrent.text = steps.toString()

        // Set since last reboot step count
        val stepsReboot = totalSteps.toInt()
        stepReboot.text =  "Steps Taken since last reboot: $stepsReboot"
    }

    // Function to animate progress bar
    private fun animateProgressBar(steps: Float) {
        progressBar.setProgressWithAnimation(steps)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do nothing
    }

    override fun onResume() {
        super.onResume()
        isRunning = true
        registerSensors()
    }

    override fun onPause() {
        super.onPause()
        isRunning = false
        sensorManager?.unregisterListener(this)
    }

    // Function to register sensors for listening
    private fun registerSensors() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        when {
            stepCounterSensor != null -> {
                sensorManager.registerListener(
                    this,
                    stepCounterSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
            accelerometerSensor != null -> {
                sensorManager.registerListener(
                    this,
                    accelerometerSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
            else -> {
                Toast.makeText(this, "Device is not Compatible", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Function to request activity recognition permission
    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION),
                ACTIVITY_RECOGNITION_REQUEST_CODE
            )
        }
    }

    private fun isPermissionGranted(): Boolean {
        //Check if permission is enabled
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        ) != PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            ACTIVITY_RECOGNITION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                } else {
                    // Permission denied
                    Toast.makeText(
                        this,
                        "Activity recognition permission denied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}


