package net.havenith.geoshot;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

 public class BluetoothConnection {
		private BluetoothDevice connectedDevice = null;
		private BluetoothSocket connectedSocket = null;
		private volatile OutputStream    outputstream    = null;
		private HandlerThread handlerThread;
		private Handler locationUpdateHandler;
		protected static final int MSG_WRITE   = 1;
		protected static final int MSG_CONNECT = 2;

		
		private static final String UUID_SERIAL = "00001101-0000-1000-8000-00805F9B34FB";
		
		@SuppressLint("HandlerLeak")
        public void connect( BluetoothDevice device)
		{
			connectedDevice  = device;
            handlerThread = new HandlerThread( "bluetooth comms thread");
            handlerThread.start();
            locationUpdateHandler = new Handler( handlerThread.getLooper()) {
                public void handleMessage( Message msg) {
                    if (msg.what == MSG_WRITE && outputstream != null) {
                        try {
                            outputstream.write( (byte[])msg.obj);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    else if (msg.what == MSG_CONNECT) {
                        async_connect();
                    }
                }
            };
			try {
				connectedSocket = device.createRfcommSocketToServiceRecord( UUID.fromString(UUID_SERIAL));
	              Message msg = Message.obtain();
	              msg.what = MSG_CONNECT;
	              locationUpdateHandler.sendMessage(msg);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public void write( byte [] message) {
		    Message msg = Message.obtain();
		    msg.what = MSG_WRITE;
		    msg.obj = message;
		    if (locationUpdateHandler != null) {
		        locationUpdateHandler.sendMessage( msg);
		    }
		}
		
		public void close()
		{
			outputstream = null;
			if (handlerThread != null) {
				handlerThread.quit();
				handlerThread = null;
			}
			try {
				if (connectedSocket != null)
				{
					connectedSocket.close();
					connectedSocket = null;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		private void async_connect()
		{
	        try {
	            // Connect the device through the socket. This will block
	            // until it succeeds or throws an exception
	            connectedSocket.connect();
	            outputstream = connectedSocket.getOutputStream();
	        } catch (IOException connectException) {
	            // Unable to connect; close the socket and get out
	            try {
	            	outputstream = null;
	            	connectedSocket.close();
	            } catch (IOException closeException) { }
	            return;
	        }
		}
	 
	    /** Will cancel an in-progress connection, and close the socket */
	    public void cancel() {
	        try {
	        	outputstream = null;
	        	connectedSocket.close();
	        } catch (IOException e) { }
	    }
	}


