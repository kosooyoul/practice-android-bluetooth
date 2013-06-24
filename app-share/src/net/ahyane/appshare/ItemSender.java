package net.ahyane.appshare;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class ItemSender implements BluetoothSimpleService.Receiver{

	public interface SendItemListener {
		public void onReady();
		
		public boolean onSend(ItemInfo item);

		public boolean onFail(String message);

		public boolean onSkip(ItemInfo item, String message);
		
		public void onFinish();
		
		public void onStatusChanged(String message);
	}

	public interface ReceiveItemListener {
		public boolean onReceive(String filepath, String appname, byte[] icondata);

		public void onStatusChanged(String message);
	}
	
	private Context mContext = null;
	private MyNetwork mNetwork = null;
	private boolean isAvailable = false;
	
	private SendItemListener mSendItemListener = null;
	private ReceiveItemListener mReceiveItemListener = null;
	
	//Handler
    //블루투스채팅서비스 객체에서 넘어온 블루투스의 상황을 표시하는 핸들러
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	if(mReceiveItemListener == null){
        		return;
        	}
            switch (msg.what) {
            case BluetoothSimpleService.MESSAGE_STATE_CHANGE:
                switch (msg.arg1) {
                case BluetoothSimpleService.STATE_CONNECTED:
                    mReceiveItemListener.onStatusChanged("원격 디바이스와 연결되었습니다.");
                    break;
                case BluetoothSimpleService.STATE_CONNECTING:
                	mReceiveItemListener.onStatusChanged("원격 디바이스와 연결중입니다.");
                    break;
                case BluetoothSimpleService.STATE_LISTEN:
                	mReceiveItemListener.onStatusChanged("원격 디바이스와의 연결을 기다리고 있습니다.");
                	break;
                case BluetoothSimpleService.STATE_NONE:
                	mReceiveItemListener.onStatusChanged("연결할 원격 디바이스가 없습니다.");
                    break;
                }
                break;
            case BluetoothSimpleService.MESSAGE_DEVICE_NAME:
            	mReceiveItemListener.onStatusChanged(
            		"원격 디바이스 '" +
            		msg.getData().getString(BluetoothSimpleService.DEVICE_NAME) +
            		"'를 찾았습니다.");
                break;
            case BluetoothSimpleService.MESSAGE_TOAST:
            	msg.getData().getString(msg.getData().getString(BluetoothSimpleService.TOAST));
                break;
            }
        }
    };
    
	public ItemSender(Context context) {
		super();

		mContext = context;
	}
	
	public void setListener(SendItemListener sendListener, ReceiveItemListener receiveListener){
		mSendItemListener = sendListener;
		mReceiveItemListener = receiveListener;
	}

	public void setAvailable(boolean enabled){
		isAvailable = enabled;
		if(enabled){
			mNetwork = new MyNetwork(mContext, mHandler, this);
		}else{
			mNetwork = null;
		}
	}
	
	public void send(final ArrayList<ItemInfo> items, final ArrayList<RemoteUser> users) {
		if(isAvailable == false){
			if(mSendItemListener != null){
				mSendItemListener.onFail("Network is not available");
			}
			return;
		}
		

		if(mSendItemListener != null){
			mSendItemListener.onReady();
		}
		
		// header
		mNetwork.sendString("Hello");

		// send user list
		if(mSendItemListener != null){
			mSendItemListener.onStatusChanged("공유할 사용자 정보를 전송합니다.");
		}
		mNetwork.sendInt(users.size());
		for (RemoteUser user : users) {
			mNetwork.sendInt(user.mId);
		}

		// send item
		if(mSendItemListener != null){
			mSendItemListener.onStatusChanged("총 " + items.size() + "개의 패키지를 전송합니다.");
		}
		mNetwork.sendInt(items.size());
		for (ItemInfo item : items) {

			if(mSendItemListener != null){
				mSendItemListener.onStatusChanged("패키지 '" + item.mcTitleFull + "'을 전송합니다.");
			}
			
			//Validity check
			if (item.mcSourceDir == null) {
				if(mSendItemListener != null){
					mSendItemListener.onSkip(item, "Skip - 패키지 파일을 찾을 수 없습니다.");
				}
				continue;
			}
			File file = new File(item.mcSourceDir);
			if (file.canRead() == false) {
				if(mSendItemListener != null){
					mSendItemListener.onSkip(item, "Skip - 패키지 파일을 읽을 수 없습니다.");
				}
				continue;
			}
			if (file.length() == 0) {
				if(mSendItemListener != null){
					mSendItemListener.onSkip(item, "Skip - 패키지 파일의 내용이 비어있습니다.");
				}
				continue;
			}

			//item info
			{
				Bitmap bitmap = ((BitmapDrawable)item.mcIcon).getBitmap();
				ByteArrayOutputStream  byteArray = new ByteArrayOutputStream();
				bitmap.compress(CompressFormat.PNG, 100, byteArray);
				
				byte[] buffer = byteArray.toByteArray();
				mNetwork.sendInt(buffer.length);
				for(int i = 0; i < buffer.length; i+=1024){
					if(buffer.length - i < 1024){
						mNetwork.sendBytes(buffer, i, buffer.length - i);
					}else{
						mNetwork.sendBytes(buffer, i, 1024);
					}
				}
				
				mNetwork.sendInt(item.mcTitleFull.getBytes().length);
				mNetwork.sendString(item.mcTitleFull);
			}
			
			String fileName = file.getName();
			mNetwork.sendInt(fileName.length());
			mNetwork.sendString(fileName);
			mNetwork.sendLong(file.length());
			byte[] buf = new byte[1024];
			try {
				FileInputStream fis = new FileInputStream(file);
				BufferedInputStream bis = new BufferedInputStream(fis);
				int size = 0;
				while ((size = bis.read(buf)) > 0) {
					mNetwork.sendBytes(buf, size);
				}
				bis.close();
				
				if(mSendItemListener != null){
					mSendItemListener.onSend(item);
					mSendItemListener.onStatusChanged("전송이 완료되었습니다.");
				}
			} catch (IOException e) {
				if(mSendItemListener != null){
					mSendItemListener.onSkip(item, "Error!! - 파일을 읽는 도중 오류가 발생하였습니다.");
				}
			}
		}
		
		// footer
		mNetwork.sendString("Bye~!");
		mNetwork.sendBytes(new byte[8192]);
		mNetwork.flush();

		if(mSendItemListener != null){
			mSendItemListener.onFinish();
			mSendItemListener.onStatusChanged("모든 패키지의 전송이 완료되었습니다.");
		}
	}

	public void start() {
		if (isAvailable) mNetwork.start();
	}

	public void stop() {
		if (isAvailable) mNetwork.stop();
	}

	public void connect(BluetoothDevice device) {
		if (isAvailable) mNetwork.connect(device);		
	}
	
	public boolean isConnectted(){
		return (isAvailable && mNetwork.isConnectted());
	}

	private static final int NEED_HEADER = 5;
	private static final int NEED_FOOTER = 5;
	private static final int NEED_BYTE = 1;
	private static final int NEED_INT = 4;
	private static final int NEED_LONG = 8;
	private static final int NEED_BUFFER = 8192 - 1024;
	
	private static final int TYPE_HEADER = 0;
	private static final int TYPE_USER_COUNT = 1;
	private static final int TYPE_USER_ID = 2;
	private static final int TYPE_PACKAGE_COUNT = 3;

	private static final int TYPE_ICON_LENGTH = 4;
	private static final int TYPE_ICON = 5;
	private static final int TYPE_APPNAME_LENGTH = 6;
	private static final int TYPE_APPNAME = 7;
	
	private static final int TYPE_FILENAME_LENGTH = 8;
	private static final int TYPE_FILENAME = 9;
	private static final int TYPE_PACKAGE_DATA_LENGTH = 10;
	private static final int TYPE_PACKAGE_DATA = 11;
	private static final int TYPE_FOOTER = 12;
	
	private long mReceiveId = 0;
	
	private boolean receiveStart = false;
	private long need = NEED_HEADER;
	
	private int type = TYPE_HEADER;
	private byte[] temp = new byte[8192];
	private int tempLength = 0;

	private int leftLoop = 0;
	private long dataLength = 0;
	private String tempText = null;
	
	//data
	private byte[] mTempByteData = null;
	private String mAppname = null;
	private String mFilepath = null;
	private FileOutputStream mFileOutputStream = null;
	private long mDataLength = 0;
	private int mPrevRatio = 0;
	
	private void didRead(int length){
		tempLength -= length;
		System.arraycopy(temp, length, temp, 0, tempLength);
	}
	
	@Override
	public void onReceive(byte[] bytes, int length) {
		System.arraycopy(bytes, 0, temp, tempLength, length);
		tempLength += length;
		
		while(tempLength >= need){
			if(receiveStart == false){
				String header = new String(temp, 0, NEED_HEADER);
				if(header.equals("Hello")){
					didRead(NEED_HEADER);
					need = NEED_INT;
					type = TYPE_USER_COUNT;
					receiveStart = true;

					mReceiveId = System.currentTimeMillis();
					{
						File file = new File("/sdcard/UmixShare/" + mReceiveId + "/");
						if(!file.exists())file.mkdirs();
					}
					{
						File file = new File("/sdcard/UmixShare/" + mReceiveId + "/users/");
						if(!file.exists())file.mkdirs();
					}
					if(mReceiveItemListener != null){
						mReceiveItemListener.onStatusChanged("원격 디바이스로부터 패키지를 수신합니다.");
					}
				}else{
					didRead(1);
				}
			}else{
				switch(type){
					case TYPE_USER_COUNT:{
						//leftLoop = temp[0] + (temp[1] << 8) + (temp[2] << 16) + (temp[3] << 24);
						leftLoop = Utils.readInt(temp);
						didRead(NEED_INT);
						need = NEED_INT;
						type = TYPE_USER_ID;
						Log.e("ItemSender", "User Count = " + leftLoop);
					}break;
					//START LOOP
					case TYPE_USER_ID:{
						leftLoop--;
						//int id = temp[0] + (temp[1] << 8) + (temp[2] << 16) + (temp[3] << 24);
						int id = Utils.readInt(temp);
						didRead(NEED_INT);
						if(leftLoop == 0){
							need = NEED_INT;
							type = TYPE_PACKAGE_COUNT;
						}
						
						try {
							new File("/sdcard/UmixShare/" + mReceiveId + "/users/" + id).createNewFile();
						} catch (IOException e) {
							e.printStackTrace();
						}
						Log.e("ItemSender", "User ID = " + id);
					}break;
					//END LOOP
					case TYPE_PACKAGE_COUNT:{
						//leftLoop = temp[0] + (temp[1] << 8) + (temp[2] << 16) + (temp[3] << 24);
						leftLoop = Utils.readInt(temp);
						didRead(NEED_INT);
						need = NEED_INT;
						type = TYPE_ICON_LENGTH;
						Log.e("ItemSender", "Package Count = " + leftLoop);

						if(mReceiveItemListener != null){
							mReceiveItemListener.onStatusChanged("총 " + leftLoop + "개의 패키지 중...");
						}
					}break;
					//START LOOP
					case TYPE_ICON_LENGTH:{
						dataLength = Utils.readInt(temp);
						
						mTempByteData = new byte[(int)dataLength];
						
						didRead(NEED_INT);
						need = NEED_BUFFER;
						type = TYPE_ICON;
						Log.e("ItemSender", "Icon data Length = " + dataLength);
					}break;
					case TYPE_ICON:{
						int readLength = NEED_BUFFER;
						if(dataLength > NEED_BUFFER){
							readLength = NEED_BUFFER;
						}else if(dataLength < NEED_BUFFER){
							readLength = (int)dataLength;
						}
						
						//Write
						System.arraycopy(temp, 0, mTempByteData, (int)(mTempByteData.length - dataLength), readLength);

						dataLength -= readLength;
						didRead(readLength);
						
						if(dataLength == 0){
							need = NEED_INT;
							type = TYPE_APPNAME_LENGTH;
						}
					}break;
					case TYPE_APPNAME_LENGTH:{
						dataLength = Utils.readInt(temp);
						didRead(NEED_INT);
						need = NEED_BYTE * dataLength;
						type = TYPE_APPNAME;
						Log.e("ItemSender", "App name Length = " + dataLength);
					}break;
					case TYPE_APPNAME:{
						mAppname = new String(temp, 0, (int)(NEED_BYTE * dataLength));
						didRead((int)(NEED_BYTE * dataLength));
						need = NEED_INT;
						type = TYPE_FILENAME_LENGTH;

						if(mReceiveItemListener != null){
							mReceiveItemListener.onStatusChanged("패키지 '" + mAppname + "'을 수신합니다.");
						}
					}break;
					case TYPE_FILENAME_LENGTH:{
						leftLoop--;
						//dataLength = temp[0] + (temp[1] << 8) + (temp[2] << 16) + (temp[3] << 24);
						dataLength = Utils.readInt(temp);
						didRead(NEED_INT);
						need = NEED_BYTE * dataLength;
						type = TYPE_FILENAME;
						Log.e("ItemSender", "Filename Length = " + dataLength);
					}break;
					case TYPE_FILENAME:{
						tempText = new String(temp, 0, (int)(NEED_BYTE * dataLength));
						didRead((int)(NEED_BYTE * dataLength));
						need = NEED_LONG;
						type = TYPE_PACKAGE_DATA_LENGTH;

						try {
							mFilepath = "/sdcard/UmixShare/" + mReceiveId + "/" + tempText;
							mFileOutputStream = new FileOutputStream(new File(mFilepath));
						} catch (IOException e) {
							e.printStackTrace();
						}
//						if(mReceiveItemListener != null){
//							mReceiveItemListener.onStatusChanged("패키지 파일 '" + tempText + "'을 수신합니다.");
//						}
						Log.e("ItemSender", "Filename = " + tempText);
					}break;
					case TYPE_PACKAGE_DATA_LENGTH:{
						//dataLength = temp[0] + (temp[1] << 8) + (temp[2] << 16) + (temp[3] << 24) + (temp[4] << 32) + (temp[5] << 40) + (temp[6] << 48) + (temp[7] << 56);
						mDataLength = dataLength = Utils.readLong(temp);
						didRead(NEED_LONG);
						need = NEED_BUFFER;
						type = TYPE_PACKAGE_DATA;
						Log.e("ItemSender", "Package data Length = " + dataLength);
					}break;
					case TYPE_PACKAGE_DATA:{
						int readLength = NEED_BUFFER;
						if(dataLength > NEED_BUFFER){
							readLength = NEED_BUFFER;
						}else if(dataLength < NEED_BUFFER){
							readLength = (int)dataLength;
						}
						
						//Log.e("ByteData", "Prev = " + dataLength + ", Read = " + readLength + ", Left = " + (dataLength - readLength));

						try {
							mFileOutputStream.write(temp, 0, readLength);
						} catch (IOException e) {
							e.printStackTrace();
						}
						
						dataLength -= readLength;
						didRead(readLength);
						
						//Ratio
						int r = (int)((float)(mDataLength - dataLength) / mDataLength * 10) * 10;
						if(r >= mPrevRatio){
							if(mReceiveItemListener != null){
								mReceiveItemListener.onStatusChanged("총 " + mDataLength + " Byte 중, " + (mDataLength - dataLength) + " Byte 수신 - " + r + " %");
							}
							mPrevRatio = (r / 10 * 10) + 10;
						}
						
						if(dataLength == 0){
							mPrevRatio = 0;
							
							if(leftLoop > 0){
//								need = NEED_INT;
//								type = TYPE_FILENAME_LENGTH;
								need = NEED_INT;
								type = TYPE_ICON_LENGTH;
							}else{
								need = NEED_FOOTER;
								type = TYPE_FOOTER;
							}
							
							try {
								mFileOutputStream.flush();
								mFileOutputStream.close();
								mFileOutputStream = null;
								if(mReceiveItemListener != null){
									mReceiveItemListener.onReceive(mFilepath, mAppname, mTempByteData);
								}
								if(mReceiveItemListener != null){
									mReceiveItemListener.onStatusChanged("패키지  '" + mAppname + "'수신을 완료하였습니다.");
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}break;
					//END LOOP
					case TYPE_FOOTER:{
						String footer = new String(temp, 0, NEED_FOOTER);
						if(footer.equals("Bye~!")){
							Log.e("ItemSender", "Successes!!");

							if(mReceiveItemListener != null){
								mReceiveItemListener.onStatusChanged("원격 디바이스에서 공유한 패키지를 모두 수신하였습니다.");
							}
						}
						didRead(NEED_FOOTER);
						need = NEED_HEADER;
						type = TYPE_HEADER;
						receiveStart = false;

						//reset
						tempLength = 0;
						leftLoop = 0;
						dataLength = 0;
						tempText = null;
					}break;
				}

			}
		}
	}
	
	private class MyNetwork {
		private static final String LOG_TAG = "MyNetwork";
		
		private BluetoothSimpleService mBluetoothService = null;
		private BluetoothDevice mBluetoothDevice = null;
		
		public MyNetwork(Context context, Handler handler, BluetoothSimpleService.Receiver receiver) {
			super();
           	mBluetoothService = new BluetoothSimpleService(context, handler, receiver);
		}
		
		private void start(){
	        /*
	         * 블루투스 채팅을 사용할 준비가 되었다면 
	         */
	        if (mBluetoothService != null) {
	        	//현재상태가 STATE_NOTE라면 시작하지 않았음을 의미
	            if (mBluetoothService.getState() == BluetoothSimpleService.STATE_NONE) {
	            	// 블루투스 채팅서비스를 시작한다
	            	mBluetoothService.start();
	            }
	        }
		}
		
		private void stop(){
	        // 채팅 서비스 정지
	        if (mBluetoothService != null) mBluetoothService.stop();
	        mBluetoothDevice = null;
		}
		
		private void connect(BluetoothDevice device){
			mBluetoothDevice = device;
			if (mBluetoothService != null) mBluetoothService.connect(device);
		}
		
		private boolean isConnectted(){
			return (mBluetoothDevice != null);
		}
		
		private void sendString(String string) {
			mBluetoothService.write(string.getBytes());
		}

		private void sendBytes(byte[] bytes) {
			mBluetoothService.write(bytes);
		}

		private void sendBytes(byte[] bytes, int size) {
			mBluetoothService.write(bytes, size);
		}

		private void sendBytes(byte[] bytes, int offset, int size) {
			mBluetoothService.write(bytes, offset, size);
		}
		
		private void sendByte(byte b) {
			mBluetoothService.write(new byte[]{b});
		}

		private void sendInt(long i) {
			mBluetoothService.write(
				new byte[]{
					(byte)(i >> 24 & 0xFF),
					(byte)(i >> 16 & 0xFF),
					(byte)(i >> 8 & 0xFF),
					(byte)(i >> 0 & 0xFF)
				}
			);
		}

		private void sendLong(long l) {
			mBluetoothService.write(
				new byte[]{
					(byte)(l >> 56 & 0xFF),
					(byte)(l >> 48 & 0xFF),
					(byte)(l >> 40 & 0xFF),
					(byte)(l >> 32 & 0xFF),
					(byte)(l >> 24 & 0xFF),
					(byte)(l >> 16 & 0xFF),
					(byte)(l >> 8 & 0xFF),
					(byte)(l >> 0 & 0xFF),
				}
			);
		}
		
		private void flush(){
			mBluetoothService.flush();
		}

	}

}
