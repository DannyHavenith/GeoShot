package net.havenith.geoshot;

import java.util.Set;

import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class SelectBluetoothDeviceActivity extends ListActivity {

	public static final String EXTRA_DEVICE_STRING = "net.havenith.selectbluetooth.EXTRA_DEVICE_STRING";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate( savedInstanceState);
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
		    finish();
		}
		ArrayAdapter<String> btArrayAdapter 
	    = new ArrayAdapter<String>(this,
	             android.R.layout.simple_list_item_1);
		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		// If there are paired devices
		if (pairedDevices.size() > 0) {
		    // Loop through paired devices
		    for (BluetoothDevice device : pairedDevices) {
		        // Add the name and address to an array adapter to show in a ListView
		        btArrayAdapter.add(device.getName() + "\n" + device.getAddress());
		    }
		}		
		
		setListAdapter( btArrayAdapter);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		String result = (String)getListAdapter().getItem(position);
		Intent intent = new Intent();
		intent.putExtra( EXTRA_DEVICE_STRING, result);
		setResult( RESULT_OK, intent);
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater()
				.inflate(R.menu.activity_select_bluetooth_device, menu);
		return true;
	}

}
