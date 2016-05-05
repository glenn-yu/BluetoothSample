package com.example.bluetoothsample;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
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
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";

	private static final int REQUEST_ENABLE_BT = 1;
	private static final int BLUETOOTH_STATE_UNKNOWN = -1;

	private BluetoothAdapter mBluetoothAdapter;

	private ListView deviceList;
	private ArrayAdapter<String> adapter; // 원격 디바이스 어뎁터
	private ArrayList<String> mArrayListDevice; // 원격 디바이스 목록

	// 채팅
	private ListView chatList;
	private ArrayAdapter<String> chatAdapter;
	private ArrayList<String> mArrayChatList;

	private static final String BLUE_NAME = "BluetoothDevice"; // 서버 소켓을 생성할때
																// SDP 레코드 이름으로
																// 사용
	private static final UUID UUID_ANDROID_DEVICE = UUID
			.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
	private static final UUID UUID_OTHER_DEVICE = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private ClientThread mClientThread = null; // 클라이언트 소켓 접속 스레드
	private AcceptThread mAcceptThread = null; // 서버 소켓 접속 스레드
	private SocketThread mSocketThread = null; // 데이터 송수신 스레드

	private Button btnScan, btnSend;
	private EditText sendMsg;

	private TextView chatText;

	// private boolean isAndroid = true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		sendMsg = (EditText) findViewById(R.id.sendMsg);

		btnScan = (Button) findViewById(R.id.btnScan);
		btnScan.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getPairedDevices();
				scanDevices();
			}
		});
		
		btnSend = (Button) findViewById(R.id.send);
		btnSend.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String strBuf = sendMsg.getText().toString();
				if (strBuf.length() < 1)
					return;
				sendMsg.setText("");
				mSocketThread.write(strBuf);
			}
		});

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// 장치가 블루투스를 지원하는지 여부 확인.
		if (mBluetoothAdapter == null) {
			Log.d(TAG, "Device dose not support Bluetooth");

			finish();
			return;
		}

		/*
		 * // Use this check to determine whether BLE is supported on the
		 * device. Then // you can selectively disable BLE-related features. if
		 * (
		 * !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE
		 * )) { Toast.makeText(this, "BLE 미지원.. ", Toast.LENGTH_SHORT).show();
		 * finish(); }
		 */

		// 블루투스가 OFF 이면 ON 으로 바꾸기
		if (mBluetoothAdapter.isEnabled()) {
			// getPairedDevices();
		} else {
			Log.d(TAG, "블루투스가 꺼져있기때문에 켜기 위하여 켜줍니다..!! ");
			// onActivityResult()에서 YES 이면 RESULT_OK, NO 이면 RESULT_CANCELED.
			// Intent enableIntent = new
			// Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			// startActivityForResult(enableIntent, REQUEST_ENABLE_BT);

			// 블루투스 꺼져있으면 자동으로 킨다.
			if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON
					|| mBluetoothAdapter.getState() == mBluetoothAdapter.STATE_ON) {
				Log.d(TAG, "disable() 블루투스를 끕니다.");
				mBluetoothAdapter.disable();
			} else {
				Log.d(TAG, "enable() 블루투스를 켭니다.");
				mBluetoothAdapter.enable();
			}
		}

		mArrayListDevice = new ArrayList<String>();
		deviceList = (ListView) findViewById(R.id.listView);
		adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, mArrayListDevice);
		deviceList.setAdapter(adapter);
		deviceList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				mBluetoothAdapter.cancelDiscovery();
				// 사용자가 선택한 항목의 내용
				String strItem = mArrayListDevice.get(position);
				Log.d(TAG, "" + strItem);
				// 사용자가 선택한 디바이스의 주소.
				int pos = strItem.indexOf("address : ");
				if (pos <= 0)
					return;
				String address = strItem.substring(pos + 10);
				Log.d(TAG, "address : " + address);

				if (mClientThread != null)
					return;
				// 디바이스 연결 전에 디바이스 검색을 중지한다.
				mBluetoothAdapter.cancelDiscovery();
				// 상대방 디바이스를 구한다.
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
				// 클라이언트 소켓 스레드 생성 & 시작
				mClientThread = new ClientThread(device);
				mClientThread.start();
			}
		});

		// mArrayChatList = new ArrayList<String>();
		// chatList = (ListView) findViewById(R.id.listView2);
		// chatAdapter = new ArrayAdapter<String>(this,
		// android.R.layout.simple_list_item_1, mArrayChatList);
		// chatList.setAdapter(chatAdapter);
		chatText = (TextView) findViewById(R.id.chatTextView);

		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
		filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		registerReceiver(mReceiver, filter);
		
		setDiscoverable();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth 활성화
				Log.d(TAG, "Bluetooth ON !!");
			} else {
				// 블루투스 비활성화. 취소 또는 오류
				Log.d(TAG, "Bluetooth OFF !!");
				finish();
			}
		}
	}

	// public synchronized void start(boolean isAndroid) {
	// if (mClientThread != null) {
	// mClientThread.cancel();
	// mClientThread = null;
	// }
	// if(mSocketThread != null) {
	// mSocketThread.cancel();
	// mSocketThread = null;
	// }
	// }

	/**
	 * 페어링 된 블루투스 디바이스 목록 가져온다.
	 */
	private void getPairedDevices() {

		if (mAcceptThread != null) {
			return;
		}
		// 서버 소켓 접속을 위한 스레드 생성 & 시작
		mAcceptThread = new AcceptThread();
		mAcceptThread.start();

		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
				.getBondedDevices();
		Log.d(TAG, "getDevices() 페어링된 디바이스 목록 가져오기..");
		// if there are paired devices
		if (pairedDevices.size() > 0) {
			// Loop through paired devices

			for (BluetoothDevice device : pairedDevices) {
				// Add the name and address to an array adapter to show in a
				// ListView
				Log.d(TAG, "========== scan device ==========");
				Log.d(TAG, "deviceName : [" + device.getName() + " ]");
				Log.d(TAG, "deviceAddress : [" + device.getAddress() + " ]");
				adapter.add("name : " + device.getName() + ", address : "
						+ device.getAddress());
				adapter.notifyDataSetChanged();
				// adapter.add("name : " + device.getName() + ", address : " +
				// device.getAddress());
			}
		}
		setDiscoverable();
	}

	/**
	 * 디바이스 검색
	 * 
	 * @return
	 */
	private boolean scanDevices() {
		Log.d(TAG, "scanDevices() 디바이스 검색...");
		adapter.clear();
		// 블루투스 OFF 이면..
		if (!mBluetoothAdapter.isEnabled())
			return false;
		// 검색중이면... 추가 검색 취소..
		if (mBluetoothAdapter.isDiscovering()) {
			mBluetoothAdapter.cancelDiscovery();
			return false;
		}

		// Start Searching ...
		mBluetoothAdapter.startDiscovery();
		return true;

	}

	public void pairDevice(BluetoothDevice device) {

	}

	/**다른 디바이스에게 자신을 검색 허용
	 * 검색 응답 모드(Discoverablility) SCAN_MODE_CONNECTABLE_DISCOVERABLE : 검색 응답 모드와
	 * 페이지 모드로 활성화되어 있어 언제든지 외부 블루투스 디바이스에 의하여 검색하고 연결 가능.
	 */
	private void setDiscoverable() {
		
		// 서버 소켓 접속을 위한 스레드 생성 & 시작
		mAcceptThread = new AcceptThread();
		mAcceptThread.start();
		
		// 현재 검색 허용 상태라면 return
		if (mBluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
			return;

		Intent discoverableIntent = new Intent(
				BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		// Intent discoverableIntent = new Intent(BluetoothAdapter.a);
		discoverableIntent.putExtra(
				BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0); // 120 ~ 300,
																	// 0이면 시간
																	// 무제한.
		startActivity(discoverableIntent);
	}

	/**
	 * 검색 과정에서 발견한 블루투스 디바이스는 'ACTION_FOUND' 액션명으로 브로드캐스트에 실어서 안드로이드 시스템 내부에
	 * 뿌려진다. 블루투스 디바이스의 정보들은 아래와 같은 인텐트의 엑스트라 이름으로 담아 보내진다. EXTRA_NAME : 주변
	 * 블루투스의 디바이스 이름 EXTRA_DEVICE : BluetoothDevice 클래스의 인스턴스 EXTRA_CLASS :
	 * BluetoothClass 클래스의 인스턴스
	 */
	// Create a BroadcastReceiver for ACTION_FOUND
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the intent
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// Add the name and address to an array adapter to show in a
				// view
				Log.d(TAG, "========== finds a device ==========");
				Log.d(TAG, "deviceName : [ " + device.getName() + " ]");
				Log.d(TAG, "deviceAddress : [ " + device.getAddress() + " ]");
				adapter.add("name : " + device.getName() + ", address : "
						+ device.getAddress());
				adapter.notifyDataSetChanged();
				// adapter.add("name : " + device.getName() + ", address : " +
				// device.getAddress());

			} else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
				Log.d(TAG, "ACTION_SCAN_MODE_CHANGED 검색 응답모드 변화.. ");
				int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE,
						BluetoothAdapter.ERROR);

				switch (mode) {
				case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
					Log.d(TAG,
							"SCAN_MODE_CONNECTABLE_DISCOVERABLE 검색, 페이지모드 활성화 ");
					break;
				case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
					Log.d(TAG,
							"SCAN_MODE_CONNECTABLE 검색 응답모드가 비활성화. 페이지 모드는 활성화.");
					break;
				case BluetoothAdapter.SCAN_MODE_NONE:
					Log.d(TAG, "SCAN_MODE_NONE 검색 응답 모드(검색, 페이지모드) 비활성화되어있습니다.");
					break;
				}
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
					.equals(action)) {
				// Finished Searching
				Log.d(TAG, "Finished Searcing Bluetooth Devices 디바이스 검색 완료.");
			} else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
				Log.d(TAG, "ACTION DISCOVERY STARTED 검색모드 시작...");
			} else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED
					.equals(action)) {
				Log.d(TAG, "ACTION STATE CHANGED");
			} else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
				Log.d(TAG, "블루투스 상태 변화 감시");
				Log.d(TAG, "=== BluetoothAdapter.ACTION_STATE_CHANGED ===");
				int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
						BLUETOOTH_STATE_UNKNOWN);

				String Message = null;

				switch (state) {
				case BluetoothAdapter.STATE_CONNECTED:
					Message = "STATE_CONNECTED 블루투스를 켰습니다.";
					break;
				case BluetoothAdapter.STATE_CONNECTING:
					Message = "STATE_CONNECTING 블루투스를 켜는중 ...";
					break;
				case BluetoothAdapter.STATE_DISCONNECTED:
					Message = "STATE_DISCONNECTED 블루투스를 껐습니다.";
					break;
				case BluetoothAdapter.STATE_DISCONNECTING:
					Message = "STATE_DISCONNECTING 블루투스를 끄는중...";
					break;
				case BluetoothAdapter.STATE_OFF:
					Message = "STATE_OFF 블루트스 꺼져있음..";
					break;
				case BluetoothAdapter.STATE_ON:
					Message = "STATE_ON 블루트스 켜짐..";
					break;
				case BluetoothAdapter.STATE_TURNING_OFF:
					Message = "STATE_TURNING_OFF 블루투스 끄는중..";
					break;
				case BluetoothAdapter.STATE_TURNING_ON:
					Message = "STATE_TURNING_ON 블루투스 켜는중..";
					break;
				default:
				}
				Log.d(TAG, "Message : [ " + Message + " ]");
			} else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
					// CONNECT
					Log.d(TAG, "========== BOND ==========");
					Log.d(TAG, "Name : " + device.getName() + ", Address : "
							+ device.getAddress());
				}
			} else {
				Log.d(TAG, "");
			}
		}
	};

	/**
	 * 클라이언트 소켓 생성을 위한 스레드
	 */
	private class ClientThread extends Thread {
		private BluetoothSocket mmClientSocket;

		// 원격 디바이스와 접속을 위한 클라이언트 소켓 생성
		public ClientThread(BluetoothDevice device) {
			try {
				Log.d(TAG, "Create Client Socket Android Device");
				mmClientSocket = device
						.createInsecureRfcommSocketToServiceRecord(UUID_ANDROID_DEVICE);
				// mmClientSocket =
				// device.createRfcommSocketToServiceRecord(UUID_ANDROID_DEVICE);
			} catch (IOException e) {
				e.printStackTrace();
				Log.d(TAG, "Create Client Socket Error");
				return;
			}
			// 블루투스 디바이스의 검색 취소
			if (mBluetoothAdapter.isDiscovering()) {
				mBluetoothAdapter.cancelDiscovery();
			}
		}

		public void run() {
			mBluetoothAdapter.cancelDiscovery();
			// 원격 디바이스와 접속 시도
			try {
				Log.d(TAG, "Connect to server");
				mmClientSocket.connect();
			} catch (IOException e) {
				Log.d(TAG, "Connect to server error");
				return;
			}

			// 원격 디바이스와 접속되었으면 데이터 송수신 스레드를 시작
			onConnected(mmClientSocket);
		}

		public void cancel() {
			try {
				Log.d(TAG, "Client Socket close");
				mmClientSocket.close();
			} catch (IOException e) {
				Log.d(TAG, "Client Socket close error");
			}
		}
	}

	/**
	 * 1. BluetoothServerSocekt 생성 listenUsingRfcommWithServiceRecord 2. 연결 요청에
	 * 대해 응답 accept() 3. 서버 소켓 닫음 close() // 한번에 하나의 연결만 가능
	 * 
	 * @author yong
	 *
	 */
	// 스레드를 생성하는 초기화 작업으로 서버 소켓을 생성
	private class AcceptThread extends Thread {
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread() {
			// Use a temporary object that is later assigned to mmServerSocket,
			// because mmServerSocket is final
			BluetoothServerSocket tmp = null;
			try {
				Log.d(TAG, "Get Server Socket Android Device");
				// tmp =
				// mBluetoothAdapter.listenUsingRfcommWithServiceRecord(BLUE_NAME,
				// UUID_ANDROID_DEVICE);
				tmp = mBluetoothAdapter
						.listenUsingInsecureRfcommWithServiceRecord(BLUE_NAME,
								UUID_ANDROID_DEVICE);
			} catch (IOException e) {
				Log.d(TAG, "Get Server Socket Error");
				e.printStackTrace();
			}
			mmServerSocket = tmp;
		}

		public void run() {
			BluetoothSocket socket = null;
			// Keep listening until exception occurs or a socket is returned
			while (true) {
				try {
					Log.d(TAG, "Socket Accept 서버소캣 수신 준비됨.");
					socket = mmServerSocket.accept();
				} catch (IOException e) {
					Log.d(TAG, "Socket Accept Error");
					break;
				}
				// 원격 디바이스와 접속되었으면 데이터 송수신 스레드를 시작
				onConnected(socket);

				if (socket != null) {
					// 클라이언트와의 통신은 별도 스레드로 실행시킨다.
				}
			}
		}

		// 계속 기다릴 수 없는 경우, 서버 소켓을 강제 종료
		public void cancel() {
			try {
				mmServerSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// 원격 디바이스와 접속되었으면 데이터 송수신 스레드를 시작
	public void onConnected(BluetoothSocket socket) {
		Log.d(TAG, "Socket Connected 원격 디바이스와 접속 완료. 데이터 송수신 스레드 시작..");

		// 데이터 송수신 스레드가 생성되어 있다면 삭제
		if (mSocketThread != null) {
			mSocketThread = null;
		}
		// 데이터 송수신 스레드를 시작
		mSocketThread = new SocketThread(socket);
		mSocketThread.start();
	}

	// 데이터 송수신 스레드
	private class SocketThread extends Thread {
		private final BluetoothSocket mmSocket; // 클라이언트 소켓
		private InputStream mmInStream; // 입력 스트림
		private OutputStream mmOutStream; // 출력 스트림

		public SocketThread(BluetoothSocket socket) {
			mmSocket = socket;

			// 입력 스트림과 출력 스트림을 구한다.
			try {
				Log.d(TAG, "Get Stream 입출력 스트림 커몬");
				mmInStream = socket.getInputStream();
				mmOutStream = socket.getOutputStream();
			} catch (IOException e) {
				e.printStackTrace();
				Log.d(TAG, "Get Stream Error");
			}
		}

		// 소켓에서 수신된 데이터를 화면에 표시한다.
		public void run() {
			byte[] buffer = new byte[1024];
			int bytes;

			while (true) {
				try {
					// 입력 스트림에서 데이터를 읽는다.
					bytes = mmInStream.read(buffer);
					String strBuf = new String(buffer, 0, bytes);
					Log.d(TAG, "Socket connected 입력 스트렘 데이터 읽기");
					Log.d(TAG, "TO : " + strBuf);
					// chatAdapter.add("TO : " + strBuf);
					// chatText.append("TO : " + strBuf + "\n");
					// chatAdapter.notifyDataSetChanged();
					SystemClock.sleep(1);
				} catch (IOException e) {
					e.printStackTrace();
					Log.d(TAG, "Socket disconnected");
					break;
				}
			}
		}

		// 데이터를 소켓으로 전송한다
		public void write(String strBuf) {
			try {
				// 출력 스트림에 데이터를 저장한다
				if (strBuf.length() > 0) {
					byte[] buffer = strBuf.getBytes();
					mmOutStream.write(buffer);
					Log.d(TAG, "데이터를 소켓으로 전송... ");
					Log.d(TAG, "ME : " + strBuf);
					// chatAdapter.add("ME : " + strBuf);
					// chatText.append("Me : " + strBuf + "\n");
					// chatAdapter.notifyDataSetChanged();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void cancel() {
			try {
				mmSocket.close();
				Log.d(TAG, "SocketThread ...  Socket Close");
			} catch (IOException e) {
				Log.d(TAG, "SocketThread ...  Socket Close Error");
			}
		}

	}

	// 앱이 종료될 때 디바이스 검색 중지
	public void onDestory() {
		super.onDestroy();
		// 디바이스 검색 중지
		unregisterReceiver(mReceiver);

		// 스레드를 종료
		if (mClientThread != null) {
			Log.d(TAG, "클라이언트 스레드 종료");
			mClientThread.cancel();
			mClientThread = null;
		}
		if (mAcceptThread != null) {
			Log.d(TAG, "서버 스레드 종료");
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		if (mSocketThread != null) {
			Log.d(TAG, "데이터 스레드 종료");
			mSocketThread = null;
			mSocketThread.cancel();
		}

	}

}
