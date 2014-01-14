package net.havenith.geoshot;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.widget.TextView;


public class CameraControlActivity extends Activity {

    private Timer timer = new Timer();
	private TextView locationText;
	private Location lastLocation = null;
	private BluetoothAdapter bluetoothAdapter = null;
	private BluetoothConnection bluetoothConnection = new BluetoothConnection();
	private static final int REQUEST_ENABLE_BT = 42;
	private static final int REQUEST_SELECT_DEVICE = 43;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera_control);
		locationText = (TextView)findViewById(R.id.position_text);
		locationText.setText("no gps available");
		setupBluetooth();
		startLocationRequests();
	}
	
	@Override
	protected void onDestroy() {
		if (locationManager != null) {
			locationManager.removeUpdates( locationListener);
		}
		if (bluetoothConnection != null)
		{
			bluetoothConnection.close();
		}
		super.onDestroy();
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
						bluetoothConnection.connect( device);
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
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		locationListener = new LocationListener() {
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
			  locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5 * 1000, 0, locationListener);
		  }
		  
		  if (locationManager.isProviderEnabled( LocationManager.GPS_PROVIDER))
		  {
			  locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5 * 1000, 0, locationListener);
		  }

		  // schedule a timer that will send the latest location to the bluetooth device
		  // every 4s via the UI thread.
		  timer.schedule( new TimerTask() {
            @Override
            public void run() {
                CameraControlActivity.this.runOnUiThread( new Runnable() {
                    @Override
                    public void run() {
                        sendLocation();
                    }
                });
            }
		  }, 4000, 4000);
	}
	
	private static final int TWO_MINUTES = 1000 * 60 * 2;
	private LocationManager locationManager;
	private LocationListener locationListener;

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
	
	/**
	 * Calculate an NMEA checksum over a range of bytes (characters).
	 * This checksum is essentially the exclusive-or of every character between the '$' and '*' character. 
	 * @param characters
	 * @return
	 */
	private int checksum( byte characters[])
	{
		byte sum = 0;
		for (byte c : characters){
			if (c != '$' && c != '*') sum ^= c;
		}
		return sum;
	}

	private String formatLongLat( double longLat, boolean isLongitude) {
		final String format = isLongitude?"%03d%05.3f":"%02d%05.3f";
		return String.format( Locale.ENGLISH, format, (long)longLat, (longLat%1) * 60);
	}
	
	@SuppressLint("SimpleDateFormat")
	private String formatTimeLongLat( long time, double longitude, double latitude)
	{
		StringBuilder builder = new StringBuilder();
		
		builder.append( new SimpleDateFormat("HHmmss").format( time));
		builder.append(",");

		builder.append( formatLongLat( Math.abs(latitude), false));
		builder.append( latitude > 0?",N,":",S,");

		builder.append( formatLongLat( Math.abs(longitude), true));
		builder.append( longitude > 0?",E,":",W,");
		return builder.toString();
	}

	private String CreateGPRMC( Location location)
	{
		double latitude = location.getLatitude();
		double longitude = location.getLongitude();
		double knots = location.getSpeed() / 0.514; // from m/s to knots
		long time = location.getTime();
		
		StringBuilder builder = new StringBuilder("$GPRMC,");
		builder.append( new SimpleDateFormat("HHmmss").format( time));
		builder.append(",A,");
		
		DecimalFormatSymbols dotInDecimals = new DecimalFormatSymbols(Locale.ENGLISH);
		dotInDecimals.setDecimalSeparator('.');
		
		DecimalFormat latitudeFormat = new DecimalFormat( "0000.00", dotInDecimals);
		builder.append( latitudeFormat.format( Math.abs(latitude) * 100));
		builder.append( latitude > 0?",N,":",S,");

		DecimalFormat longitudeFormat = new DecimalFormat( "00000.00", dotInDecimals);
		builder.append( longitudeFormat.format( Math.abs(longitude) * 100));
		builder.append( longitude > 0?",E,":",W,");
		builder.append( new DecimalFormat("000.0", dotInDecimals).format(knots));
		builder.append(',');
		builder.append( new DecimalFormat("000.0", dotInDecimals).format( location.getBearing()));
		builder.append( ',');
		builder.append(new SimpleDateFormat("ddMMyy").format(time));
		builder.append(",000.1,E*");
		builder.append( String.format("%02X", checksum(builder.toString().getBytes())));
		builder.append("\r\n");
		return builder.toString();
	}
	
	
	private String CreateGPGGA( Location location)
	{
		double knots = location.getSpeed() / 0.514; // from m/s to knots
		
		StringBuilder builder = new StringBuilder("$GPGGA,");
		builder.append(formatTimeLongLat( location.getTime(), location.getLongitude(), location.getLatitude()));
		builder.append("1,04,0.9,0.0,M,0.0,M,,*");
		builder.append( String.format("%02X", checksum(builder.toString().getBytes())));
		builder.append("\r\n");

		return builder.toString();
	}
	
	protected void makeUseOfNewLocation(Location location) {
		if (isBetterLocation(location, lastLocation))
		{
			lastLocation  = location;
		}
	}
	
	protected void sendLocation() {
	    if (lastLocation != null) {
	        StringBuilder builder = new StringBuilder();
	        builder.append( lastLocation.getLongitude());
	        builder.append( ", ");
	        builder.append( lastLocation.getLatitude());
	        String gprmc = CreateGPRMC(lastLocation);
	        locationText.setText( gprmc);
	        bluetoothConnection.write( gprmc.getBytes());
	        bluetoothConnection.write( CreateGPGGA(lastLocation).getBytes());
	    }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_camera_control, menu);
		return true;
	}

}
