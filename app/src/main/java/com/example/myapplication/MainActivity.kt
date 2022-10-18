package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import cn.wch.ch34xuartdriver.CH34xUARTDriver
import java.lang.Thread.sleep
import kotlin.math.asin
import kotlin.math.atan2

class MainActivity : AppCompatActivity() {
    class MyListener(val callback:(listen:MyListener)->Unit) : SensorEventListener{
        public var roll:Float = 0.0f
        public var pitch:Float = 0.0f
        public var yaw:Float = 0.0f
        var Q:FloatArray=FloatArray(4)
        var R:FloatArray= FloatArray(9)

        override fun onSensorChanged(event: SensorEvent?) {
            var test:FloatArray= FloatArray(3)
            if (event != null) {
                SensorManager.getQuaternionFromVector(Q,event.values)
                SensorManager.getRotationMatrixFromVector(R,event.values)
                SensorManager.getOrientation(R,test)
                //Q2Angle(Q)
                test=test.map { it * 180.0f/3.14159f }.toFloatArray()
                yaw=test[0]
                roll=test[1]
                pitch=test[2]
                callback(this)
                //println("roll $roll   pitch:$pitch")
            }
        }

        fun Q2Angle(q:FloatArray){
           pitch= asin(-1*q[1]*q[3] + 2*q[0]*q[2])* 57.3f;           //pitch
           roll = atan2(2*q[2]*q[3] + 2*q[0]*q[1], -2*q[1]*q[1] - 2*q[2]*q[2]+1)* 57.3f;   //roll
           yaw = atan2(2*q[1]*q[2] + 2*q[0]*q[3], -2*q[2]*q[2] - 2*q[3]*q[3]+1)* 57.3f;         //yaw

        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }
    }

    private lateinit var serialDriver:CH34xUARTDriver
    private var serialOpened=false

    private lateinit var bytes:ByteArray
    private val crsfData:CRSFData= CRSFData()

    private var leftJoyStickX:Float=0f
    private var leftJoyStickY:Float=0f
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        sensorManager.registerListener(MyListener(this::sensorCallBack), sensor, 100000);
        serialDriver=CH34xUARTDriver(getSystemService(USB_SERVICE) as UsbManager, this,"cn.wch.wchusbdriver.USB_PERMISSION")

        //bytes=getTestByteArray("C8 18 16 E0 03 1F 2B C0 F7 8B 5F FC E2 17 E5 2B 5F F9 CA 07 00 00 44 3C E2 B8")
        val tableLayoutView=findViewById<TableLayout>(R.id.tablelayout)
        val joyStick = findViewById<Joystick>(R.id.leftJoystick)
        joyStick.setOnJoystickMoveListener(
            object:OnJoystickMoveListener{
                override fun onJoystickValueChanged(x: Float, y: Float) {
                    leftJoyStickX=x
                    leftJoyStickY=y
                    crsfData.data_array[0]=((leftJoyStickX/2+0.5) * 2047).toInt()
                    crsfData.data_array[1]=((leftJoyStickY/2+0.5) * 2047).toInt()
                }
            },10)

        val test=TextView(this)
        test.setText("BV")
        (tableLayoutView[3] as TableRow).addView(test)

        for (i in 1 .. 16){
            crsfData.data_array[i-1]=i
        }
        bytes=crsfData.pack().toByteArray()
        for(i in bytes){
            print(String.format("%02X,", i))
        }
        println("")
    }

    private fun debugInfo(str: String) = AlertDialog.Builder(this).setMessage(str).setTitle(getString(R.string.app_name)).create().show()

    fun getTestByteArray(str:String):ByteArray{
        val arr=str.split(" ")
        var result=ByteArray(26)
        for ((cnt, i) in arr.withIndex()){
            result[cnt]=i.toUByte(16).toByte()
        }
        return result
    }

    private fun openUartDevice():Boolean{
        val ret=serialDriver.ResumeUsbList()
        if(ret == -1 ){
            debugInfo("No Uart Device!")
            return false
        }

        if(!serialDriver.UartInit()){
            debugInfo("Fail to Open Uart Device!")
            return false
        }
        val config_ret=serialDriver.SetConfig(460800,8,1,0,0)
        debugInfo("config ret:$config_ret")
        return true
    }

    private fun uartWrite(bytearr:ByteArray,len:Int){
        if(!serialOpened){
            return
        }

        val ret = serialDriver.WriteData(bytearr,len)
        if(ret<0){
           debugInfo("Uart device disconnected!")
            serialOpened=false
            findViewById<Button>(R.id.openSerialButton).isEnabled = true
            try {
                serialDriver.CloseDevice()
            }catch (e:java.lang.Exception){
                debugInfo(e.toString())
            }
        }
    }

    /** Called when the user taps the Send button */
    fun openSerial(view: View) {
        if(!openUartDevice()){
            return
        }
        serialOpened=true
        findViewById<Button>(R.id.openSerialButton).isEnabled = false
    }

    fun sensorCallBack(listen:MyListener){
        findViewById<TextView>(R.id.rollValText).text = listen.roll.toString()
        findViewById<TextView>(R.id.pitchValText).text = listen.pitch.toString()
        findViewById<TextView>(R.id.yawValText).text = listen.yaw.toString()

        val channel_text=crsfData.data_array.map { "$it" }.joinToString("  ") + "\n $leftJoyStickX "+ " $leftJoyStickY "
        findViewById<TextView>(R.id.testView).text=channel_text

        if(serialOpened){
            bytes=crsfData.pack().toByteArray()
            uartWrite(bytes,26)
        }
    }
}