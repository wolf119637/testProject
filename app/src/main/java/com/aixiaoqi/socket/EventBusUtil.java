package com.aixiaoqi.socket;

import org.greenrobot.eventbus.EventBus;

import de.blinkt.openvpn.model.ChangeConnectStatusEntity;
import de.blinkt.openvpn.model.ShowDeviceEntity;
import de.blinkt.openvpn.model.SimRegisterStatue;
import de.blinkt.openvpn.model.StateChangeEntity;

/**
 * Created by Administrator on 2017/2/9 0009.
 */

public class EventBusUtil {

    public static void simRegisterStatue(int regstatues) {
        SimRegisterStatue entity = new SimRegisterStatue();
        entity.setRigsterSimStatue(regstatues);
        EventBus.getDefault().post(entity);
    }

    public static void simStateChange(String registerType,boolean isopen) {
        StateChangeEntity entity = new StateChangeEntity();
        entity.setStateType(registerType);
        entity.setIsopen(isopen);
        EventBus.getDefault().post(entity);
    }
    public static void changeConnectStatus(String status, int statusDrawableInt) {
        ChangeConnectStatusEntity entity = new ChangeConnectStatusEntity();
        entity.setStatus(status);
        entity.setStatusDrawableInt(statusDrawableInt);
        EventBus.getDefault().post(entity);
    }

    public static void showDevice(boolean showDevice) {
        ShowDeviceEntity entity = new ShowDeviceEntity();
        entity.setShowDevice(showDevice);
        EventBus.getDefault().post(entity);
    }

}
