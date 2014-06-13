package com.megster.cordova;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
// kludgy imports to support 2.9 and 3.0 due to package changes
import org.apache.cordova.*;
import org.apache.cordova.api.*;
// import org.apache.cordova.CordovaArgs;
// import org.apache.cordova.CordovaPlugin;
// import org.apache.cordova.CallbackContext;
// import org.apache.cordova.PluginResult;
// import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/**
 * PhoneGap Plugin for Serial Communication over Bluetooth
 */
public class BluetoothSerial extends CordovaPlugin {

	//nwags variable
	public static double pos;
	public static double xmax=400.0;
	public static double ymax=200.0;
	public static double zmax=200.0;
	public static double amax=24.0;
	public static boolean power=true;
	
    // actions
    private static final String LIST = "list";
    private static final String CONNECT = "connect";
    private static final String CONNECT_INSECURE = "connectInsecure";
    private static final String DISCONNECT = "disconnect";
    private static final String WRITE = "write";
    private static final String AVAILABLE = "available";
    private static final String READ = "read";
    private static final String READ_UNTIL = "readUntil";
    private static final String SUBSCRIBE = "subscribe";
    private static final String UNSUBSCRIBE = "unsubscribe";
    private static final String IS_ENABLED = "isEnabled";
    private static final String IS_CONNECTED = "isConnected";
    private static final String CLEAR = "clear";
    
    //nwags-actions
    private static final String JOG = "Jog";
    private static final String JOG_SUBSCRIBE = "jogsubscribe";
    private static final String JOG_UNSUBSCRIBE = "jogunsubscribe";

    // callbacks
    private CallbackContext connectCallback;
    private CallbackContext dataAvailableCallback;
    
    //nwags-callbacks
    private CallbackContext jogDataCallback;
    
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSerialService bluetoothSerialService;

    // Debugging
    private static final String TAG = "BluetoothSerial";
    private static final boolean D = true;

    // Message types sent from the BluetoothSerialService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    StringBuffer buffer = new StringBuffer();
    private String delimiter;

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {

        LOG.d(TAG, "action = " + action);

        if (bluetoothAdapter == null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (bluetoothSerialService == null) {
            bluetoothSerialService = new BluetoothSerialService(mHandler);
        }

        boolean validAction = true;
        
        if (action.equals(LIST)) {

            listBondedDevices(callbackContext);

        } else if (action.equals(CONNECT)) {

            boolean secure = true;
            connect(args, secure, callbackContext);

        } else if (action.equals(CONNECT_INSECURE)) {

            // see Android docs about Insecure RFCOMM http://goo.gl/1mFjZY
            boolean secure = false;
            connect(args, false, callbackContext);

        } else if (action.equals(DISCONNECT)) {

            connectCallback = null;
            bluetoothSerialService.stop();
            callbackContext.success();

        } else if (action.equals(WRITE)) {

            String data = args.getString(0);
            bluetoothSerialService.write(data.getBytes());
            callbackContext.success();

        } else if (action.equals(AVAILABLE)) {

            callbackContext.success(available());

        } else if (action.equals(READ)) {

            callbackContext.success(read());

        } else if (action.equals(READ_UNTIL)) {

            String interesting = args.getString(0);
            callbackContext.success(readUntil(interesting));

        } else if (action.equals(SUBSCRIBE)) {

            delimiter = args.getString(0);
            dataAvailableCallback = callbackContext;

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else if (action.equals(UNSUBSCRIBE)) {

            delimiter = null;
            dataAvailableCallback = null;

            callbackContext.success();

        } else if (action.equals(IS_ENABLED)) {

            if (bluetoothAdapter.isEnabled()) {
                callbackContext.success();                
            } else {
                callbackContext.error("Bluetooth is disabled.");
            }            

        } else if (action.equals(IS_CONNECTED)) {
            
            if (bluetoothSerialService.getState() == BluetoothSerialService.STATE_CONNECTED) {
                callbackContext.success();                
            } else {
                callbackContext.error("Not connected.");
            }

        } else if (action.equals(CLEAR)) {

            buffer.setLength(0);
            callbackContext.success();

        } else if (action.equals(JOG)) { //nwags-action
        	
        	jog(args, callbackContext);
        	
        } else if (action.equals(JOG_SUBSCRIBE)) { //nwags-action
        	jogDataCallback = callbackContext;
        	
        	PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
        } else if (action.equals(JOG_UNSUBSCRIBE)) { //nwags-action
        	jogDataCallback = null;
            callbackContext.success();
        } else {

            validAction = false;

        }

        return validAction;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bluetoothSerialService != null) {
            bluetoothSerialService.stop();
        }
    }

    private void listBondedDevices(CallbackContext callbackContext) throws JSONException {
        JSONArray deviceList = new JSONArray();
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : bondedDevices) {
            JSONObject json = new JSONObject();
            json.put("name", device.getName());
            json.put("address", device.getAddress());
            json.put("id", device.getAddress());
            if (device.getBluetoothClass() != null) {
                json.put("class", device.getBluetoothClass().getDeviceClass());
            }
            deviceList.put(json);
        }
        callbackContext.success(deviceList);
    }

    private void connect(CordovaArgs args, boolean secure, CallbackContext callbackContext) throws JSONException {
        String macAddress = args.getString(0);
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);

        if (device != null) {
            connectCallback = callbackContext;
            bluetoothSerialService.connect(device, secure);

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else {
            callbackContext.error("Could not connect to " + macAddress);
        }
    }

    // The Handler that gets information back from the BluetoothSerialService
    // Original code used handler for the because it was talking to the UI.
    // Consider replacing with normal callbacks
    private final Handler mHandler = new Handler() {

         public void handleMessage(Message msg) {
             switch (msg.what) {
                 case MESSAGE_READ:
                    buffer.append((String)msg.obj);
                    
                    if (dataAvailableCallback != null) {
                        sendDataToSubscriber();
                    }
                    
                    if(jogDataCallback != null){ //nwags
                    	sendJogDataToSubscriber();
                    }
                    break;
                 case MESSAGE_STATE_CHANGE:

                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothSerialService.STATE_CONNECTED:
                            Log.i(TAG, "BluetoothSerialService.STATE_CONNECTED");
                            notifyConnectionSuccess();
                            break;
                        case BluetoothSerialService.STATE_CONNECTING:
                            Log.i(TAG, "BluetoothSerialService.STATE_CONNECTING");
                            break;
                        case BluetoothSerialService.STATE_LISTEN:
                            Log.i(TAG, "BluetoothSerialService.STATE_LISTEN");
                            break;
                        case BluetoothSerialService.STATE_NONE:
                            Log.i(TAG, "BluetoothSerialService.STATE_NONE");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    //  byte[] writeBuf = (byte[]) msg.obj;
                    //  String writeMessage = new String(writeBuf);
                    //  Log.i(TAG, "Wrote: " + writeMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    Log.i(TAG, msg.getData().getString(DEVICE_NAME));
                    break;
                case MESSAGE_TOAST:
                    String message = msg.getData().getString(TOAST);
                    notifyConnectionLost(message);
                    break;
             }
         }
    };

    private void notifyConnectionLost(String error) {
        if (connectCallback != null) {
            connectCallback.error(error);
            connectCallback = null;
        }
    }

    private void notifyConnectionSuccess() {
        if (connectCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(true);
            connectCallback.sendPluginResult(result);
        }
    }

    private void sendDataToSubscriber() {
        String data = readUntil(delimiter);
        if (data != null && data.length() > 0) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
            result.setKeepCallback(true);
            dataAvailableCallback.sendPluginResult(result);

            sendDataToSubscriber();
        }
    }

    private int available() {
        return buffer.length();
    }

    private String read() {
        int length = buffer.length();
        String data = buffer.substring(0, length);
        buffer.delete(0, length);
        return data;
    }

    private String readUntil(String c) {
        String data = "";
        int index = buffer.indexOf(c, 0);
        if (index > -1) {
            data = buffer.substring(0, index + c.length());
            buffer.delete(0, index + c.length());
        }
        return data;
    }

    //nwags private methods
    private void sendJogDataToSubscriber() {
    	LOG.d(TAG, "sendJogDataToSubscriber called");
    	String data = readUntil("\n");
    	String jsonStr = "";
    	if(data != null && data.length() > 0){
    		try {
    			
    			JSONObject json = new JSONObject(data);
    			if (json.has("r")) {
    				jsonStr = processBody(json.getJSONObject("r"));
    			}else if (json.has("sr")) {
    				jsonStr = processStatusReport(json.getJSONObject("sr"));
    			}
    			
    		} catch(Exception e){
    			if(e.getMessage()!=null)
    				Log.e(TAG, e.getMessage());
    			
    		}
    		
    		PluginResult result = new PluginResult(PluginResult.Status.OK, jsonStr);
            result.setKeepCallback(true);
            jogDataCallback.sendPluginResult(result);

            sendJogDataToSubscriber();
    	}
    }
    
    private String processBody(JSONObject json) throws JSONException {
    	LOG.d(TAG, "processBody called");
    	String result = "";
    	if(json.has("sr"))
    		result = processStatusReport(json.getJSONObject("sr"));
    	return result;
    }
    
    private String processStatusReport(JSONObject sr) throws JSONException{
    	LOG.d(TAG, "processStatusReport called");
    	String result = "";
    	JSONObject jResult = new JSONObject();
    	if (sr.has("posx"))
    		pos = sr.getDouble("posx");
    		pos = pos/xmax;
    		jResult.put("x", pos);
		if (sr.has("posy"))
			pos = sr.getDouble("posy");
			pos = pos/ymax;
			jResult.put("y", pos);
		if (sr.has("posz"))
			pos = sr.getDouble("posz");
			pos = pos/zmax;
			jResult.put("z", pos);
		if (sr.has("posa"))
			pos = sr.getDouble("posa");
			pos = pos/amax;
			jResult.put("a", pos);
		if (sr.has("stat")){
			switch (sr.getInt("stat")){
			case 0:
				jResult.put("listening", 0);
				break;
			case 1:
				jResult.put("listening", 1);
				break;
			case 2:
				jResult.put("listening", 0);
				break;
			case 3:
				jResult.put("listening", 1);
				break;
			case 4:
				jResult.put("listening", 0);
				break;
			case 5:
				jResult.put("listening", 0);
				break;
			case 6:
				jResult.put("listening", 0);
				break;
			case 7:
				jResult.put("listening", 0);
				break;
			case 8:
				jResult.put("listening", 0);
				break;
			case 9:
				jResult.put("listening", 0);
				break;
			}
		}
		result = jResult.toString();
		LOG.d(TAG, "result: "+result);
    	return result;
    }
    
    private void jog(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    	JSONObject jsonObj = args.getJSONObject(0);
    	
    	if(jsonObj.has("reset")) {
    		LOG.d(TAG, "reset");
    		byte[] rst = {0x18};
    		bluetoothSerialService.write(rst);
            callbackContext.success();
            return;
    	}
    	if(jsonObj.has("stop")) {
    		LOG.d(TAG, "stop");
    		bluetoothSerialService.write("!%\n".getBytes());
            callbackContext.success();
            return;
    	}
    	if(jsonObj.has("power")) {
    		LOG.d(TAG, "power");
    		if(jsonObj.getBoolean("power")){
    			bluetoothSerialService.write("$me\n".getBytes());
    			bluetoothSerialService.write("{\"1pm\":\"1\"}\n".getBytes());
    			bluetoothSerialService.write("{\"2pm\":\"1\"}\n".getBytes());
    			bluetoothSerialService.write("{\"3pm\":\"1\"}\n".getBytes());
    			bluetoothSerialService.write("{\"4pm\":\"1\"}\n".getBytes());
    			power = true;
                callbackContext.success();
                
    		}else{
    			bluetoothSerialService.write("$md\n".getBytes());
    			power = false;
                callbackContext.success();
    		}
    	}
    	double gogo = 0.0;
    	String gogoStr = "";
    	String gocode = "{\"gc\":\"g90 g0 ";//\"}\n";
    	if(jsonObj.has("x")) {
    		gogo = jsonObj.getDouble("x")*xmax;
    		gogoStr = "x"+String.valueOf(gogo);
    		gocode+=gogoStr;
    	}
    	if(jsonObj.has("y")) {
    		gogo = jsonObj.getDouble("y")*ymax;
    		gogoStr = "y"+String.valueOf(gogo);
    		gocode+=gogoStr;
    	}
    	if(jsonObj.has("z")) {
    		gogo = jsonObj.getDouble("z")*zmax;
    		gogoStr = "z"+String.valueOf(gogo);
    		gocode+=gogoStr;
    	}
    	if(jsonObj.has("a")) {
    		gogo = jsonObj.getDouble("a")*amax;
    		gogoStr = "a"+String.valueOf(gogo);
    		gocode+=gogoStr;
    	}
    	gocode+="\"}\n";
    	bluetoothSerialService.write(gocode.getBytes());
        callbackContext.success();
    }

}
