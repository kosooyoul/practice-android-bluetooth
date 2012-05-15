package net.ahyane.b2btransceiver;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.StringTokenizer;

import net.ahyane.b2btransceiver.BluetoothController.OnBluetoothListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class B2BTransceiver extends Activity implements OnBluetoothListener, OnClickListener{
	private static final String TAG = "B2BTransceiver";
	
	private final int REQUEST_NONE = 0;
	private final int REQUEST_BLUETOOTH_ENABLE = 1;
	private final int REQUEST_BLUETOOTH_DISCOVERABLE = 2;
	
	private	ArrayList<BluetoothDevice> mBluetoothDeviceList;
	private BluetoothController mBluetoothController;
	
	private EditText mSendPathView;
    private EditText mReceivePathView;
    private TextView mStateView;
	private LinearLayout mLinearLayout;
	
	private static final int MSG_TEST = 0;
	private static final int MSG_SET_STATEVIEW = 1;
	
	private Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what){
				case MSG_TEST:
					break;
				case MSG_SET_STATEVIEW:
					mStateView.setText((String)msg.obj);
					break;
				
			}
			super.handleMessage(msg);
		}
	};
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		mBluetoothController = new BluetoothController(this, this.getPackageName(), this);
		mBluetoothDeviceList = new ArrayList<BluetoothDevice>();
        
        setContentView(R.layout.main);
        ((Button)findViewById(R.id.button1)).setOnClickListener(this);
        ((Button)findViewById(R.id.button2)).setOnClickListener(this);
        ((Button)findViewById(R.id.button3)).setOnClickListener(this);
        ((Button)findViewById(R.id.button4)).setOnClickListener(this);
        ((Button)findViewById(R.id.button5)).setOnClickListener(this);
        ((Button)findViewById(R.id.button6)).setOnClickListener(this);
        
        mSendPathView = (EditText)findViewById(R.id.editText1);
        mReceivePathView = (EditText)findViewById(R.id.editText2);
        mStateView = (TextView)findViewById(R.id.stateView);
        mLinearLayout = (LinearLayout)findViewById(R.id.messageLayout);
    }

	@Override
	protected void onStart() {
		super.onStart();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode){
			case REQUEST_NONE:{
				;
			}break;
			case REQUEST_BLUETOOTH_ENABLE:{
				Toast.makeText(this, "Enable Bluetooth. (result:" + resultCode + ")", Toast.LENGTH_SHORT).show();
			}break;
			case REQUEST_BLUETOOTH_DISCOVERABLE:{
				Toast.makeText(this, "Find Bluetooth devices. (result:" + resultCode + ")", Toast.LENGTH_SHORT).show();
			}break;
		}	
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onClick(View view) {
		switch(view.getId()){
			case R.id.button1:{
				mBluetoothController.enable(this, REQUEST_BLUETOOTH_ENABLE);
			}break;
			case R.id.button2:{
				mBluetoothController.discoverable(this, REQUEST_BLUETOOTH_DISCOVERABLE);
				mBluetoothController.startDiscovery();
			}break;
			case R.id.button3:{
				mHandler.sendMessage(Message.obtain(mHandler, MSG_SET_STATEVIEW, "Listen"));
				Toast.makeText(this, "Server listen", Toast.LENGTH_SHORT).show();
				mBluetoothController.link(true);
			}break;
			case R.id.button4:{
				showDeviceList();
			}break;
			case R.id.button5:{
				mBluetoothController.stop();
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_STATEVIEW, "Stop"));
			}break;
			case R.id.button6:{
				sendFile(mSendPathView.getText().toString());
			}break;
		}
	}
	boolean mDoWrite = false;
	
	private void sendFile(String path){
		File file = new File(path);

		if(!file.exists()){
			mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_STATEVIEW, "Failed Send File: File not exist"));
			return;
		}
		
		FileInputStream fis = null;
		BufferedInputStream bis = null;
		try {
			fis = new FileInputStream(file);
			bis = new BufferedInputStream(fis);
		
			mBluetoothController.write(("@AHYANE/FILE/" + String.valueOf(file.length()) + "/" + file.getName()).getBytes());
			mDoWrite = false;
			
			int ch = 0;
			int c = 0;
			byte buf[] = new byte[1024];
			while((ch = bis.read(buf)) != -1){
				while(!mDoWrite){
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					c++;
					if(c > 4){
						mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_STATEVIEW, "Time out"));
						break;
					}
				}
				mBluetoothController.write(buf);
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_STATEVIEW, "Failed Send File: Cannot found file"));
		} catch (IOException e) {
			mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_STATEVIEW, "Failed Send File: Cannot read"));
			return;
		} finally {
			try {
				if(bis != null)bis.close();
				if(fis != null)fis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}	
	
	/////////////////////////////
	//bluetooth callback
	/////////////////////////////
	@Override
	public void onDiscoveryStarted() {
		// TODO Auto-generated method stub
		mBluetoothDeviceList.clear();
		
		mHandler.sendMessage(Message.obtain(mHandler, MSG_SET_STATEVIEW, "Discovery Started"));
		Log.e(TAG, "onDiscoveryStarted");
	}

	@Override
	public void onDiscoveryFinished() {
		// TODO Auto-generated method stub
		mBluetoothDeviceList.addAll(0, mBluetoothController.getBoundDevices());
		for(int i = 0; i < mBluetoothDeviceList.size(); i++){
			BluetoothDevice device = mBluetoothDeviceList.get(i);
			TextView textView = new TextView(this);
			textView.setText(device.getName() + " / " + device.getAddress() + " / " + device.getBondState());
			mLinearLayout.addView(textView);
		}

		mHandler.sendMessage(Message.obtain(mHandler, MSG_SET_STATEVIEW, "Discovery Finished"));
		Log.e(TAG, "onDiscoveryFinished");
	}
	
	@Override
	public void onDeviceDiscovered(BluetoothDevice device) {
		// TODO Auto-generated method stub
		mBluetoothDeviceList.add(device);
		
		mHandler.sendMessage(Message.obtain(mHandler, MSG_SET_STATEVIEW,
			"Discovered Device: " + device.getName() + ", " + device.getAddress() + ", " + String.valueOf(device.getBondState()) + ", " + String.valueOf(device.getBluetoothClass().toString())));
		Log.e(TAG, "onDeviceDiscovered: " + device.getName() + ", " + device.getAddress() + ", " + String.valueOf(device.getBondState()) + ", " + String.valueOf(device.getBluetoothClass().toString()));
	}

	@Override
	public void onConnected(BluetoothSocket socket, BluetoothDevice device, boolean secure) {
		// TODO Auto-generated method stub
		
		mHandler.sendMessage(Message.obtain(mHandler, MSG_SET_STATEVIEW, "Connected: " + device.getName() + ", " + device.getAddress() + ", " + String.valueOf(device.getBondState())));
		Log.e(TAG, "onConnected: " + device.getName() + ", " + device.getAddress() + ", " + String.valueOf(device.getBondState()));
	}

	@Override
	public boolean onConnectionLost() {
		// TODO Auto-generated method stub

		mHandler.sendMessage(Message.obtain(mHandler, MSG_SET_STATEVIEW, "Connection Lost"));
		Log.e(TAG, "onConnectionLost");
		return false;
	}

	@Override
	public boolean onConnectionFaild() {
		// TODO Auto-generated method stub

		
		mHandler.sendMessage(Message.obtain(mHandler, MSG_SET_STATEVIEW, "Connection Faild"));
		Log.e(TAG, "onConnectionFaild");
		return false;
	}

	@Override
	public void onStateChanged(int state) {
		// TODO Auto-generated method stub
		Log.e(TAG, "onStateChanged: " + String.valueOf(state));
	}

	private static final int RECEIVE_MODE_NONE = 0;
	private static final int RECEIVE_MODE_FILE = 1;
	private int mReceiveMode = RECEIVE_MODE_NONE;

	private long mReservedFileSize;
	private long mReceivedFileSize;
	private long mCurrentFileSize;
	private String mReservedFileName;
	private File mReservedFile;
	private FileOutputStream mFOS;
	private DataOutputStream mDOS;
	
	private ByteBuffer mBB;
	
	@Override
	public synchronized void onDataReceived(byte[] data, int length) {
		byte temp[] = new byte[length];
		System.arraycopy(data, 0, temp, 0, length);
		
		if(mReceiveMode == RECEIVE_MODE_NONE){
			String string = new String(temp);
			if(string.equals("@OK")){
				mDoWrite = true;
			}else{
				StringTokenizer stringTokenizer = new StringTokenizer(string, "/");
				int c = stringTokenizer.countTokens();
				for(int i = 0; i < c; i++){
					String ele = stringTokenizer.nextToken();
					if(i == 0){
						if(!ele.equals("@AHYANE"))return;
					}else if(i == 1){
						if(!ele.equals("FILE"))return;
						else mReceiveMode = RECEIVE_MODE_FILE;
					}else if(i == 2){
						mReservedFileSize = Long.parseLong(ele);
						mCurrentFileSize = 0;
						mReceivedFileSize = 0;
					}else if(i == 3){
						mReservedFileName = ele;
					}
				}
				mBluetoothController.write(("@OK").getBytes());
				int size = (int)((mReservedFileSize - 1) / 1024) * 1024 + 1024;
				mBB = ByteBuffer.allocate((int)size);
			}
		}else if(mReceiveMode == RECEIVE_MODE_FILE){
			if(mReceivedFileSize < mReservedFileSize){
				mBB.put(temp);
				Log.e(TAG, new String(temp));
//				mDatas.add(new DataBack(data, length));
				mReceivedFileSize += length;
				
			}else{
				mReceiveMode = RECEIVE_MODE_NONE;
				
				new Thread(new Runnable(){
					@Override
					public void run() {
						String path;
						int i = 0;
						do{
							path = mReceivePathView.getText().toString() + mReservedFileName + ((i++ > 0)?("("+i+")"):(""));
							mReservedFile = new File(path);
						}while(mReservedFile.exists());
						
						try {
							mReservedFile.createNewFile();
							mFOS = new FileOutputStream(mReservedFile);
							mDOS = new DataOutputStream(mFOS);
							
							mCurrentFileSize = 0;
							int len;
							byte temp[] = new byte[1024];
							mBB.position(0);
							while(mCurrentFileSize < mReservedFileSize){
								if(mCurrentFileSize + 1024 < mReservedFileSize){
									len = 1024;
								}else{
									len = (int) (mReservedFileSize - mCurrentFileSize);
								}
								mBB.get(temp, 0, len);
								Log.e(TAG, new String(temp));
								mDOS.write(temp, 0, len);
								mCurrentFileSize += 1024;
							}
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							try {
								mBB.clear();
								if(mDOS != null)mDOS.close();
								if(mFOS != null)mFOS.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				}
				).start();
				
			}
		}
		
	}

	private void showDeviceList(){
		String deviceNameList[] = new String[mBluetoothDeviceList.size()];
		for(int i = 0; i < mBluetoothDeviceList.size(); i++){
			BluetoothDevice device = mBluetoothDeviceList.get(i);
			deviceNameList[i] = device.getName() + " / " + device.getBondState() + " / " + device.getAddress();
		}
		new AlertDialog.Builder(this)
		.setTitle("Devices")
		.setItems(deviceNameList,
			new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int selectedIndex) {
					mHandler.sendMessage(Message.obtain(mHandler, MSG_SET_STATEVIEW, "Connect"));
					mBluetoothController.link(mBluetoothDeviceList.get(selectedIndex), true);
				}
			}
		)
		.show();
	}
	
}