/*
 * 채팅을 하기위한 헬퍼클래스
 */

package net.ahyane.appshare;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BluetoothSimpleService {
    // Debugging
    private static final String TAG = "BluetoothSimpleService";
    private static final boolean D = true;

    // BluetoothChatService 처리기에서 보낸 키 이름
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    
    //서버소켓을 생성한 SDP 레코드 이름
    private static final String NAME = "BluetoothChat";

    // 현재 어플의 UUID값
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    private final BluetoothAdapter mAdapter;
    //Activity Main에 존재하는 핸들러
    private final Handler mHandler;
    private final Receiver mReceiver;
    
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    
    private int mState;

    //어떠한 연결도 이루어지지 않은 상태
    public static final int STATE_NONE = 0;
    //지금 들어오는(incoming) 연결을 청취중
    public static final int STATE_LISTEN = 1;
    // 현재 나가는(outgoing) 연결을 초기화 중
    public static final int STATE_CONNECTING = 2;
    //다른 디바이스와 연결이 되었다면
    public static final int STATE_CONNECTED = 3; 

    //BluetoothChatService 처리기에서 보낸 메시지 유형
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
  
    public BluetoothSimpleService(Context context, Handler handler, Receiver receiver) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
		mReceiver = receiver;
    }

    //현재 디바이스의 상태 세팅
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        //현재 상태가 변경되었음을 Main Activity에 알려줌
        mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }
    public synchronized int getState() {
        return mState;
    }
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // 연결을 시도하는 쓰레드를 취소한다
        if (mConnectThread != null) {
        	mConnectThread.cancel(); mConnectThread = null;}

        // 현재 연결을 실행하는 스레드 취소
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // BluetoothServerSocket에서 수신 대기하는 스레드를 시작
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
        setState(STATE_LISTEN);
    }

    /**
     * 원격 장치에 대한 연결을 시작 ConnectThread을 시작
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // 연결을 시도하는 스레드를 취소
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // 현재 연결을 실행하는 스레드 취소
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // 해당 장치와 연결할 수있는 스레드를 시작
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
         블루투스 연결을 관리 시작 ConnectedThread을 시작
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        // 연결완료 스레드 취소
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // 현재 연결을 실행하는 스레드 취소
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // 장치에 연결하려는 때문에  취소
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}

        // 연결을 관리하고 전송을 수행하는 스레드를 시작
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        //Main UI에 현재 장치의 이름을 보냄
        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * 모든 쓰레드 종료
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
        setState(STATE_NONE);
    }

    /**
     * 비동기 방식으로 ConnectedThread로 쓰기
     */
    public void write(byte[] out) {
        //연결된 쓰레드의 임시객체
        ConnectedThread r = null;
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
            r.write(out);
        }
    }

    public void write(byte[] out, int size) {
        //연결된 쓰레드의 임시객체
        ConnectedThread r = null;
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
            r.write(out, size);
        }
    }

    public void write(byte[] out, int offset, int size) {
        //연결된 쓰레드의 임시객체
        ConnectedThread r = null;
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
            r.write(out, offset, size);
        }
    }
    
    public void flush(){
        //연결된 쓰레드의 임시객체
        ConnectedThread r = null;
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
            r.flush();
        }
    }
    
    /**
     * 연결에 실패할 경우 보낼 메세지 구성
     */
    private void connectionFailed() {
        setState(STATE_LISTEN);

        // Main Activity로 메세지 전송
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "디바이스 연결이 실패했습니다!");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * 연결이 손실되었음을 나타내고 및 Main에 알린다
     */
    private void connectionLost() {
        setState(STATE_LISTEN);

        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "연결이 끊어졌습니다");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private class AcceptThread extends Thread {
        // 현재 로컬 블루투스서버소켓
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            //리스닝가능한 서버소켓을 생성
            try {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "BEGIN mAcceptThread" + this);
            //쓰레드 이름 세팅
            setName("AcceptThread");
            BluetoothSocket socket = null;

            //연결된 상태가 아니라면
            while (mState != STATE_CONNECTED) {
                try {
                    //연결요청을 기다리며 블럭된다.
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                if (socket != null) {
                    synchronized (BluetoothSimpleService.this) {
                        switch (mState) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // 정상적인 상황에서 연결된 스레드를 시작
                            connected(socket, socket.getRemoteDevice());
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:                        
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "소켓닫을 때 문제발생!", e);
                            }
                            break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            if (D) Log.d(TAG, "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }


    /**
     * 디바이스를 연결하는 쓰레드
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            //주어진 BluetoothDevice객체를 이용하여 커넥션을 위한 블루투스소켓객체를 얻는다
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            //현재 쓰레드 이름 세팅
            setName("ConnectThread");

            //연결을 느리게 할 수 있으므로 탐색을 취소함
            mAdapter.cancelDiscovery();

            // 블루투스 커넥션소켓 만들기
            try {
                // 연결또는 예외를 발생 시킴(블럭됨)
                mmSocket.connect();
            } catch (IOException e) {
            	//연결실패 메세지를 보낸다
                connectionFailed();
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "소켓 닫는중 예외발생!", e2);
                }
                //리스닝 모드를 다시 시작한다
                BluetoothSimpleService.this.start();
                return;
            }

            // 커넥션 재설정
            synchronized (BluetoothSimpleService.this) {
                mConnectThread = null;
            }

            //새로 실행
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel()에서 소켓 예외 발생!", e);
            }
        }
    }

    /**
     * 원격 디바이스와 연결을 실행.
     * 송/수신연결을 처리.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            DataInputStream bis = null;
            DataOutputStream bos = null;
            
            //스트림 연결
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
                bis = new DataInputStream(tmpIn);
                bos = new DataOutputStream(tmpOut);
            } catch (IOException e) {
                Log.e(TAG, "스트림연결중 에러발생!", e);
            }

			//mmInStream = tmpIn;
			//mmOutStream = tmpOut;
            
            mmInStream = bis;
            mmOutStream = bos;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    mReceiver.onReceive(buffer, bytes);
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * 출력스트림
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                //mmOutStream.flush();
                //mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "출력 중 에러!", e);
            }
        }

        public void write(byte[] buffer, int size) {
            try {
                mmOutStream.write(buffer, 0, size);
                //mmOutStream.flush();
                //mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "출력 중 에러!", e);
            }
        }

        public void write(byte[] buffer, int offset, int size) {
            try {
                mmOutStream.write(buffer, offset, size);
                //mmOutStream.flush();
                //mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "출력 중 에러!", e);
            }
        }
        
        public void flush(){
        	try {
				mmOutStream.flush();
			} catch (IOException e) {
                Log.e(TAG, "출력 중 에러!", e);
			}
        }
        
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "에러발생!", e);
            }
        }
    }

    public interface Receiver{
    	public void onReceive(byte[] bytes, int length);
    }

}
