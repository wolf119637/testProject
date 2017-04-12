package de.blinkt.openvpn.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.com.aixiaoqi.R;
import de.blinkt.openvpn.activities.Base.BaseNetActivity;
import de.blinkt.openvpn.bluetooth.service.UartService;
import de.blinkt.openvpn.constant.BluetoothConstant;
import de.blinkt.openvpn.constant.Constant;
import de.blinkt.openvpn.constant.HttpConfigUrl;
import de.blinkt.openvpn.core.ICSOpenVPNApplication;
import de.blinkt.openvpn.http.CommonHttp;
import de.blinkt.openvpn.http.CreateHttpFactory;
import de.blinkt.openvpn.http.IsBindHttp;
import de.blinkt.openvpn.model.BluetoothMessageCallBackEntity;
import de.blinkt.openvpn.model.BluetoothModel;
import de.blinkt.openvpn.model.ServiceOperationEntity;
import de.blinkt.openvpn.util.CommonTools;
import de.blinkt.openvpn.util.SharedUtils;
import de.blinkt.openvpn.views.dialog.DialogBalance;
import de.blinkt.openvpn.views.dialog.DialogInterfaceTypeBase;

import static de.blinkt.openvpn.bluetooth.util.SendCommandToBluetooth.sendMessageToBlueTooth;
import static de.blinkt.openvpn.constant.Constant.UP_TO_POWER;
import static de.blinkt.openvpn.util.CommonTools.getBLETime;


public class BindDeviceActivity extends BaseNetActivity implements DialogInterfaceTypeBase {

	@BindView(R.id.stopTextView)
	TextView stopTextView;
	@BindView(R.id.connectedRelativeLayout)
	RelativeLayout connectedRelativeLayout;
	@BindView(R.id.tip_search)
	TextView tip_search;
	@BindView(R.id.search_bluetooth)
	TextView search_bluetooth;
	@BindView(R.id.findedImageView)
	ImageView findedImageView;
	@BindView(R.id.seekImageView)
	ImageView seekImageView;

	private Handler mHandler;
	private Handler findDeviceHandler;
	private HashSet<BluetoothModel> deviceSet;
	private BluetoothAdapter mBluetoothAdapter;
	private static final long SCAN_PERIOD = 120000; //120 seconds
	private String deviceAddress = "";
	SharedUtils utils = SharedUtils.getInstance();
	private DialogBalance noDevicedialog;
	private String TAG = "BindDeviceActivity";
	private UartService mService = ICSOpenVPNApplication.uartService;
	private String bracelettype;
	//设备名称：类型不同名称不同，分别有【unitoys、unibox】
	private String bluetoothName = Constant.UNITOYS;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {//没有发现设备
			CommonTools.showShortToast(this, getString(R.string.bluetooth_ble_not_support));
			finish();
			return;
		}

		bracelettype = getIntent().getStringExtra(MyDeviceActivity.BRACELETTYPE);

		final BluetoothManager bluetoothManager =
				(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null) {
			finish();
			return;
		}
		setContentView(R.layout.activity_bind_device);
		EventBus.getDefault().register(this);
		ButterKnife.bind(this);

		if (bracelettype != null && bracelettype.contains(MyDeviceActivity.UNIBOX)) {
			bluetoothName = Constant.UNIBOX;
			search_bluetooth.setText(getString(R.string.searching_unibox_strap));
			tip_search.setText(getString(R.string.please_makesure_bind));
		} else {
			bluetoothName = Constant.UNITOYS;
		}

		deviceSet = new HashSet<>();
		mHandler = new Handler();
		findDeviceHandler = new Handler();
		scanLeDevice(true);
		setAnimation();
	}

	private void setAnimation() {
		Animation anim = AnimationUtils.loadAnimation(this, R.anim.anim_rotate_seek);
		anim.setInterpolator(new LinearInterpolator());//代码设置插补器
		seekImageView.startAnimation(anim);
	}

	//查看选择设备类型
	private void afterConnDevice() {
		if (bracelettype != null) {
			if (bracelettype.contains(MyDeviceActivity.UNIBOX)) {
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						showIsBindLayout();
					}
				}, 2000);
			} else {
				finish();
			}
		}
	}

	private void scanLeDevice(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mService != null && mService.mConnectionState != UartService.STATE_CONNECTED && !isStartFindDeviceDelay) {
						mBluetoothAdapter.stopLeScan(mLeScanCallback);
						showDialog();
					}
				}
			}, SCAN_PERIOD);

			mBluetoothAdapter.startLeScan(mLeScanCallback);

		} else {
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
		}

	}

	private void showDialog() {
		//不能按返回键，只能二选其一
		noDevicedialog = new DialogBalance(BindDeviceActivity.this, BindDeviceActivity.this, R.layout.dialog_balance, 2);
//		noDevicedialog.setCanClickBack(false);
		if (bracelettype != null && bracelettype.contains(MyDeviceActivity.UNIBOX)) {
			noDevicedialog.changeText(getString(R.string.no_find_unibox), getResources().getString(R.string.retry));
		} else {
			noDevicedialog.changeText(getResources().getString(R.string.no_find_unitoys), getResources().getString(R.string.retry));
		}
	}

	protected void onPause() {
		super.onPause();
		scanLeDevice(false);
		if (noDevicedialog != null && noDevicedialog.getDialog() != null && noDevicedialog.getDialog().isShowing()) {
			noDevicedialog.getDialog().dismiss();
		}
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		deviceSet.clear();
		EventBus.getDefault().unregister(this);
	}

	//是否打开找到设备的计时器
	private boolean isStartFindDeviceDelay;
	private BluetoothAdapter.LeScanCallback mLeScanCallback =
			new BluetoothAdapter.LeScanCallback() {

				@Override
				public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {

							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (device.getName() == null) {
										return;
									}
									Log.i("test", "find the device:" + device.getName() + ",rssi :" + rssi);
									if (device.getName().contains(bluetoothName)) {
										BluetoothModel model = new BluetoothModel();
										model.setAddress(device.getAddress());
										model.setDiviceName(device.getName());
										model.setRssi(rssi);
										deviceSet.add(model);
										if (!isStartFindDeviceDelay) {
											findDeviceHandler.postDelayed(new Runnable() {
												@Override
												public void run() {
													List<BluetoothModel> infos =
															new ArrayList<>(deviceSet);
													Collections.sort(infos, new Comparator<BluetoothModel>() {
														@Override
														public int compare(BluetoothModel lhs, BluetoothModel rhs) {
															return rhs.getRssi() - lhs.getRssi();
														}
													});
													for (int i = 0; i < infos.size(); i++) {
														String id = infos.get(i).toString();
														Log.i(TAG, "排序后：" + id);
													}
													//排序后连接操作
													scanLeDevice(false);
													if (infos.size() == 0 || mService == null || mService.isConnecttingBlueTooth()) {
														CommonTools.showShortToast(BindDeviceActivity.this, getString(R.string.no_device_around));
														finish();
														return;
													}
													deviceAddress = infos.get(0).getAddress();
													utils.writeString(Constant.BRACELETNAME, infos.get(0).getDiviceName());
													createHttpRequest(HttpConfigUrl.COMTYPE_ISBIND_DEVICE, deviceAddress);
													isStartFindDeviceDelay = false;
													deviceSet.clear();
												}
											}, 5000);
											isStartFindDeviceDelay = true;
										}
									}
								}
							});
						}
					});
				}
			};


	@OnClick(R.id.stopTextView)
	public void onClick() {
		scanLeDevice(false);
		mService.disconnect();
		ICSOpenVPNApplication.isConnect = false;
		utils.delete(Constant.IMEI);
		utils.delete(Constant.BRACELETNAME);
		finish();
	}

	@Override
	public void rightComplete(int cmdType, CommonHttp object) {
		if (cmdType == HttpConfigUrl.COMTYPE_ISBIND_DEVICE) {
			IsBindHttp http = (IsBindHttp) object;
			if (http.getStatus() == 1 && http.getIsBindEntity() != null && http.getIsBindEntity().getBindStatus() == 0) {
				if (mService != null) {
					//判断无人连接后记录MAC地址
					utils.writeString(Constant.IMEI, deviceAddress);
					String braceletname = utils.readString(Constant.BRACELETNAME);
					if (!TextUtils.isEmpty(braceletname)) {
						if (braceletname.contains(MyDeviceActivity.UNITOYS)) {
							CreateHttpFactory.instanceHttp(BindDeviceActivity.this, HttpConfigUrl.COMTYPE_BIND_DEVICE
									, deviceAddress, "0", 0 + "");
						} else {
							mService.connect(deviceAddress);
						}
					}
				} else {
					CommonTools.showShortToast(BindDeviceActivity.this, getString(R.string.connect_failure));
					restartUartService();
					finish();
				}
			} else {
				CommonTools.showShortToast(this, "该设备已经绑定过了！");
				scanLeDevice(false);
				mService.disconnect();
				BluetoothConstant.IS_BIND = false;
				SharedUtils.getInstance().delete(Constant.IMEI);
				SharedUtils.getInstance().delete(Constant.BRACELETNAME);
				finish();
			}
		} else if (cmdType == HttpConfigUrl.COMTYPE_BIND_DEVICE) {
			Log.i(TAG, "绑定设备返回：" + object.getMsg() + ",返回码：" + object.getStatus());
			if (object.getStatus() == 1) {
				Log.i("test", "保存设备名成功");
				if (bluetoothName.contains(Constant.UNITOYS)) {
					mService.connect(deviceAddress);
				} else {
					connectedRelativeLayout.setVisibility(View.VISIBLE);
					new Handler().postDelayed(new Runnable() {
						@Override
						public void run() {
							finish();
						}
					}, 2000);
				}
				updateDeviceInfo();

			} else {
				CommonTools.showShortToast(this, object.getMsg());
			}
//			finish();
		}
	}

	//更新设备信息
	private void updateDeviceInfo() {
		//绑定完成更新设备信息
		if (utils == null)
			utils = SharedUtils.getInstance();

		createHttpRequest(HttpConfigUrl.COMTYPE_UPDATE_CONN_INFO, utils.readString(Constant.BRACELETVERSION),
				utils.readInt(Constant.BRACELETPOWER) + "", utils.readInt(Constant.BRACELETTYPE) + "");
	}

	private void restartUartService() {
		Log.i(TAG, "restart Uart服务");
		new Thread(new Runnable() {
			@Override
			public void run() {
				//关闭UartService服务
				ServiceOperationEntity serviceOperationEntity = new ServiceOperationEntity();
				serviceOperationEntity.setServiceName(UartService.class.getName());
				serviceOperationEntity.setOperationType(ServiceOperationEntity.REMOVE_SERVICE);
				EventBus.getDefault().post(serviceOperationEntity);
				CommonTools.delayTime(200);
				serviceOperationEntity.setOperationType(ServiceOperationEntity.CREATE_SERVICE);
				EventBus.getDefault().post(serviceOperationEntity);
			}
		}).start();
	}

	@Override
	public void dialogText(int type, String text) {
		if (type == 2) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					scanLeDevice(true);
				}
			}).start();
		} else {
			stopTextView.performClick();
		}
	}

	@Subscribe(threadMode = ThreadMode.MAIN)//ui线程
	public void onVersionEntity(BluetoothMessageCallBackEntity entity) {
		String type = entity.getBlueType();
		if (BluetoothConstant.BLUE_BIND_SUCCESS.equals(type)) {
			if (entity.isSuccess()) {
				Log.i(TAG, "蓝牙注册返回:" + entity.getBlueType() + ",参数：MEI：" + utils.readString(Constant.IMEI) + ",版本号：" + utils.readString(Constant.BRACELETVERSION));
				if (bluetoothName.contains(Constant.UNIBOX)) {
//					final BindDeviceHttp bindDevicehttp = new BindDeviceHttp();
//					new Thread(bindDevicehttp).start();
					CreateHttpFactory.instanceHttp(BindDeviceActivity.this, HttpConfigUrl.COMTYPE_BIND_DEVICE
							, deviceAddress, "0", 1 + "");
				}
				new Thread(new Runnable() {
					@Override
					public void run() {
						BluetoothConstant.IS_BIND = true;
						//更新时间操作
						sendMessageToBlueTooth(getBLETime());
						CommonTools.delayTime(500);
						//上电指令
						sendMessageToBlueTooth(UP_TO_POWER);
						if (!bluetoothName.contains(Constant.UNIBOX)) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									updateDeviceInfo();
									finish();
								}
							});
						}
					}
				}).start();
			} else {
				finish();
			}

		} else if (BluetoothConstant.BLUE_BIND.equals(type)) {
			afterConnDevice();
		}
	}

	private void showIsBindLayout() {
		tip_search.setText(getString(R.string.finded_bracelet));
		search_bluetooth.setText(getString(R.string.click_bracelet_sure_bind));
		seekImageView.clearAnimation();
		if (bracelettype != null) {
//			pointProgressBar.setVisibility(View.GONE);
//			findedImageView.setVisibility(View.VISIBLE);
			if (bracelettype.equals(MyDeviceActivity.UNITOYS)) {
//				findedImageView.setBackground(ContextCompat.getDrawable(this, R.drawable.unitoy_finded));
			} else {
//				findedImageView.setBackground(ContextCompat.getDrawable(this, R.drawable.unibox_finded));
				seekImageView.setBackgroundResource(R.drawable.seek_finish_pic);
				findedImageView.setVisibility(View.VISIBLE);
				Animation anim = AnimationUtils.loadAnimation(this, R.anim.anim_trans_seek_over);
				findedImageView.startAnimation(anim);

			}
		}
	}

}
