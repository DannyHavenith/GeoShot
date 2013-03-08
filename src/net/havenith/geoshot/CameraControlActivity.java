package net.havenith.geoshot;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;



import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.widget.TextView;

public class CameraControlActivity extends Activity {

	private TextView locationText;
	private Location lastLocation = null;
	private BluetoothAdapter bluetoothAdapter = null;
	private BluetoothDevice connectedDevice = null;
	private BluetoothSocket connectedSocket;
	private OutputStream outputstream;
	private ConnectThread connectThread;
	private static final int REQUEST_ENABLE_BT = 42;
	private static final int REQUEST_SELECT_DEVICE = 43;
	private static final String UUID_SERIAL = "00001101-0000-1000-8000-00805F9B34FB";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera_control);
		locationText = (TextView)findViewById(R.id.position_text);
		locationText.setText("no gps available");
		setupBluetooth();
		//startLocationRequests();
	}
	
	private class ConnectThread extends Thread {
	 
	    public void run() {
	        // Cancel discovery because it will slow down the connection
	        //bluetoothAdapter.cancelDiscovery();
	 
	        try {
	            // Connect the device through the socket. This will block
	            // until it succeeds or throws an exception
	            connectedSocket.connect();
	            outputstream = connectedSocket.getOutputStream();
	            outputstream.write("+connect(danny)".getBytes());
	            CameraControlActivity.this.runOnUiThread( new Runnable() {
					
					@Override
					public void run() {
						startLocationRequests();
					}
				});
	        } catch (IOException connectException) {
	            // Unable to connect; close the socket and get out
	            try {
	            	connectedSocket.close();
	            } catch (IOException closeException) { }
	            return;
	        }
	 
	        // Do work to manage the connection (in a separate thread)
	        
	    }
	 
	    /** Will cancel an in-progress connection, and close the socket */
	    public void cancel() {
	        try {
	        	connectedSocket.close();
	        } catch (IOException e) { }
	    }
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_ENABLE_BT) {
			if (resultCode != RESULT_OK) {
				showError( "failed to enable bluetooth");
			}
			else {
				connectToBluetoothDevice();
			}
		} else if (requestCode == REQUEST_SELECT_DEVICE) {
			if (resultCode == RESULT_OK) {
				String deviceString = data.getStringExtra( SelectBluetoothDeviceActivity.EXTRA_DEVICE_STRING);
				Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
				
				for (BluetoothDevice device : pairedDevices) {
					if (deviceString.equals( device.getName() + "\n" + device.getAddress() )) {
						connectedDevice  = device;
						try {
							connectedSocket = device.createRfcommSocketToServiceRecord( UUID.fromString(UUID_SERIAL));
							connectThread = new ConnectThread();
							connectThread.start();
						} catch (IOException e) {
							e.printStackTrace();
						}
						break;
					}
				}
					
				
				
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	/**
	 * connect to either a pre-configured bluetooth device or try to find one.
	 */
	private void connectToBluetoothDevice() {
		Intent intent = new Intent( this, SelectBluetoothDeviceActivity.class);
		startActivityForResult( intent, REQUEST_SELECT_DEVICE);
	}

	private void setupBluetooth()
	{
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
		    showError("no bluetooth adapter found");
		    return;
		}		
		if (!bluetoothAdapter.isEnabled()) {
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
		else {
			connectToBluetoothDevice();
		}
	}
	
	private void showError(String message) {
		locationText.setText( message);
	}

	private void startLocationRequests()
	{
		// Acquire a reference to the system Location Manager
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		// Define a listener that responds to location updates
		LocationListener locationListener = new LocationListener() {
			@Override
		    public void onLocationChanged(Location location) {
		      // Called when a new location is found by the network location provider.
		      makeUseOfNewLocation(location);
		    }

		    public void onStatusChanged(String provider, int status, Bundle extras) {}

		    public void onProviderEnabled(String provider) {}

		    public void onProviderDisabled(String provider) {}

		  };

		// Register the listener with the Location Manager to receive location updates
		  if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
		  {
			  locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60 * 1000, 0, locationListener);
		  }
		  
		  if (locationManager.isProviderEnabled( LocationManager.GPS_PROVIDER))
		  {
			  locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60 * 1000, 0, locationListener);
		  }
	}
	private static final int TWO_MINUTES = 1000 * 60 * 2;

	/** Determines whether one Location reading is better than the current Location fix
	  * @param location  The new Location that you want to evaluate
	  * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	  */
	protected boolean isBetterLocation(Location location, Location currentBestLocation) {
	    if (currentBestLocation == null) {
	        // A new location is always better than no location
	        return true;
	    }

	    // Check whether the new location fix is newer or older
	    long timeDelta = location.getTime() - currentBestLocation.getTime();
	    boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
	    boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
	    boolean isNewer = timeDelta > 0;

	    // If it's been more than two minutes since the current location, use the new location
	    // because the user has likely moved
	    if (isSignificantlyNewer) {
	        return true;
	    // If the new location is more than two minutes older, it must be worse
	    } else if (isSignificantlyOlder) {
	        return false;
	    }

	    // Check whether the new location fix is more or less accurate
	    int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
	    boolean isLessAccurate = accuracyDelta > 0;
	    boolean isMoreAccurate = accuracyDelta < 0;
	    boolean isSignificantlyLessAccurate = accuracyDelta > 200;

	    // Check if the old and new location are from the same provider
	    boolean isFromSameProvider = isSameProvider(location.getProvider(),
	            currentBestLocation.getProvider());

	    // Determine location quality using a combination of timeliness and accuracy
	    if (isMoreAccurate) {
	        return true;
	    } else if (isNewer && !isLessAccurate) {
	        return true;
	    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
	        return true;
	    }
	    return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
	    if (provider1 == null) {
	      return provider2 == null;
	    }
	    return provider1.equals(provider2);
	}
	
	protected void makeUseOfNewLocation(Location location) {
		if (isBetterLocation(location, lastLocation))
		{
			lastLocation  = location;
			StringBuilder builder = new StringBuilder();
			builder.append( location.getLongitude());
			builder.append( ", ");
			builder.append( location.getLatitude());
			locationText.setText( builder.toString());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_camera_control, menu);
		return true;
	}

}
