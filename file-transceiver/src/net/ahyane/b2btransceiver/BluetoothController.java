package net.ahyane.b2btransceiver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class BluetoothController{
	private static final String TAG = "BluetoothController";
	private Context mContext;
	
	public BluetoothController(Context context, String linkName, OnBluetoothListener bluetoothListener){
		mContext = context;
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothListener = bluetoothListener;
		mState = STATE_NONE;
		INIT_LINK_IDENTIFIER(linkName);
	}

	/////////////////////////////
	//user constants
	/////////////////////////////
	public static final int LINK_PART_NONE = 0;
	public static final int LINK_PART_SERVER = 1;
	public static final int LINK_PART_CLIENT = 2;
	
	public static final int STATE_NONE = 0;
	public static final int STATE_LISTEN = 1;
	public static final int STATE_CONNECTING = 2;
	public static final int STATE_CONNECTED = 3;
	
	/////////////////////////////
	//user members
	/////////////////////////////
	private int mState;
	private OnBluetoothListener mBluetoothListener = null;
	public interface OnBluetoothListener{
		public void onDiscoveryStarted();
		public void onDiscoveryFinished();
		public void onDeviceDiscovered(BluetoothDevice device);
		public void onConnected(BluetoothSocket socket, BluetoothDevice device, final boolean secure);
		public boolean onConnectionLost();
		public boolean onConnectionFaild();
		public void onStateChanged(int state);
		public void onDataReceived(byte[] data, int length);
	}
	
	/////////////////////////////
	//user public functions
	/////////////////////////////
	//s1
	public boolean isSupport(){
		return (mAdapter != null);
	}

	//s2
    public synchronized int getState(){
    	return mState;
    }
    
	//a1
    public boolean enable(Activity acticity, int requestCode){
    	if(!mAdapter.isEnabled()){
	    	Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	    	acticity.startActivityForResult(enableBluetoothIntent, requestCode);
	    	return true;
    	}
    	return false;
    }

    //a2
    public Set<BluetoothDevice> getBoundDevices(){
    	return mAdapter.getBondedDevices();
    }
    
    //a3
    public boolean discoverable(Activity acticity, int requestCode){
    	if(!mAdapter.isDiscovering()){
	    	Intent bluetoothDiscoverable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
	    	bluetoothDiscoverable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
	    	acticity.startActivityForResult(bluetoothDiscoverable, requestCode);
	    	return true;
    	}
    	return false;
    }

    //a4-1
    public void startDiscovery(){
    	mAdapter.startDiscovery();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
		intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		mContext.registerReceiver(mReceiver, intentFilter);

		if(mBluetoothListener != null){
			mBluetoothListener.onDiscoveryStarted();
		}
    }
    
    //a4-2
    public void cancelDiscovery(){
    	try{
    		mContext.unregisterReceiver(mReceiver);
    	}catch(IllegalArgumentException e){
    		e.printStackTrace();
    	}
    	mAdapter.cancelDiscovery();
    }

    //a5-1
	public synchronized int link(boolean secure){
		listen(secure);
		return LINK_PART_SERVER;
	}
	
	//a5-2-1
	public synchronized int link(BluetoothDevice device, boolean secure){
		if(device != null){
			connect(device, secure);
			return LINK_PART_CLIENT;
		}
		return LINK_PART_NONE;
	}
	
	//a5-2-2
	public synchronized int link(String address, boolean secure){
		if(address != null){
			BluetoothDevice device = mAdapter.getRemoteDevice(address);
			if(device != null){
				connect(device, secure);
				return LINK_PART_CLIENT;
			}
		}
		return LINK_PART_NONE;
	}
	
	//a6 common
	public synchronized void stop(){
    	//release
        if(mConnectThread != null){mConnectThread.cancel(); mConnectThread = null;}
        if(mConnectedThread != null){mConnectedThread.cancel(); mConnectedThread = null;}
        if(mAcceptThread != null){mAcceptThread.cancel(); mAcceptThread = null;}

		Log.d(TAG, "stop");
        setState(STATE_NONE);
    }
    
	//a7 common
	public boolean write(byte[] data){
        CommonConnectedThread commonConnectedThread;

        synchronized(this){
        	if(mState != STATE_CONNECTED)return false;
            commonConnectedThread = mConnectedThread;
        }
        
        if(commonConnectedThread != null){
        	commonConnectedThread.write(data);
        }
        return true;
    }

	/////////////////////////////
	//user private functions
	/////////////////////////////
	//server
	private synchronized void listen(boolean secure){
        //release
		if(mConnectThread != null){mConnectThread.cancel(); mConnectThread = null;}
		if(mConnectedThread != null){mConnectedThread.cancel(); mConnectedThread = null;}
		if(mAcceptThread != null){mAcceptThread.cancel(); mAcceptThread = null;}

		Log.d(TAG, "listen");
		setState(STATE_LISTEN);
		
		//new accept
		mAcceptThread = new ServerAcceptThread(true);
		mAcceptThread.start();
	}
	
	//client
	private synchronized void connect(BluetoothDevice device, boolean secure){
        //release
		if(mConnectThread != null){mConnectThread.cancel(); mConnectThread = null;}
        if(mConnectedThread != null){mConnectedThread.cancel(); mConnectedThread = null;}

		Log.d(TAG, "connect");
        setState(STATE_CONNECTING);
        
        //new request
		mConnectThread = new ClientConnectThread(device, secure);
		mConnectThread.start();
	}

    //common connected
	private synchronized void ouverture(BluetoothSocket socket, BluetoothDevice device, final boolean secure){
        //release
		if(mConnectThread != null){mConnectThread.cancel(); mConnectThread = null;}
		if(mConnectedThread != null){mConnectedThread.cancel(); mConnectedThread = null;}
		if(mAcceptThread != null){mAcceptThread.cancel(); mAcceptThread = null;}

		Log.d(TAG, "connected");
		setState(STATE_CONNECTED);
		
		if(mBluetoothListener != null){
			mBluetoothListener.onConnected(socket, device, secure);
		}
		
		//new connection
		mConnectedThread = new CommonConnectedThread(socket);
		mConnectedThread.start();
	}
	
	private synchronized void setState(int state){
        if(mState != state){
        	mState = state;
        	if(mBluetoothListener != null){
        		mBluetoothListener.onStateChanged(state);
        	}
        }
    }
	
	private void connectionFaild(){
		if(mBluetoothListener != null){
			if(!mBluetoothListener.onConnectionFaild()){
				stop();
			}
		}
	}
	
	private void connectionLost(){
		if(mBluetoothListener != null){
			if(!mBluetoothListener.onConnectionLost()){
				stop();
			}
		}
	}

	/////////////////////////////
	//bluetooth constants
	/////////////////////////////
	private String LINK_NAME_SECURE;
	private String LINK_NAME_INSECURE;
	private UUID LINK_UUID_SECURE;
	private UUID LINK_UUID_INSECURE;
	private final void INIT_LINK_IDENTIFIER(String LINK_NAME){
		LINK_NAME_SECURE = LINK_NAME + "SECURE";
		LINK_NAME_INSECURE = LINK_NAME + "INSECURE";
		LINK_UUID_SECURE = UUID.nameUUIDFromBytes(LINK_NAME_SECURE.getBytes());
		LINK_UUID_INSECURE = UUID.nameUUIDFromBytes(LINK_NAME_INSECURE.getBytes());
	}
	
	/////////////////////////////
	//bluetooth members
	/////////////////////////////
	private BluetoothAdapter mAdapter;
	private ServerAcceptThread mAcceptThread;
	private ClientConnectThread mConnectThread;
	private CommonConnectedThread mConnectedThread;
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			
			if(BluetoothDevice.ACTION_FOUND.equals(action)){
				BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if(mBluetoothListener != null){
					mBluetoothListener.onDeviceDiscovered(bluetoothDevice);
				}
			}else if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
				;//
			}else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
				if(mBluetoothListener != null){
					mBluetoothListener.onDiscoveryFinished();
				}
			}
		}
	};
	
	/////////////////////////////
	//bluetooth class/functions
	/////////////////////////////
    //������ Ŭ���̾�Ʈ ������ ��ٸ�
    private class ServerAcceptThread extends Thread{
    	private BluetoothServerSocket mmServerSocket;
    	private boolean mmSecure;
    	
		public ServerAcceptThread(boolean secure){
			BluetoothServerSocket tempServerSocket = null;
			mmSecure = secure;
			try{
				if(secure){
					tempServerSocket = mAdapter.listenUsingRfcommWithServiceRecord(LINK_NAME_SECURE, LINK_UUID_SECURE);
				}else{
					//2.3���� ����
					//tempServerSocket = mAdapter.listenUsingInsecureRfcommWithServiceRecord(LINK_NAME_INSECURE, LINK_UUID_INSECURE);
					tempServerSocket = mAdapter.listenUsingRfcommWithServiceRecord(LINK_NAME_INSECURE, LINK_UUID_INSECURE);
				}
			}catch(IOException e){
				e.printStackTrace();
			}
			mmServerSocket = tempServerSocket;		
		}

		@Override
		public void run(){
			if(mmServerSocket == null)return;
			
			BluetoothSocket socket = null;
			
			//�������� ������ ��ٸ�
			while(mState != STATE_CONNECTED){
				try{
					socket = mmServerSocket.accept();
				}catch(IOException e){
					e.printStackTrace();
					break;
				}
				
				if(socket != null){
					synchronized(BluetoothController.this){
						switch(mState){
		                    case STATE_LISTEN:
		                    case STATE_CONNECTING:{
		                    	// Situation normal. Start the connected thread.
		                    	ouverture(socket, socket.getRemoteDevice(), mmSecure);
		                    }break;
		                    case STATE_NONE:
	                        case STATE_CONNECTED:{
	                            // Either not ready or already connected. Terminate new socket.
	                            try {
	                                socket.close();
	                            } catch (IOException e) {
	                                e.printStackTrace();
	                            }
	                        }break;
						}
					}
				}
			}
		}
    	
		public void cancel(){
			if(mmServerSocket != null){
				try{
					mmServerSocket.close();
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		}
    }
    
    //Ŭ���̾�Ʈ�� ������ ���� ��û��
    private class ClientConnectThread extends Thread{
    	private BluetoothDevice mmDevice = null;
    	private BluetoothSocket mmSocket = null;
    	private boolean mmSecure;

		public ClientConnectThread(BluetoothDevice bluetoothDevice, boolean secure) {
			BluetoothSocket socket = null;
			mmDevice = bluetoothDevice;
			mmSecure = secure;
			try{
				if(secure){
					socket = bluetoothDevice.createRfcommSocketToServiceRecord(LINK_UUID_SECURE);
				}else{
					//2.3���� ����
					//socket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(LINK_UUID_INSECURE);
					socket = bluetoothDevice.createRfcommSocketToServiceRecord(LINK_UUID_INSECURE);
				}
			}catch(IOException e){
				e.printStackTrace();
			}
			mmSocket = socket;
		}

		@Override
		public void run() {
			//�뿪���� �����ϸ� �����ϱ� ���� �ֺ� �������� ��ġ �˻��� ����
			mAdapter.cancelDiscovery();
			
			//���� �õ�
			try{
				mmSocket.connect();
			}catch(IOException connectException){
				connectException.printStackTrace();
				try{
					mmSocket.close();
				}catch(IOException closeException){
					closeException.printStackTrace();
				}
				connectionFaild();
				return;
			}
			
			synchronized(BluetoothController.this){
				mConnectThread = null;
			}
			
			//connected
			ouverture(mmSocket, mmDevice, mmSecure);
		}
		
        public void cancel() {
            try{
                mmSocket.close();
            }catch(IOException e){
				e.printStackTrace();
            }
        }
    }

    //Ŭ���̾�Ʈ ������ ������ ����
    private class CommonConnectedThread extends Thread{
    	private BluetoothSocket mmSocket = null;
    	private InputStream mmInputStream = null;
    	private OutputStream mmOutputStream = null;
    	
		public CommonConnectedThread(BluetoothSocket bluetoothSocket) {
			InputStream tempInputStream = null;
			OutputStream tempOutputStream = null;
			mmSocket = bluetoothSocket;
			try{
				tempInputStream = bluetoothSocket.getInputStream();
				tempOutputStream = bluetoothSocket.getOutputStream();
			}catch(IOException e){
				e.printStackTrace();
			}
			mmInputStream = tempInputStream;
			mmOutputStream = tempOutputStream;
		}

		@Override
		public void run() {
			byte[] buffer = new byte[1024];
			int length;
			
			while(true){
				try{
					length = mmInputStream.read(buffer);
					if(mBluetoothListener != null){
						mBluetoothListener.onDataReceived(buffer, length);
					}
				}catch(IOException e){
					e.printStackTrace();
					connectionLost();
					break;
				}
			}
		}
    	
    	public void write(byte[] bytes){
    		try{
				mmOutputStream.write(bytes);
			}catch(IOException e){
				e.printStackTrace();
			}
    	}
    	
    	public void cancel(){
    		try{
				mmSocket.close();
			}catch(IOException e){
				e.printStackTrace();
			}
    	}
    }
}
