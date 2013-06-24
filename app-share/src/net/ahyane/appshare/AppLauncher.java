package net.ahyane.appshare;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;

public class AppLauncher extends Activity {
    private static final int REQUEST_CONNECT_DEVICE = 0x0001;
	private static final int REQUEST_ENABLE_BT = 0x0002;

	ItemInfoManager mItemInfoManager = null;
    
    //All Applications
	ArrayList<ItemInfo> mItemInfoList = null;
	ItemsAdapter mItemsAdapter = null;
	GridView mItemGridView = null;
	
	//Basket Applications
	ArrayList<ItemInfo> mBasketItemInfoList = null;
	ItemsAdapter mBasketItemsAdapter = null;
	ItemInfo mBasketItemInfo = null;
	GridView mBasketItemGridView = null;
	LinearLayout mBasketLayout = null;
	
	//Received Applications
	ArrayList<ItemInfo> mReceivedItemInfoList = null;
	ItemsAdapter mReceivedItemsAdapter = null;
	ItemInfo mReceivedItemInfo = null;
	GridView mReceivedItemGridView = null;
	LinearLayout mReceivedLayout = null;
	
	//Basket Send UI
	ToggleButton mDemoUser1 = null;
	ToggleButton mDemoUser2 = null;
	ToggleButton mDemoUser3 = null;
	Button mSendButton = null;
	
	//Other UI
	FrameLayout mOverlayLayout = null;
	View mDraggingView = null;
	boolean isDrag = false;

	//Network
	ItemSender mItemSender = null;
	
	//Status UI
	LinearLayout mStatusLayout = null;
	ScrollView mStatusScrollView = null;
	Dialog mDialog = null;

	//UI Update Handler
	private static final int MSG_ON_APP_RECEIVED = 0;
	private static final int MSG_ON_MESSGE = 1;
	private static final int MSG_START_WAIT = 2;
	private static final int MSG_END_WAIT = 3;
	Handler mUIUpdateHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what){
			
				case MSG_ON_APP_RECEIVED:{
					mReceivedItemInfoList.add((ItemInfo)msg.obj);
					mReceivedLayout.setVisibility(View.VISIBLE);
					mReceivedItemGridView.invalidateViews();
				}break;
				case MSG_ON_MESSGE:{
					TextView textView = new TextView(AppLauncher.this);
					textView.setText((String)msg.obj);
					mStatusLayout.addView(textView);
					if(mStatusLayout.getChildCount() > 20){
						mStatusLayout.removeViewAt(0);
					}
					mStatusScrollView.post(new Runnable(){
						@Override
						public void run() {
							mStatusScrollView.fullScroll(View.FOCUS_DOWN);							
						}
					});
				}break;
				case MSG_START_WAIT:{
					mDialog = new ProgressDialog(AppLauncher.this);
					mDialog.setOnKeyListener(new OnKeyListener() {
						@Override
						public boolean onKey(DialogInterface arg0, int arg1, KeyEvent arg2) {
							return true;
						}
					});
					mDialog.show();
				}break;
				case MSG_END_WAIT:{
					mDialog.dismiss();
				}break;
			}
		}
	};
	
	//All Applications Grid view
	OnItemClickListener mGridViewItemClickListener = new OnItemClickListener(){
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			//TODO::
		}
	};
	OnItemLongClickListener mGridViewItemLongClickListener = new OnItemLongClickListener(){
		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			mBasketItemInfo = (ItemInfo)parent.getItemAtPosition(position);
			if(mBasketItemInfo.isASEC){
				return true;
			}
			mUIUpdateHandler.sendMessage(Message.obtain(mUIUpdateHandler, MSG_ON_MESSGE, "패키지 '" + mBasketItemInfo.mcTitleFull + "' (" + mBasketItemInfo.mcSourceFilesizeText + ") 를 아래 목록에 드래그 드랍하여 공유 항목에 포함시킵니다."));
			
			mBasketItemInfo.isSelected = !mBasketItemInfo.isSelected;
			mBasketItemInfo.isNeedsUpdate = true;
			mItemGridView.invalidateViews();

			mOverlayLayout.setVisibility(View.VISIBLE);
			mDraggingView = mBasketItemInfo.createIsolatedView(AppLauncher.this);
			mOverlayLayout.addView(mDraggingView);
			mDraggingView.setX(-999);
			mDraggingView.setY(-999);
			
			mBasketLayout.setVisibility(View.VISIBLE);
			
			isDrag = true;
			
			return true;
		}
	};
	OnTouchListener mOnTouchDragListener = new OnTouchListener(){
		@Override
		public boolean onTouch(View view, MotionEvent event) {
			if(isDrag == false){
				return false;
			}
			int action = event.getAction();
			if(action == MotionEvent.ACTION_DOWN
			|| action == MotionEvent.ACTION_MOVE){
				mDraggingView.setX((int)(event.getX() - mDraggingView.getWidth() * 0.5f));
				mDraggingView.setY((int)(event.getY() - mDraggingView.getHeight() * 0.5f));
			}else if(action == MotionEvent.ACTION_UP){
				mOverlayLayout.setVisibility(View.INVISIBLE);
				mOverlayLayout.removeAllViews();
				
				if(event.getY() > mBasketLayout.getY()){
					boolean contains = false;
					for(ItemInfo info: mBasketItemInfoList){
						if(info.mPackageName.equals(mBasketItemInfo.mPackageName)){
							contains = true;
							break;
						}
					}
					if(contains == false){
						mUIUpdateHandler.sendMessage(Message.obtain(mUIUpdateHandler, MSG_ON_MESSGE, "패키지 '" + mBasketItemInfo.mcTitleFull + "'를 공유 항목으로 선택합니다."));
						
						mBasketItemInfoList.add(0, new ItemInfo(mBasketItemInfo.mPackageName, mBasketItemInfo.mType));
						mBasketItemGridView.invalidateViews();
					}
				}else{
					if(mBasketItemInfoList.size() == 0){
						mBasketLayout.setVisibility(View.GONE);
					}
				}
				
				isDrag = false;
				return true;
			}
			return false;
		}
	};
	//Basket Grid view
	OnItemClickListener mBasketGridViewItemClickListener = new OnItemClickListener(){
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			mUIUpdateHandler.sendMessage(Message.obtain(mUIUpdateHandler, MSG_ON_MESSGE, "패키지 '" + mBasketItemInfoList.get(position).mcTitleFull + "'를 제외합니다."));
			
			mBasketItemInfoList.remove(position);
			mBasketItemGridView.invalidateViews();
			
			if(mBasketItemInfoList.size() == 0){
				mBasketLayout.setVisibility(View.GONE);
			}
		}
	};
	//Received Grid view
	OnItemClickListener mReceivedGridViewItemClickListener = new OnItemClickListener(){
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			ItemInfo info = mReceivedItemInfoList.get(position);
			//ItemInfoManager.install(getPackageManager(), mReceivedItemInfoList.get(position));
			startActivity(ItemInfoManager.getInstallIntent(info.mExternalFilepath));
		}
	};
	
	//Basket Send Button
	OnClickListener mOnSendButtonClickListener = new OnClickListener(){
		@Override
		public void onClick(View view) {
			final ArrayList<RemoteUser> users = new ArrayList<RemoteUser>();
			if(mDemoUser1.isChecked()){
				users.add(new RemoteUser(0, String.valueOf(mDemoUser1.getText())));
			}
			if(mDemoUser2.isChecked()){
				users.add(new RemoteUser(1, String.valueOf(mDemoUser2.getText())));
			}
			if(mDemoUser3.isChecked()){
				users.add(new RemoteUser(2, String.valueOf(mDemoUser3.getText())));
			}
			if(users.size() > 0){
				new Thread(new Runnable(){
					@Override
					public void run() {
						mItemSender.send(mBasketItemInfoList, users);
					}
				}).start();
			}
		}
	};
	//Sender listener
	ItemSender.SendItemListener mSenderListener = new ItemSender.SendItemListener() {
		@Override
		public void onReady() {
			Log.e("SendItemListener", "Ready");
			mUIUpdateHandler.sendMessage(Message.obtain(mUIUpdateHandler, MSG_START_WAIT));			
		}

		@Override
		public boolean onSend(ItemInfo item) {
			Log.e("SendItemListener", "Successes : " + item.mPackageName);
			return false;
		}
		
		@Override
		public boolean onSkip(ItemInfo item, String message) {
			Log.e("SendItemListener", "Failed : " + item.mPackageName);
			Log.e("SendItemListener", "Reason : " + message);
			return false;
		}

		@Override
		public boolean onFail(String message) {
			Log.e("SendItemListener", "Failed : " + message);
			return false;
		}

		@Override
		public void onFinish() {
			Log.e("SendItemListener", "Finished");
			mUIUpdateHandler.sendMessage(Message.obtain(mUIUpdateHandler, MSG_END_WAIT));
		}

		@Override
		public void onStatusChanged(String message) {
			Log.e("SendItemListener", message);
			mUIUpdateHandler.sendMessage(Message.obtain(mUIUpdateHandler, MSG_ON_MESSGE, message));
		}
	};
	ItemSender.ReceiveItemListener mReceiveListener = new ItemSender.ReceiveItemListener() {
		@Override
		public void onStatusChanged(String message) {
			Log.e("ReceiveItemListener", message);
			mUIUpdateHandler.sendMessage(Message.obtain(mUIUpdateHandler, MSG_ON_MESSGE, message));
		}
		
		@Override
		public boolean onReceive(String filepath, String appname, byte[] icondata) {
			
//			int len = icondata.length;
//			int size = (int)Math.sqrt(len / 4);
//			Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
//			bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(icondata));
//			;
			ByteArrayInputStream in = new ByteArrayInputStream(icondata);
			Bitmap bitmap = BitmapFactory.decodeStream(in);

			//BitmapFactory.Options opt = new BitmapFactory.Options();
			//opt.inDither = true;
			//opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
			//Bitmap bitmap = BitmapFactory.decodeByteArray(icondata, 0, icondata.length)
			
			ItemInfo iteminfo = new ItemInfo((String)filepath, appname, new BitmapDrawable(getResources(), bitmap));
			
			mUIUpdateHandler.sendMessage(Message.obtain(mUIUpdateHandler, MSG_ON_APP_RECEIVED, iteminfo));
			return true;
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	    //feature
	    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
	    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

	    //layout/view
	    setContentView(R.layout.activity_app_launcher);

	    
	    //data manager/list adapter
	    mItemInfoManager = ItemInfoManager.getInstance(this);
		
		//grid view
	    mItemInfoList = new ArrayList<ItemInfo>();
		mItemsAdapter = new ItemsAdapter(this, mItemInfoList);
		mItemGridView = (GridView)findViewById(R.id.MyGridList);
		mItemGridView.setAdapter(mItemsAdapter);
		mItemGridView.setOnItemClickListener(mGridViewItemClickListener);
		mItemGridView.setOnItemLongClickListener(mGridViewItemLongClickListener);
		mItemGridView.setOnTouchListener(mOnTouchDragListener);
		//status layout
		//mStatusLayout = (LinearLayout)findViewById(R.id.StatusLayout);
		//mFileCountTextView = (TextView)mStatusLayout.findViewById(R.id.StatusFileCount);
		//mFileSizeTextView = (TextView)mStatusLayout.findViewById(R.id.StatusFileSize);
		
		//basket
		mBasketLayout = (LinearLayout)findViewById(R.id.BasketLayout);

		//basket grid view
	    mBasketItemInfoList = new ArrayList<ItemInfo>();
		mBasketItemsAdapter = new ItemsAdapter(this, mBasketItemInfoList);
		mBasketItemGridView = (GridView)findViewById(R.id.BasketGridList);
		mBasketItemGridView.setAdapter(mBasketItemsAdapter);
		mBasketItemGridView.setOnItemClickListener(mBasketGridViewItemClickListener);

		//received
		mReceivedLayout = (LinearLayout)findViewById(R.id.ReceivedLayout);
		
		//received grid view
	    mReceivedItemInfoList = new ArrayList<ItemInfo>();
		mReceivedItemsAdapter = new ItemsAdapter(this, mReceivedItemInfoList);
		mReceivedItemGridView = (GridView)findViewById(R.id.ReceivedGridList);
		mReceivedItemGridView.setAdapter(mReceivedItemsAdapter);
		mReceivedItemGridView.setOnItemClickListener(mReceivedGridViewItemClickListener);
		
		//basket send
		mDemoUser1 = (ToggleButton)findViewById(R.id.ToggleUser1);
		mDemoUser2 = (ToggleButton)findViewById(R.id.ToggleUser2);
		mDemoUser3 = (ToggleButton)findViewById(R.id.ToggleUser3);
		mSendButton = (Button)findViewById(R.id.SendButton);
		mSendButton.setOnClickListener(mOnSendButtonClickListener);
		
		//drag view
		mOverlayLayout = (FrameLayout)findViewById(R.id.OverlayLayout);
		
		//Network
		mItemSender = new ItemSender(this);
		mItemSender.setListener(mSenderListener, mReceiveListener);
		
		//Status Layout
		mStatusLayout = (LinearLayout)findViewById(R.id.StatusLayout);
		mStatusScrollView = (ScrollView)findViewById(R.id.StatusScrollView);
	}
	
	@Override
    public void onStart() {
        super.onStart();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        /*
         * 현재 블루투스기기가 활성화 되어있는지 확인한다.
         * 보통 전력소모를 위해 단말기의 설정에서 블루투스를 활성화 하지 않으며
         * 블루투스를 사용할 때 해당 인텐트를 호출하여 활성화를 진행한다.
         */
        if (bluetoothAdapter != null
        && !bluetoothAdapter.isEnabled()) {
        	/*
        	 * 블루투스 활성화/비활성화를 위한 권한을 요구하는 대화창이 호출된다.
        	 * 보통 enable()를 사용하지 않고 이렇게 인텐트를 통해 활성화 설정을 담당하게 된다.
        	 * 활성화를 사용자가 허락하게 되면 블루투스의 상태를 방송하는 ACTION_STATE_CHANGED BR
        	 * Action을 실행하게 된다.
        	 */
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //동기방식으로 현재 액티비티를 실행한다.
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            //활성화 퍼미션이 되어있다면 채팅서비스를 세팅한다.
        } else {
        	if(mItemSender.isConnectted() == false){
        		mItemSender.setAvailable(true);
        		scanRemoteDevices();
        	}
        }
    }

	@Override
	protected void onResume() {
		super.onResume();

		//Default UI
		findViewById(R.id.RootLayout).setBackground(WallpaperManager.getInstance(this).getDrawable());
		
		//My Applications
		PackageManager pm = getPackageManager();
		List<ApplicationInfo> appInfoList = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		Collections.sort(appInfoList, new ApplicationInfo.DisplayNameComparator(pm));
		for(ApplicationInfo appInfo: appInfoList){
			Intent intent = pm.getLaunchIntentForPackage(appInfo.packageName);
			if(intent == null)continue;
			if(ItemInfoManager.isSystem(appInfo.sourceDir))continue;
			mItemInfoList.add(new ItemInfo(appInfo.packageName, ItemInfo.APP_TYPE_INSTALLED));
		}
		mItemGridView.invalidate();
		
		//Basket Layout
		if(mBasketItemInfoList.size() > 0){
			mBasketLayout.setVisibility(View.VISIBLE);
		}
		
		//Network
		mItemSender.start();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		//Network
		mItemSender.stop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.app_launcher, menu);
		return true;
	}

	//private request functions
	//디바이스 스캔
    private void scanRemoteDevices(){
        //디바이스 스캔하기
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }
    
    //다른 디바이스에서 검색됨을 허용
    private void ensureDiscoverable() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        /*
         * 검색과 연결이 가능한 스캔모드가 아니라면 
         * 검색 및 응답 대화창을 호출하는 액티비티액션을 호출한다
         */
        if (bluetoothAdapter.getScanMode() !=
             BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            //300초동안 검색 및 응답모드가 실행된다
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        	// DeviceListActivity에서 스캔해서 선택해 넘어온 장치정보
            if (resultCode == Activity.RESULT_OK) {
            	//디바이스의 MAC주소를 얻는다
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                //연결된 디바이스의 객체를 얻는다
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                //디바이스와 연결을 시도한다.
                mItemSender.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // 블루투스 활성화를 허락했다면 채팅 준비를 시작한다
            if (resultCode == Activity.RESULT_OK) {
            	mItemSender.setAvailable(true);
            	
            	scanRemoteDevices();
            }
        }
    }

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		
		switch(keyCode){
			case KeyEvent.KEYCODE_VOLUME_UP:{
				mItemSender.stop();
				mItemSender.start();
				scanRemoteDevices();
			}return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:{
				ensureDiscoverable();
			}return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
    
    
    
}
