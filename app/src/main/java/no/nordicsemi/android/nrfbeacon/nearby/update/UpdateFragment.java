/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package no.nordicsemi.android.nrfbeacon.nearby.update;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.sample.libeddystoneeidr.EddystoneEidrGenerator;
import com.google.sample.libproximitybeacon.Project;
import com.google.sample.libproximitybeacon.ProximityBeaconImpl;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import no.nordicsemi.android.nrfbeacon.nearby.MainActivity;
import no.nordicsemi.android.nrfbeacon.nearby.R;
import no.nordicsemi.android.nrfbeacon.nearby.UpdateService;
import no.nordicsemi.android.nrfbeacon.nearby.common.BaseFragment;
import no.nordicsemi.android.nrfbeacon.nearby.scanner.ScannerFragment;
import no.nordicsemi.android.nrfbeacon.nearby.scanner.ScannerFragmentListener;
import no.nordicsemi.android.nrfbeacon.nearby.settings.UpdateSettingsActivity;
import no.nordicsemi.android.nrfbeacon.nearby.util.NetworkUtils;
import no.nordicsemi.android.nrfbeacon.nearby.util.ParserUtils;
import no.nordicsemi.android.nrfbeacon.nearby.util.RefreshAccessTokenTask;
import no.nordicsemi.android.nrfbeacon.nearby.util.RequestAccessTokenTask;
import no.nordicsemi.android.nrfbeacon.nearby.util.Utils;
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence;
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig;

public class UpdateFragment extends BaseFragment implements ScannerFragmentListener,
        UnlockBeaconDialogFragment.OnBeaconUnlockListener,
        ReadWriteAdvertisementSlotDialogFragment.OnReadWriteAdvertisementSlotListener,
        LockStateDialogFragment.OnLockStateListener,
        RegisterBeaconDialogFragment.OnBeaconRegisteredListener,
        CreateAttachmentDialogFragment.OnAttachmentCreatedListener,
        RadioTxPowerDialogFragment.OnRadioTxPowerListener,
        ClearSlotDialogFragment.OnClearSlotListener,
        AdvertisingIntervalDialogFragment.OnAdvertisingIntervalListener,
        ProximityApiErrorDialogFragment.OnHandleError,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleSignInDialogFragment.OnSignInCompletion,
        ProjectSelectionDialogFragment.OnProjectSelectionListener{
    /**
     * The UUID of a service in the beacon advertising packet when in Config mode. This may be <code>null</code> if no filter required.
     */
    private static final UUID EDDYSTONE_GATT_CONFIG_SERVICE_UUID = UUID.fromString("A3C87500-8ED3-4BDF-8A39-A01BEBEDE295");
    private static final String AUTH_SCOPE_PROXIMITY_API = "oauth2:https://www.googleapis.com/auth/userlocation.beacon.registry";
    private static final String ACCOUNT_NAME_PREF = "userAccount";
    private static final String SHARED_PREFS_NAME = "nrfNearbyInfo";
    private static final String TAG = "BEACON";

    private static final int LOCKED = 0x00;
    private static final int UNLOCKED = 0x01;
    private static final int UNLOCKED_AUTOMATIC_RELOCK_DISABLED = 0x02;
    private static final int IS_VARIABLE_TX_POWER_SUPPORTED = 0x02;
    private static final int EMPTY_SLOT = -1;
    private static final int TYPE_UID = 0x00;
    private static final int TYPE_URL = 0x10;
    private static final int TYPE_TLM = 0x20;
    private static final int TYPE_EID = 0x30;

    public static final String TOOLBAR_BUTTON_HELP = "TOOLBAR_BUTTON_HELP";

    private TextView mBeaconHelp;

    private Button mConnectButton;
    private LinearLayout mBeaconConfigurationContainer;
    private LinearLayout mFrameTypeContainer;
    private LinearLayout mUidDataContainer;
    private LinearLayout mUrlDataContainer;
    private LinearLayout mTlmDataContainer;
    private LinearLayout mEtlmDataContainer;
    private LinearLayout mEidDataContainer;
    private ImageView mShowBroadcastCapabilities;
    private ImageView mEditSlot;
    private ImageView mEditAdvInterval;
    private ImageView mEditRadioTxPower;
    private ImageView mShowSlotInfo;
    private ImageView mClearActiveSlot;
    private ImageView mRefreshActiveSlot;
    private TextView mFrameTypeView;
    private Spinner mActiveSlots;
    private TextView mNamespaceId;
    private TextView mInstanceId;
    private TextView mUrl;
    private TextView mEtlm;
    private TextView mEtlmSalt;
    private TextView mEtlmMessageIntCheck;
    private TextView mVoltage;
    private TextView mTemperature;
    private TextView mPduCount;
    private TextView mTimeSinceReboot;
    private TextView mTimerExponent;
    private TextView mClockValue;
    private TextView mEid;
    private TextView mAdvertisingInterval;
    private TextView mRadioTxPower;

    private ProgressDialog mProgressDialog;
    private ProgressDialog mConnectionProgressDialog;

    private UpdateService.ServiceBinder mBinder;
    private ProximityBeaconImpl mProximityApiClient;
    private boolean mBounnd;
    private boolean mIsBeaconLocked = true;
    private boolean mIsActiveSlotAdapterUpdated = false;
    private boolean mRemainConnectable = false;
    private boolean mEikGenerated = false;

    private byte[] mBroadcastCapabilities;
    private byte[] mRwAdvertisingSlot;
    private byte[] mRadioTxPowerData;
    private byte[] mDecryptedIdentityKey;
    private byte[] mEncryptedIdentityKey;
    private byte[] mServiceEcdhKey;
    private byte[] mBeaconPublicEcdhKey = null;
    private byte[] mUnlockCode = null;

    private ArrayList<String> mActiveSlotsTypes;

    private int mActiveSlot;
    private int mCurrentLockState;
    private int mAdvancedAdvTxPower;
    private int mFrameType;
    private EddystoneEidrGenerator generator;
    private Handler mProgressDialogHandler;
    private String mAccountName;
    private Context mContext;
    private Project mProject;
    private GoogleApiClient mGoogleApiClient;
    private boolean mSilentSignInFailed = false;
    private boolean mRegisterBeacon = false;
    private byte []  mEidSlotData;
    private boolean mConfigureEidSlot = false;
    private boolean mCreateShowcase = false;
    private String mServerAuthCode = null;
    private ShowcaseConfig mShowcaseConfig = null;
    private MaterialShowcaseSequence materialShowcaseSequence = null;

    public void ensurePermissionGranted(final String[] permissions) {
        ensurePermission(permissions);
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            final UpdateService.ServiceBinder binder = mBinder = (UpdateService.ServiceBinder) service;
            final int state = binder.getState();
            Log.v(TAG, "Connection state on rotation: " + state);
            switch (state) {
                case UpdateService.STATE_DISCONNECTED:
                    binder.connect();
                    break;
                case UpdateService.STATE_CONNECTED:
                    if (!hasOptionsMenu()) {
                        setHasOptionsMenu(true);
                        getActivity().invalidateOptionsMenu();
                    }
                    mConnectButton.setText(R.string.action_disconnect);
                    final Integer lockState = binder.getLockState();
                    if (lockState != null)
                        updateUiForBeacons(BluetoothGatt.STATE_CONNECTED, lockState);

                    switch(lockState){
                        case UNLOCKED:
                        case UNLOCKED_AUTOMATIC_RELOCK_DISABLED:
                            readCharacteristicsOnRotation(binder);
                            break;
                    }
                    break;
                case UpdateService.STATE_CONNECTING:
                    if(mConnectionProgressDialog != null) {
                        mConnectionProgressDialog.setTitle(getString(R.string.prog_dialog_connect_title));
                        mConnectionProgressDialog.setMessage(getString(R.string.prog_dialog_connect_message));
                        mProgressDialog.show();
                    }
                    break;
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            mBinder = null;
            mIsActiveSlotAdapterUpdated = false;
        }
    };

    private byte[] mChallenge;
    private BroadcastReceiver mServiceBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final Activity activity = getActivity();
            if (activity == null || !isResumed())
                return;

            final String action = intent.getAction();

            if (UpdateService.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(UpdateService.EXTRA_DATA, UpdateService.STATE_DISCONNECTED);

                switch (state) {
                    case UpdateService.STATE_DISCONNECTED:
                        mConnectButton.setText(R.string.action_connect);
                        mConnectButton.setEnabled(true);

                        final Intent service = new Intent(activity, UpdateService.class);
                        if(mBounnd)
                            activity.unbindService(mServiceConnection);
                        activity.stopService(service);
                        mBinder = null;
                        mBounnd = false;
                        if (mProgressDialog != null && mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                            mProgressDialogHandler.removeCallbacks(mRunnableHandler);
                        }

                        if (mConnectionProgressDialog != null && mConnectionProgressDialog.isShowing()) {
                            mConnectionProgressDialog.dismiss();
                        }

                        mEikGenerated = false;
                        mIsBeaconLocked = true;
                        updateUiForBeacons(UpdateService.STATE_DISCONNECTED, UpdateService.LOCKED);
                        updateUiOnDisconnection();
                        mUnlockCode = null;
                        mShowcaseConfig = null;
                        materialShowcaseSequence = null;
                        break;
                    case UpdateService.STATE_CONNECTED:
                        mConnectButton.setText(R.string.action_disconnect);
                        mConnectButton.setEnabled(true);
                        break;
                    case UpdateService.STATE_DISCONNECTING:
                    case UpdateService.STATE_CONNECTING:
                        mConnectButton.setEnabled(false);
                        break;
                }
            } else if (UpdateService.ACTION_UNLOCK_BEACON.equals(action)) {
                if (mConnectionProgressDialog != null && mConnectionProgressDialog.isShowing()) {
                    mConnectionProgressDialog.dismiss();
                }
                final byte[] challenge = intent.getByteArrayExtra(UpdateService.EXTRA_DATA);
                if (challenge != null && challenge.length == 16) {
                    final String unlockMessage;
                    //Checking and storing the challenge to ensure if the initial unlock attempt failed to  display an unlock failure.
                    if(mChallenge == null) {
                        mChallenge = challenge;
                        unlockMessage = getString(R.string.unlock_beacon_message);
                    } else unlockMessage = getString(R.string.incorrect_unlock_key);

                    UnlockBeaconDialogFragment unlockBeaconDialogFragment = UnlockBeaconDialogFragment.newInstance(challenge, unlockMessage);
                    unlockBeaconDialogFragment.show(getChildFragmentManager(), null);
                } else {
                    //showToast(getString(R.string.error_challenge));
                }

            } else if (UpdateService.ACTION_DISSMISS_UNLOCK.equals(action)) {
                setHasOptionsMenu(true);
                getActivity().invalidateOptionsMenu();
                if (mConnectionProgressDialog != null && mConnectionProgressDialog.isShowing()) {
                    mConnectionProgressDialog.setTitle(getString(R.string.read_all_slots_title));
                    mConnectionProgressDialog.setMessage(getString(R.string.read_all_slots));
                    mConnectionProgressDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.INVISIBLE);
                }

                /*if(!mCreateShowcase){
                    createShowcaseSequence();
                }*/

            } else if (UpdateService.ACTION_BROADCAST_CAPABILITIES.equals(action)) {
                mBroadcastCapabilities = intent.getByteArrayExtra(UpdateService.EXTRA_DATA);
                if (mBroadcastCapabilities != null) {
                    Log.v("BEACON", "Broadcast capabilities");
                    int mMaxSupportedSlots = ParserUtils.getIntValue(mBroadcastCapabilities, 1, BluetoothGattCharacteristic.FORMAT_UINT8);
                    updateActiveSlotSpinner(mMaxSupportedSlots);
                }
            } else if (UpdateService.ACTION_ACTIVE_SLOT.equals(action)) {
                mActiveSlot = intent.getIntExtra(UpdateService.EXTRA_DATA, 0);
                Log.v("BEACON", "Active slot: " + mActiveSlot);
            } else if (UpdateService.ACTION_ADVERTISING_INTERVAL.equals(action)) {
                final int advInterval = intent.getIntExtra(UpdateService.EXTRA_DATA, 0);
                mAdvertisingInterval.setText(String.valueOf(advInterval) + " ms");
            } else if (UpdateService.ACTION_RADIO_TX_POWER.equals(action)) {
                final byte[] radioTxPower = intent.getByteArrayExtra(UpdateService.EXTRA_DATA);
                mRadioTxPowerData = radioTxPower;
                mRadioTxPower.setText(ParserUtils.parse(radioTxPower, 0, radioTxPower.length, getString(R.string.update_rssi_unit)));
            } else if (UpdateService.ACTION_ADVANCED_ADVERTISED_TX_POWER.equals(action)) {
                mAdvancedAdvTxPower = intent.getIntExtra(UpdateService.EXTRA_DATA, 0);
            } else if (UpdateService.ACTION_LOCK_STATE.equals(action)) {
                updateUiForBeacons(BluetoothProfile.STATE_CONNECTED, intent.getIntExtra(UpdateService.EXTRA_DATA, 0));
            } else if (UpdateService.ACTION_ECDH_KEY.equals(action)) {
                final byte[] data = intent.getByteArrayExtra(UpdateService.EXTRA_DATA);
                if (!mEikGenerated && data.length == 32) {
                    final String ecdhKey = ParserUtils.bytesToHex(data, 0, 32, false);
                    mBeaconPublicEcdhKey = new byte[32];
                    ParserUtils.setByteArrayValue(mBeaconPublicEcdhKey, 0, ecdhKey);
                }
            } else if (UpdateService.ACTION_EID_IDENTITY_KEY.equals(action)) {
                byte[] data = intent.getByteArrayExtra(UpdateService.EXTRA_DATA);
                mEncryptedIdentityKey = data;
                if(mUnlockCode != null) {
                    data = ParserUtils.aes128decrypt(data, new SecretKeySpec(mUnlockCode, "AES"));
                    mDecryptedIdentityKey = data;
                } else {
                    if (mBinder != null ){
                        if(mUnlockCode == null) {
                            UnlockBeaconDialogFragment unlockBeaconDialogFragment = UnlockBeaconDialogFragment.newInstance(null, getString(R.string.unlock_beacon_for_identity_key));
                            unlockBeaconDialogFragment.show(getChildFragmentManager(), null);
                        }
                    }
                }
            } else if (UpdateService.ACTION_READ_WRITE_ADV_SLOT.equals(action)) {
                mFrameType = intent.getIntExtra(UpdateService.EXTRA_FRAME_TYPE, -1);
                updateUiWithFrameType(intent);
                if (mProgressDialog != null && mProgressDialog.isShowing())
                    mProgressDialog.dismiss();
            } else if (UpdateService.ACTION_ADVANCED_FACTORY_RESET.equals(action)) {
                //advanced factory reset is not implemented on the latest Nordic Eddystone firmware
            } else if (UpdateService.ACTION_ADVANCED_REMAIN_CONNECTABLE.equals(action)) {
                if (mConnectionProgressDialog != null && mConnectionProgressDialog.isShowing()) {
                    mConnectionProgressDialog.dismiss();
                }
                mRemainConnectable = intent.getExtras().getBoolean(UpdateService.EXTRA_DATA);
                Log.v(TAG, "Remain connectable: " + mRemainConnectable);
                //createShowcaseSequence();
                if(mUnlockCode != null)
                    materialShowcaseSequence.start();
                    //createShowcaseSequence();
            } else if (UpdateService.ACTION_BROADCAST_ALL_SLOT_INFO.equals(action)) {
                mActiveSlotsTypes = intent.getStringArrayListExtra(UpdateService.EXTRA_DATA);
                Log.v(TAG, "SLot info list size: " + mActiveSlotsTypes.size());
            } else if (UpdateService.ACTION_DONE.equals(action)) {
                //final boolean advanced = intent.getBooleanExtra(UpdateService.EXTRA_DATA, false);
            } else if (UpdateService.ACTION_GATT_ERROR.equals(action)) {
                final int error = intent.getIntExtra(UpdateService.EXTRA_DATA, 0);
                switch (error) {
                    case UpdateService.ERROR_UNSUPPORTED_DEVICE:
                        showToast(getString(R.string.update_error_device_not_supported));
                        break;
                    default:
                        showToast(getString(R.string.update_error_other, error));
                        break;
                }
                mBinder.disconnectAndClose();
            }
        }
    };

    private void readCharacteristicsOnRotation(final UpdateService.ServiceBinder binder){
        mActiveSlotsTypes = binder.getAllSlotInformation();

        mBroadcastCapabilities = mBinder.getBroadcastCapabilities();
        if (mBroadcastCapabilities != null) {
            Log.v("BEACON", "Broadcast capabilities");
            int mMaxSupportedSlots = ParserUtils.getIntValue(mBroadcastCapabilities, 1, BluetoothGattCharacteristic.FORMAT_UINT8);
            updateActiveSlotSpinner(mMaxSupportedSlots);
        }

        mActiveSlot = binder.getActiveSlot();
        if (mActiveSlot > -1) {
            mIsActiveSlotAdapterUpdated = true;
            mActiveSlots.setSelection(mActiveSlot);
        }

        final Integer advertisingInterval = binder.getAdvInterval();
        if (advertisingInterval != null)
            mAdvertisingInterval.setText(String.valueOf(advertisingInterval));

        final Integer radioTxPower = binder.getRadioTxPower();
        if (radioTxPower != null)
            mRadioTxPower.setText(String.valueOf(radioTxPower));

        /*final Integer advanceAdvInterval = binder.getAdvancedAdvertisedTxPower();
        if (advanceAdvInterval != null) {

        }//update ui*/

        final byte[] beaconPublicEcdhKey = binder.getBeaconPublicEcdhKey();
        if (beaconPublicEcdhKey != null && beaconPublicEcdhKey.length == 32) {
            final String ecdhKey = ParserUtils.bytesToHex(beaconPublicEcdhKey, 0, 32, false);
            mBeaconPublicEcdhKey = new byte[32];
            ParserUtils.setByteArrayValue(mBeaconPublicEcdhKey, 0, ecdhKey);
        }

        final byte[] encryptedIdentityKey = binder.getIdentityKey();
        if (encryptedIdentityKey != null && encryptedIdentityKey.length == 16) {
            mEncryptedIdentityKey = encryptedIdentityKey;
            mUnlockCode = binder.getBeaconLockCode();
            if (mUnlockCode != null) {
                ParserUtils.aes128decrypt(encryptedIdentityKey, new SecretKeySpec(mUnlockCode, "AES"));
                mDecryptedIdentityKey = encryptedIdentityKey;
            }
        }

        final byte[] readWriteAdvSlot = binder.getReadWriteAdvSlotData();
        if (readWriteAdvSlot != null && readWriteAdvSlot.length > 0) {
            mRwAdvertisingSlot = readWriteAdvSlot;
            mFrameType = ParserUtils.getIntValue(readWriteAdvSlot, 0, BluetoothGattCharacteristic.FORMAT_UINT8);
            updateUiWithFrameType(readWriteAdvSlot);
        }
    }

    private void updateUiWithFrameType(Intent intent) {
        switch (mFrameType) {
            case TYPE_UID:
                mFrameTypeView.setText(getString(R.string.type_uid));
                final String namespaceId = intent.getExtras().getString(UpdateService.EXTRA_NAMESPACE_ID, "");
                final String instanceId = intent.getExtras().getString(UpdateService.EXTRA_INSTANCE_ID, "");
                mRwAdvertisingSlot = intent.getByteArrayExtra(UpdateService.EXTRA_DATA);
                mUidDataContainer.setVisibility(View.VISIBLE);
                mUrlDataContainer.setVisibility(View.GONE);
                mTlmDataContainer.setVisibility(View.GONE);
                mEtlmDataContainer.setVisibility(View.GONE);
                mEidDataContainer.setVisibility(View.GONE);
                mNamespaceId.setText(namespaceId);
                mInstanceId.setText(instanceId);
                break;

            case TYPE_URL:
                mFrameTypeView.setText(getString(R.string.type_url));
                final String url = intent.getExtras().getString(UpdateService.EXTRA_URL, "");
                mRwAdvertisingSlot = intent.getByteArrayExtra(UpdateService.EXTRA_DATA);
                mUrlDataContainer.setVisibility(View.VISIBLE);
                mUidDataContainer.setVisibility(View.GONE);
                mTlmDataContainer.setVisibility(View.GONE);
                mEtlmDataContainer.setVisibility(View.GONE);
                mEidDataContainer.setVisibility(View.GONE);

                final SpannableString urlAttachment = new SpannableString(url);
                urlAttachment.setSpan(new UnderlineSpan(), 0, url.length(), 0);
                mUrl.setText(urlAttachment);
                //mUrl.setText(url);
                break;

            case TYPE_TLM:
                mEidDataContainer.setVisibility(View.GONE);
                mUrlDataContainer.setVisibility(View.GONE);
                mUidDataContainer.setVisibility(View.GONE);

                mRwAdvertisingSlot = intent.getByteArrayExtra(UpdateService.EXTRA_DATA);
                final boolean containsEid = mActiveSlotsTypes.contains(getString(R.string.type_eid));
                if (containsEid) {
                    mTlmDataContainer.setVisibility(View.GONE);
                    mEtlmDataContainer.setVisibility(View.VISIBLE);
                    mFrameTypeView.setText(getString(R.string.type_etlm));
                    final String etlm = ParserUtils.bytesToHex(mRwAdvertisingSlot, 2, 12, true);
                    final String salt = ParserUtils.bytesToHex(mRwAdvertisingSlot, 14, 2, true);
                    final String messageIntegrityCheck = ParserUtils.bytesToHex(mRwAdvertisingSlot, 16, 2, true);

                    mEtlm.setText(etlm);
                    mEtlmSalt.setText(salt);
                    mEtlmMessageIntCheck.setText(messageIntegrityCheck);
                } else {
                    mFrameTypeView.setText(getString(R.string.type_tlm));
                    final String batteryVoltage = intent.getExtras().getString(UpdateService.EXTRA_VOLTAGE, "");
                    final String beaconTemperature = intent.getExtras().getString(UpdateService.EXTRA_BEACON_TEMPERATURE, "");
                    final String pduCount = intent.getExtras().getString(UpdateService.EXTRA_PDU_COUNT, "");
                    final String timeSinceBoot = intent.getExtras().getString(UpdateService.EXTRA_TIME_SINCE_BOOT, "");
                    final long timeSinceBootInMs = Long.valueOf(timeSinceBoot);
                    final Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(timeSinceBootInMs);
                    calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
                    StringBuilder builder = new StringBuilder();
                    if (timeSinceBootInMs < 86400000L)
                        builder.append(String.format(Locale.US, "%1$tR:%1$tS.%1$tL", calendar));
                    else if (timeSinceBootInMs < 86400000L * 2){
                        builder.append(String.format(Locale.US, "1 day + %1$tR:%1$tS.%1$tL", calendar));
                    } else {
                        final int days = (int) (timeSinceBootInMs / 86400000L);
                        builder.append(String.format(Locale.US, "%1$d days + %2$tR:%2$tS.%2$tL", days, calendar));
                    }

                    mEtlmDataContainer.setVisibility(View.GONE);
                    mTlmDataContainer.setVisibility(View.VISIBLE);
                    mVoltage.setText(batteryVoltage);
                    mTemperature.setText(beaconTemperature);
                    mPduCount.setText(pduCount);
                    mTimeSinceReboot.setText(builder.toString());
                }
                break;

            case TYPE_EID:
                mFrameTypeView.setText(getString(R.string.type_eid));
                final String timerExponent = intent.getExtras().getString(UpdateService.EXTRA_TIMER_EXPONENT);
                final String clockValue = intent.getExtras().getString(UpdateService.EXTRA_CLOCK_VALUE);
                final String eid = intent.getExtras().getString(UpdateService.EXTRA_EID, "");
                mRwAdvertisingSlot = intent.getByteArrayExtra(UpdateService.EXTRA_DATA);

                mEidDataContainer.setVisibility(View.VISIBLE);
                mUrlDataContainer.setVisibility(View.GONE);
                mUidDataContainer.setVisibility(View.GONE);
                mTlmDataContainer.setVisibility(View.GONE);
                mEtlmDataContainer.setVisibility(View.GONE);
                mTimerExponent.setText(timerExponent);
                mClockValue.setText(clockValue);
                mEid.setText(eid);
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                    mProgressDialogHandler.removeCallbacks(mRunnableHandler);
                }
                break;
            case EMPTY_SLOT:
                mFrameTypeView.setText(getString(R.string.slot_state_empty));
                mRwAdvertisingSlot = intent.getByteArrayExtra(UpdateService.EXTRA_DATA);
                mUrlDataContainer.setVisibility(View.GONE);
                mUidDataContainer.setVisibility(View.GONE);
                mTlmDataContainer.setVisibility(View.GONE);
                mEtlmDataContainer.setVisibility(View.GONE);
                mEidDataContainer.setVisibility(View.GONE);
                break;
        }
    }

    private void updateUiWithFrameType(final byte[] readWriteAdvSlot) {
        switch (mFrameType) {
            case TYPE_UID:
                mFrameTypeView.setText(getString(R.string.type_uid));
                final String namespaceId = ParserUtils.bytesToHex(readWriteAdvSlot, 2, 10, true);
                final String instanceId = ParserUtils.bytesToHex(readWriteAdvSlot, 12, 6, true);
                mUidDataContainer.setVisibility(View.VISIBLE);
                mUrlDataContainer.setVisibility(View.GONE);
                mTlmDataContainer.setVisibility(View.GONE);
                mEtlmDataContainer.setVisibility(View.GONE);
                mEidDataContainer.setVisibility(View.GONE);
                mNamespaceId.setText(namespaceId);
                mInstanceId.setText(instanceId);
                break;

            case TYPE_URL:
                mFrameTypeView.setText(getString(R.string.type_url));
                final String url = ParserUtils.decodeUri(readWriteAdvSlot, 2, readWriteAdvSlot.length - 2);
                mUrlDataContainer.setVisibility(View.VISIBLE);
                mUidDataContainer.setVisibility(View.GONE);
                mTlmDataContainer.setVisibility(View.GONE);
                mEtlmDataContainer.setVisibility(View.GONE);
                mEidDataContainer.setVisibility(View.GONE);

                final SpannableString urlAttachment = new SpannableString(url);
                if(url != null) {
                    urlAttachment.setSpan(new UnderlineSpan(), 0, url.length(), 0);
                    mUrl.setText(urlAttachment);
                }
                //mUrl.setText(url);
                break;

            case TYPE_TLM:
                mEidDataContainer.setVisibility(View.GONE);
                mUrlDataContainer.setVisibility(View.GONE);
                mUidDataContainer.setVisibility(View.GONE);
                final boolean containsEid = mActiveSlotsTypes.contains(getString(R.string.type_eid));
                if(containsEid){
                    mEtlmDataContainer.setVisibility(View.VISIBLE);
                    mFrameTypeView.setText(getString(R.string.type_etlm));
                    final String etlm = ParserUtils.bytesToHex(readWriteAdvSlot, 2, 12, true);
                    final String salt = ParserUtils.bytesToHex(readWriteAdvSlot, 14, 2, true);
                    final String messageIntegrityCheck = ParserUtils.bytesToHex(readWriteAdvSlot, 16, 2, true);

                    mEtlm.setText(etlm);
                    mEtlmSalt.setText(salt);
                    mEtlmMessageIntCheck.setText(messageIntegrityCheck);


                } else {
                    mFrameTypeView.setText(getString(R.string.type_tlm));
                    mTlmDataContainer.setVisibility(View.VISIBLE);
                    final int voltage = ParserUtils.decodeUint16BigEndian(readWriteAdvSlot, 2);
                    if (voltage > 0) {
                        mVoltage.setText(String.valueOf(voltage));
                    } else {
                        mVoltage.setText(getString(R.string.batt_voltage_unsupported));
                    }

                    final float temp = ParserUtils.decode88FixedPointNotation(readWriteAdvSlot, 4);
                    if (temp > -128.0f)
                        mTemperature.setText(String.valueOf(temp));
                    else
                        mTemperature.setText(getString(R.string.temperature_unsupported));

                    final String pduCount = String.valueOf(ParserUtils.decodeUint32BigEndian(readWriteAdvSlot, 6));
                    final String timeSinceBoot = String.valueOf(ParserUtils.decodeUint32BigEndian(readWriteAdvSlot, 10) * 100);

                    final long timeSinceBootInMs = Long.valueOf(timeSinceBoot);
                    final Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(timeSinceBootInMs);
                    calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
                    StringBuilder builder = new StringBuilder();
                    if (timeSinceBootInMs < 86400000L)
                        builder.append(String.format(Locale.US, "%1$tR:%1$tS.%1$tL", calendar));
                    else if (timeSinceBootInMs < 86400000L * 2){
                        builder.append(String.format(Locale.US, "1 day + %1$tR:%1$tS.%1$tL", calendar));
                    } else {
                        final int days = (int) (timeSinceBootInMs / 86400000L);
                        builder.append(String.format(Locale.US, "%1$d days + %2$tR:%2$tS.%2$tL", days, calendar));
                    }

                    mPduCount.setText(pduCount);
                    mTimeSinceReboot.setText(builder.toString());
                }

                break;

            case TYPE_EID:
                mFrameTypeView.setText(getString(R.string.type_eid));
                final String timerExponent = String.valueOf(ParserUtils.getIntValue(readWriteAdvSlot, 1, BluetoothGattCharacteristic.FORMAT_UINT8));
                final String clockValue = String.valueOf(ParserUtils.getIntValue(readWriteAdvSlot, 2, ParserUtils.FORMAT_UINT32_BIG_INDIAN));
                final String eid = String.valueOf(ParserUtils.bytesToHex(readWriteAdvSlot, 6, 8, true));
                mEidDataContainer.setVisibility(View.VISIBLE);
                mUrlDataContainer.setVisibility(View.GONE);
                mUidDataContainer.setVisibility(View.GONE);
                mTlmDataContainer.setVisibility(View.GONE);
                mEtlmDataContainer.setVisibility(View.GONE);
                mTimerExponent.setText(timerExponent);
                mClockValue.setText(clockValue);
                mEid.setText(eid);
                break;
            case EMPTY_SLOT:
                mFrameTypeView.setText(getString(R.string.slot_state_empty));
                mUrlDataContainer.setVisibility(View.GONE);
                mUidDataContainer.setVisibility(View.GONE);
                mTlmDataContainer.setVisibility(View.GONE);
                mEtlmDataContainer.setVisibility(View.GONE);
                mEidDataContainer.setVisibility(View.GONE);
                break;
        }
    }

    private void updateUiOnDisconnection() {
        mFrameTypeView.setText("");
        mNamespaceId.setText("");
        mInstanceId.setText("");
        mFrameTypeView.setText("");
        mUrl.setText("");
        mFrameTypeView.setText("");
        mEtlm.setText("");
        mEtlmSalt.setText("");
        mEtlmMessageIntCheck.setText("");
        mFrameTypeView.setText("");
        mVoltage.setText("");
        mTemperature.setText("");
        mPduCount.setText("");
        mTimeSinceReboot.setText("");
        mFrameTypeView.setText("");
        mTimerExponent.setText("");
        mClockValue.setText("");
        mEid.setText("");
        mFrameTypeView.setText("");
        mUrlDataContainer.setVisibility(View.GONE);
        mUidDataContainer.setVisibility(View.GONE);
        mTlmDataContainer.setVisibility(View.GONE);
        mEtlmDataContainer.setVisibility(View.GONE);
        mEidDataContainer.setVisibility(View.GONE);
        if(mRefreshActiveSlot != null) {
            mRefreshActiveSlot.setOnClickListener(null);
            mRefreshActiveSlot = null;
        }

        if(mClearActiveSlot != null) {
            mClearActiveSlot.setOnClickListener(null);
            mClearActiveSlot = null;
        }
    }

    private void updateActiveSlotSpinner(int maxSupportedSlots) {
        ArrayList<String> mMaxSupportedSlots = new ArrayList<>();
        for (int i = 0; i < maxSupportedSlots; i++) {
            mMaxSupportedSlots.add("Slot " + i);
        }
        ArrayAdapter<String> mMaxActiveSlotsAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, mMaxSupportedSlots);
        mMaxActiveSlotsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mIsActiveSlotAdapterUpdated = true;
        mActiveSlots.setAdapter(mMaxActiveSlotsAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        // This will connect to the service only if it's already running
        Log.v(Utils.TAG, "" + getClass().getName());
        final Activity activity = getActivity();
        final Intent service = new Intent(activity, UpdateService.class);
        mBounnd = activity.bindService(service, mServiceConnection, 0);
        mGoogleApiClient.connect();
        handleSilentSignIn();
    }

    private void handleSilentSignIn(){

        OptionalPendingResult<GoogleSignInResult> pendingResult = Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);

        if (pendingResult.isDone()) {
            // If the user's cached credentials are valid, the OptionalPendingResult will be "done"
            // and the GoogleSignInResult will be available instantly.
            Log.d(Utils.TAG, "Got cached sign-in");
            GoogleSignInResult result = pendingResult.get();
            handleSignInResult(result);
        } else {
            pendingResult.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                @Override
                public void onResult(@NonNull GoogleSignInResult googleSignInResult) {
                    handleSignInResult(googleSignInResult);
                }
            });
        }
    }

    private void handleSignInResult(GoogleSignInResult result) {
        Log.d(Utils.TAG, "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            mSilentSignInFailed = false;
            // Signed in successfully, show authenticated UI.
            GoogleSignInAccount acct = result.getSignInAccount();
            mServerAuthCode = acct.getServerAuthCode();
            if(!checkIfAccessTokenExists()) {
                if (!Utils.UTILS_CLIENT_ID.equals(getString(R.string.server_client_id))) {
                    new RequestAccessTokenTask(getActivity(), mRequestAccessTokenCallback, "https://www.googleapis.com/oauth2/v4/token", getString(R.string.server_client_id), acct.getServerAuthCode()).execute();
                } else showToast(getString(R.string.error_server_client_id));
            }
        } else {
            mSilentSignInFailed = true;
        }
    }

    Callback mRequestAccessTokenCallback = new Callback() {
        @Override
        public void onFailure(Request request, IOException e) {

        }

        @Override
        public void onResponse(Response response) throws IOException {

            try {
                String body = response.body().string();
                final JSONObject jsonResponse = new JSONObject(body);
                Log.v(Utils.TAG, jsonResponse.toString());
                if (response.isSuccessful()) {
                    final String access_token = jsonResponse.getString("access_token");
                    final String refresh_token = jsonResponse.getString("refresh_token");
                    saveAccessToken(access_token, refresh_token);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    Callback mRefreshAccessTokenCallback = new Callback() {
        @Override
        public void onFailure(Request request, IOException e) {

        }

        @Override
        public void onResponse(Response response) throws IOException {

            try {
                String body = response.body().string();
                final JSONObject jsonResponse = new JSONObject(body);
                Log.v(Utils.TAG, jsonResponse.toString());
                if (!response.isSuccessful()) {
                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                        mProgressDialogHandler.removeCallbacks(mRunnableHandler);
                    }
                    showGoogleSignInDialogFragment();
                } else {
                    final String access_token = jsonResponse.getString("access_token");
                    saveNewAccessToken(access_token);

                    if(mRegisterBeacon) {
                        registerBeacons();
                        return;
                    }
                    if(mConfigureEidSlot){
                        configureEidSlot(mEidSlotData);
                    }

                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private void saveAccessToken(final String accessToken, final String refresh_token){
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(Utils.ACCESS_TOKEN_INFO, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Utils.ACCESS_TOKEN, accessToken);
        editor.putString(Utils.REFRESH_TOKEN, refresh_token);
        editor.commit();
    }

    private void saveNewAccessToken(final String accessToken){
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(Utils.ACCESS_TOKEN_INFO, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Utils.ACCESS_TOKEN, accessToken);
        editor.commit();
    }

    @Override
    public void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
        if (mBounnd)
            getActivity().unbindService(mServiceConnection);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        final MainActivity parent = (MainActivity) mContext;
        parent.setUpdateFragment(null);
        mContext = null;

        if (getActivity().isFinishing()) {
            final Activity activity = getActivity();
            final Intent service = new Intent(activity, UpdateService.class);
            activity.stopService(service);
        }

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        if (mConnectionProgressDialog != null && mConnectionProgressDialog.isShowing()) {
            mConnectionProgressDialog.dismiss();
        }

        unbindImageViews();
    }

    private void unbindImageViews(){
        mEditAdvInterval.setImageBitmap(null);
        mEditAdvInterval.setImageDrawable(null);

        mEditRadioTxPower.setImageBitmap(null);
        mEditRadioTxPower.setImageDrawable(null);

        mShowSlotInfo.setImageBitmap(null);
        mShowSlotInfo.setImageDrawable(null);

        if(mClearActiveSlot != null) {
            mClearActiveSlot.setImageBitmap(null);
        }
        if(mRefreshActiveSlot != null) {
            mRefreshActiveSlot.setImageBitmap(null);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readProjectInformation();
        final MainActivity parent = (MainActivity) mContext;
        parent.setUpdateFragment(this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_update, container, false);
        mProgressDialogHandler = new Handler();
        mBeaconConfigurationContainer = (LinearLayout) view.findViewById(R.id.beacon_configuration_container);
        mFrameTypeContainer = (LinearLayout) view.findViewById(R.id.frame_type_container);
        mFrameTypeView = (TextView) view.findViewById(R.id.frame_type);
        mFrameTypeContainer = (LinearLayout) view.findViewById(R.id.frame_type_container);
        mUidDataContainer = (LinearLayout) view.findViewById(R.id.uid_data_container);
        mUrlDataContainer = (LinearLayout) view.findViewById(R.id.url_data_container);
        mTlmDataContainer = (LinearLayout) view.findViewById(R.id.tlm_data_container);
        mEtlmDataContainer= (LinearLayout) view.findViewById(R.id.etlm_data_container);
        mEidDataContainer = (LinearLayout) view.findViewById(R.id.eid_data_container);
        mEditSlot = (ImageView) view.findViewById(R.id.edit_slot);
        mEditAdvInterval = (ImageView) view.findViewById(R.id.edit_adv_interval);
        mShowBroadcastCapabilities = (ImageView) view.findViewById(R.id.show_broadcast_capabilities);
        mShowSlotInfo = (ImageView) view.findViewById(R.id.show_slot_info);
        mEditRadioTxPower = (ImageView) view.findViewById(R.id.edit_radio_tx_power);
        mActiveSlots = (Spinner) view.findViewById(R.id.active_slots);
        mNamespaceId = (TextView) view.findViewById(R.id.namespace_id);
        mInstanceId = (TextView) view.findViewById(R.id.instance_id);
        mUrl = (TextView) view.findViewById(R.id.url_data);
        mEtlm = (TextView) view.findViewById(R.id.etlm_data);
        mEtlmSalt = (TextView) view.findViewById(R.id.etlm_salt);
        mEtlmMessageIntCheck = (TextView) view.findViewById(R.id.etlm_message_integrity_check);
        mVoltage = (TextView) view.findViewById(R.id.voltage);
        mTemperature = (TextView) view.findViewById(R.id.temperature);
        mPduCount = (TextView) view.findViewById(R.id.advertiser_count);
        mTimeSinceReboot = (TextView) view.findViewById(R.id.time_since_boot);
        mTimerExponent = (TextView) view.findViewById(R.id.timer_exponent);
        mClockValue = (TextView) view.findViewById(R.id.clock_value);
        mEid = (TextView) view.findViewById(R.id.eid);
        mAdvertisingInterval = (TextView) view.findViewById(R.id.adv_interval_ms);
        mRadioTxPower = (TextView) view.findViewById(R.id.radio_tx_power);
        mBeaconHelp = (TextView) view.findViewById(R.id.beacon_update_help);
        mConnectButton = (Button) view.findViewById(R.id.btn_connect);

        mProgressDialog = new ProgressDialog(getActivity());
        mProgressDialog.setCancelable(false);
        mProgressDialog.setCanceledOnTouchOutside(false);

        mConnectionProgressDialog = new ProgressDialog(getActivity());
        mConnectionProgressDialog.setCancelable(false);
        mConnectionProgressDialog.setCanceledOnTouchOutside(false);
        mConnectionProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(mBinder != null)
                    mBinder.disconnectAndClose();
                updateUiForBeacons(BluetoothProfile.STATE_DISCONNECTED, UpdateService.LOCKED);
            }
        });

        // Configure the CONNECT / DISCONNECT button
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {

                if (mBinder == null) {
                    if(isBleEnabled()) {
                        if(isLocationEnabled()) {
                            final ScannerFragment scannerFragment = ScannerFragment.getInstance(EDDYSTONE_GATT_CONFIG_SERVICE_UUID);
                            scannerFragment.show(getChildFragmentManager(), null);
                        } else {
                            showToast(getString(R.string.enable_location_services));
                        }
                    } else {
                        enableBle();
                    }
                } else {
                    mBinder.disconnectAndClose();
                    updateUiForBeacons(BluetoothProfile.STATE_DISCONNECTED, UpdateService.LOCKED);
                }
            }
        });

        mShowBroadcastCapabilities.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final BroadcastCapabilitesDialogFragment broadcastCapabilitesDialogFragment = BroadcastCapabilitesDialogFragment.newInstance(mBroadcastCapabilities);
                broadcastCapabilitesDialogFragment.show(getChildFragmentManager(), null);
            }
        });

        mShowSlotInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AllSlotInfoDialogFragment m = AllSlotInfoDialogFragment.newInstance(mActiveSlotsTypes);
                m.show(getChildFragmentManager(), null);
            }
        });

        mEditSlot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ReadWriteAdvertisementSlotDialogFragment dialogFragment = ReadWriteAdvertisementSlotDialogFragment.newInstance(false, mActiveSlot, mRwAdvertisingSlot);
                dialogFragment.show(getChildFragmentManager(), null);
            }
        });

        mEditRadioTxPower.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final RadioTxPowerDialogFragment dialogFragment = RadioTxPowerDialogFragment.newInstance(ParserUtils.parse(mRadioTxPowerData, 0, mRadioTxPowerData.length, "").trim(), mBroadcastCapabilities, false);
                dialogFragment.show(getChildFragmentManager(), null);

            }
        });

        mEditAdvInterval.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AdvertisingIntervalDialogFragment dialogFragment = AdvertisingIntervalDialogFragment.newInstance(mAdvertisingInterval.getText().toString().trim());
                dialogFragment.show(getChildFragmentManager(), null);
            }
        });

        mActiveSlots.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final byte[] data = new byte[1];
                data[0] = (byte) position;
                if (mIsActiveSlotAdapterUpdated)
                    mIsActiveSlotAdapterUpdated = false;
                else {
                    mProgressDialog.setTitle(getString(R.string.switching_slots));
                    mProgressDialog.setMessage(getString(R.string.please_wait_switching_slots));
                    mProgressDialog.show();
                    mProgressDialogHandler.postDelayed(mRunnableHandler, 10000);
                    mEikGenerated = false;
                    mBinder.changeToSelectedActiveSlot(data);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mUrl.getText().toString()));
                getActivity().startActivity(browserIntent);
            }
        });

        startGoogleSignin();

        return view;
    }

    private void startGoogleSignin() {
        Scope scope = new Scope("https://www.googleapis.com/auth/cloud-platform");
        Scope scope1 = new Scope("https://www.googleapis.com/auth/userlocation.beacon.registry");
        GoogleSignInOptions mGso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(scope, scope1)
                .requestServerAuthCode(getString(R.string.server_client_id))
                .build();
        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                .addApi(Auth.GOOGLE_SIGN_IN_API, mGso)
                .build();
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(false);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mServiceBroadcastReceiver, createIntentFilters());
    }


    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.menu_update, menu);

        MenuItem itemClearActiveSlot = menu.findItem(R.id.action_clear_slot);
        itemClearActiveSlot.setActionView(R.layout.menu_clear_active_slot);
        mClearActiveSlot = (ImageView) itemClearActiveSlot.getActionView();
        mClearActiveSlot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClearSlotDialogFragment clearSlotDialogFragment = ClearSlotDialogFragment.newInstance();
                clearSlotDialogFragment.show(getChildFragmentManager(), null);
            }
        });


        MenuItem itemRefreshActiveSlot = menu.findItem(R.id.action_refresh_slot);
        itemRefreshActiveSlot.setActionView(R.layout.menu_refresh_active_slot);
        mRefreshActiveSlot = (ImageView) itemRefreshActiveSlot.getActionView();
        mRefreshActiveSlot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBinder != null) {
                    if (mProgressDialog != null) {
                        mProgressDialog.setTitle(getString(R.string.prog_dialog_reading));
                        mProgressDialog.setMessage(getString(R.string.prog_dialog_rw_adv_slot_msg));
                        mProgressDialog.show();
                        mProgressDialogHandler.postDelayed(mRunnableHandler, 10000);
                    }
                    mBinder.startReadingCharacteristicsForActiveSlot();
                }
            }
        });
        //if(mCreateShowcase)
        createShowcaseSequence();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        switch (id) {
            case R.id.action_clear_slot:
                ClearSlotDialogFragment clearSlotDialogFragment = ClearSlotDialogFragment.newInstance();
                clearSlotDialogFragment.show(getChildFragmentManager(), null);
                return true;
            case R.id.action_refresh_slot:
                if (mBinder != null) {
                    if (mProgressDialog != null) {
                        mProgressDialog.setTitle(getString(R.string.prog_dialog_reading));
                        mProgressDialog.setMessage(getString(R.string.prog_dialog_rw_adv_slot_msg));
                        mProgressDialog.show();
                        mProgressDialogHandler.postDelayed(mRunnableHandler, 10000);
                    }
                    mBinder.startReadingCharacteristicsForActiveSlot();
                }
                return true;
            case R.id.action_lock:
                if (!mIsBeaconLocked) {
                    LockStateDialogFragment lockStateDialogFragment = LockStateDialogFragment.newInstance(mCurrentLockState, mUnlockCode);
                    lockStateDialogFragment.show(getChildFragmentManager(), null);
                } else {
                    showToast(getString(R.string.unlock_beacon_error));
                }
                return true;
            case R.id.action_ecdh_info:
                if (mBeaconPublicEcdhKey != null && mFrameType == TYPE_EID) {
                    EcdhKeyInfoDialogFragment ecdhKeyInfoDialogFragment = EcdhKeyInfoDialogFragment.newInstance(mActiveSlot, mFrameType,
                            ParserUtils.bytesToHex(mBeaconPublicEcdhKey, 0, 32, true), ParserUtils.bytesToHex(mEncryptedIdentityKey, 0, 16, true),
                            ParserUtils.bytesToHex(mDecryptedIdentityKey, 0, 16, true));
                    ecdhKeyInfoDialogFragment.show(getChildFragmentManager(), null);
                } else {
                    ErrorDialogFragment errorDialogFragment = ErrorDialogFragment.newInstance(getString(R.string.error_no_ecdh_info));
                    errorDialogFragment.show(getChildFragmentManager(), null);
                }
                break;
            case R.id.action_register_beacons:
                mRegisterBeacon = true;
                registerBeacons();
                break;
            case R.id.action_switch_projects:
                switchProjects();
                break;
            case R.id.action_adv_advertised_tx_power:
                RadioTxPowerDialogFragment dialogFragment = RadioTxPowerDialogFragment.newInstance(String.valueOf(mAdvancedAdvTxPower), mBroadcastCapabilities, true);
                dialogFragment.show(getChildFragmentManager(), null);
                break;
            case R.id.action_remain_connectable:
                RemainConnectableDialogFragment remainConnectableDialogFragment = RemainConnectableDialogFragment.newInstance(mRemainConnectable);
                remainConnectableDialogFragment.show(getChildFragmentManager(), null);
                break;
            case R.id.action_update_about:
                Intent intent = new Intent(getActivity(), UpdateSettingsActivity.class);
                startActivityForResult(intent, Utils.REQUEST_UPDATE_SETTINGS);
        }
        return false;
    }

    private void createShowcaseSequence(){
        if(((MainActivity)mContext).getTabPosition() == 1 && mShowcaseConfig == null) {
            mShowcaseConfig = new ShowcaseConfig();
            mShowcaseConfig.setDelay(500);
            materialShowcaseSequence = new MaterialShowcaseSequence(getActivity(), TOOLBAR_BUTTON_HELP);
            materialShowcaseSequence.setConfig(mShowcaseConfig);
            materialShowcaseSequence.addSequenceItem(mShowSlotInfo,
                    getString(R.string.all_slot_showcase), getString(R.string.got_it));
            materialShowcaseSequence.addSequenceItem(mShowBroadcastCapabilities,
                    getString(R.string.capabilities_showcase), getString(R.string.got_it));
            materialShowcaseSequence.addSequenceItem(mActiveSlots,
                    getString(R.string.active_slots_showcase), getString(R.string.got_it));
            materialShowcaseSequence.addSequenceItem(mEditSlot,
                    getString(R.string.edit_slot_showcase), getString(R.string.got_it));
            if(mClearActiveSlot != null) {
                materialShowcaseSequence.addSequenceItem(mClearActiveSlot,
                        getString(R.string.clear_slot_showcase), getString(R.string.got_it));
                materialShowcaseSequence.addSequenceItem(mRefreshActiveSlot,
                        getString(R.string.refresh_slot_showcase), getString(R.string.got_it));
                //materialShowcaseSequence.start();
            }
        }

    }

    private IntentFilter createIntentFilters() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(UpdateService.ACTION_STATE_CHANGED);
        filter.addAction(UpdateService.ACTION_DONE);
        filter.addAction(UpdateService.ACTION_GATT_ERROR);
        filter.addAction(UpdateService.ACTION_UNLOCK_BEACON);
        filter.addAction(UpdateService.ACTION_DISSMISS_UNLOCK);
        filter.addAction(UpdateService.ACTION_BROADCAST_CAPABILITIES);
        filter.addAction(UpdateService.ACTION_ACTIVE_SLOT);
        filter.addAction(UpdateService.ACTION_ADVERTISING_INTERVAL);
        filter.addAction(UpdateService.ACTION_RADIO_TX_POWER);
        filter.addAction(UpdateService.ACTION_ADVANCED_ADVERTISED_TX_POWER);
        filter.addAction(UpdateService.ACTION_LOCK_STATE);
        filter.addAction(UpdateService.ACTION_UNLOCK);
        filter.addAction(UpdateService.ACTION_ECDH_KEY);
        filter.addAction(UpdateService.ACTION_EID_IDENTITY_KEY);
        filter.addAction(UpdateService.ACTION_READ_WRITE_ADV_SLOT);
        filter.addAction(UpdateService.ACTION_ADVANCED_FACTORY_RESET);
        filter.addAction(UpdateService.ACTION_ADVANCED_REMAIN_CONNECTABLE);
        filter.addAction(UpdateService.ACTION_BROADCAST_ALL_SLOT_INFO);
        Log.v(TAG, "Intent filters created");
        return filter;
    }

    private void registerEidBeacon(final byte[] uid) {
        final JSONObject jBody = createEidBeaconJson(uid);
        if (jBody != null) {
            if(mProximityApiClient != null) {
                if (NetworkUtils.checkNetworkConnectivity(getActivity())) {
                    if (mProgressDialog != null) {
                        mProgressDialog.setTitle(getString(R.string.registration_eid_title));
                        mProgressDialog.setMessage(getString(R.string.registration_eid_message));
                        mProgressDialog.show();
                        mProgressDialogHandler.postDelayed(mRunnableHandler, 10000);
                    }
                    mProximityApiClient.registerBeacon(mBeaconRegistrationCallback, jBody);
                } else showToast(getString(R.string.check_internet_connectivity));
            } else {
                createProximityAPIClient();
            }
        } else showToast(getString(R.string.service_ecdh_missing));
    }

    private void registerUidBeacon() {
        final JSONObject jBody = createUidBeaconJson();
        if (jBody != null)
            if (mProximityApiClient != null) {
                if (NetworkUtils.checkNetworkConnectivity(getActivity())) {
                    if (mProgressDialog != null) {
                        mProgressDialog.setTitle(getString(R.string.registration_uid_title));
                        mProgressDialog.setMessage(getString(R.string.registration_uid_message));
                        mProgressDialog.show();
                        mProgressDialogHandler.postDelayed(mRunnableHandler, 10000);
                    }
                    mProximityApiClient.registerBeacon(mBeaconRegistrationCallback, jBody);
                } else showToast(getString(R.string.check_internet_connectivity));
            } else {
                createProximityAPIClient();
            }
    }

    private JSONObject createEidBeaconJson(final byte[] uid) {

        JSONObject body;
        try {
            body = new JSONObject();

            JSONObject advertisedId = new JSONObject()
                    .put("type", "EDDYSTONE")
                    .put("id", ParserUtils.base64Encode(uid));
            body.put("advertisedId", advertisedId);

            body.put("status", "ACTIVE");

            // TODO: encode the remaining beacon parameters like location, description, etc.
            // https://developers.google.com/beacons/proximity/reference/rest/v1beta1/beacons#Beacon
            if (mServiceEcdhKey == null) {
                return null;
            }
            String beaconEcdhPublicKey = ParserUtils.base64Encode(mBeaconPublicEcdhKey);
            String serviceEcdhPublicKey = ParserUtils.base64Encode(mServiceEcdhKey);

            final byte[] eidr = new byte[8];
            ParserUtils.setByteArrayValue(eidr, 0, mEid.getText().toString().trim().replace("0x", ""));
            String initialEidr = ParserUtils.base64Encode(eidr);

            JSONObject ephemeralIdRegistration = new JSONObject()
                    .put("beaconEcdhPublicKey", beaconEcdhPublicKey)
                    .put("serviceEcdhPublicKey", serviceEcdhPublicKey)
                    .put("rotationPeriodExponent", mTimerExponent.getText().toString())
                    .put("initialClockValue", mClockValue.getText().toString())
                    .put("initialEid", initialEidr);
            body.put("ephemeralIdRegistration", ephemeralIdRegistration);

            Log.d(TAG, "request:" + body.toString(2));
        } catch (JSONException e) {
            return null;
        }
        return body;
    }

    private JSONObject createUidBeaconJson() {

        JSONObject body;
        try {
            body = new JSONObject();
            String namespaceId = mNamespaceId.getText().toString().trim();
            String instanceId = mInstanceId.getText().toString().trim();
            if (namespaceId.startsWith("0x"))
                namespaceId = namespaceId.substring(2, namespaceId.length());

            if (instanceId.startsWith("0x"))
                instanceId = instanceId.substring(2, instanceId.length());

            final String uid = namespaceId + instanceId;

            JSONObject advertisedId = new JSONObject()
                    .put("type", "EDDYSTONE")
                    .put("id", ParserUtils.base64Encode(uid));
            body.put("advertisedId", advertisedId);

            body.put("status", "ACTIVE");

            Log.d(TAG, "request:" + body.toString(2));
        } catch (JSONException e) {
            return null;
        }
        return body;
    }

    Callback mBeaconRegistrationCallback = new Callback() {
        @Override
        public void onFailure(Request request, IOException e) {

        }

        @Override
        public void onResponse(Response response) throws IOException {

            try {
                mProgressDialog.dismiss();
                mProgressDialogHandler.removeCallbacks(mRunnableHandler);
                String body = response.body().string();
                final JSONObject jsonResponse = new JSONObject(body);
                if (!response.isSuccessful()) {
                    final JSONObject jsonError = jsonResponse.getJSONObject("error");
                    final int errorCode = jsonError.getInt("code");
                    final String message = jsonError.getString("message");
                    ProximityApiErrorDialogFragment errorDialogFragment;
                    switch (errorCode) {
                        case Utils.ERROR_ALREADY_EXISTS:
                            mRegisterBeacon = false;
                            errorDialogFragment = ProximityApiErrorDialogFragment.newInstance(String.valueOf(errorCode), message, getString(R.string.error_beacon_already_exists));
                            errorDialogFragment.show(getChildFragmentManager(), null);
                            break;
                        case Utils.ERROR_ACCESS_REVOKED:
                            break;
                        case Utils.ERROR_UNAUTHORIZED:
                            if(mServerAuthCode != null) {
                                final String refreshToken = getActivity().getSharedPreferences(Utils.ACCESS_TOKEN_INFO, Context.MODE_PRIVATE).getString(Utils.REFRESH_TOKEN, "");
                                if(!refreshToken.isEmpty())
                                    new RefreshAccessTokenTask(mRefreshAccessTokenCallback, refreshToken, getString(R.string.server_client_id), mServerAuthCode).execute();
                            } else {
                                showGoogleSignInDialogFragment();
                            }
                            break;

                        case 403:
                        default:
                            errorDialogFragment = ProximityApiErrorDialogFragment.newInstance(String.valueOf(errorCode), message.equals("")? getString(R.string.unknown_error) : message, getString(R.string.beacon_registration_fail));
                            errorDialogFragment.show(getChildFragmentManager(), null);
                            break;
                    }
                    return;
                }

                mRegisterBeacon = false;
                showToast(getString(R.string.registration_success));
                final String beaconName = jsonResponse.getString("beaconName");
                CreateAttachmentDialogFragment createAttachmentDialogFragment = CreateAttachmentDialogFragment.newInstance(beaconName);
                createAttachmentDialogFragment.show(getChildFragmentManager(), null);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private void showGoogleSignInDialogFragment(){
        GoogleSignInDialogFragment googleSignInDialogFragment = GoogleSignInDialogFragment.newInstance(getString(R.string.sign_in_rationale));
        googleSignInDialogFragment.show(getChildFragmentManager(), null);
    }

    private void showToast(final String message) {
        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mServiceBroadcastReceiver);
        Log.v(TAG, "Receiver unregistered!");
        mServiceBroadcastReceiver = null;
        mClearActiveSlot = null;
        mRefreshActiveSlot = null;
    }

    @Override
    public void onDeviceSelected(final BluetoothDevice device, final String name) {
        if (mConnectionProgressDialog != null) {
            mConnectionProgressDialog.setTitle(getString(R.string.prog_dialog_connect_title));
            mConnectionProgressDialog.setMessage(getString(R.string.prog_dialog_connect_message));
            mConnectionProgressDialog.show();
        }

        final Activity activity = getActivity();
        final Intent service = new Intent(activity, UpdateService.class);
        service.putExtra(UpdateService.EXTRA_DATA, device);
        updateUiForBeacons(BluetoothProfile.STATE_CONNECTED, UpdateService.LOCKED);
        activity.startService(service);
        mBounnd = true;
        activity.bindService(service, mServiceConnection, 0);
    }

    private final Runnable mRunnableHandler = new Runnable() {
        @Override
        public void run() {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }
    };

    private void updateUiForBeacons(int connectionState, int lockState) {
        switch (connectionState) {
            case BluetoothProfile.STATE_CONNECTED:
                if (UpdateService.LOCKED == lockState) {
                    mIsBeaconLocked = true;
                    mCurrentLockState = LOCKED;
                } else if(UpdateService.UNLOCKED == lockState || UpdateService.UNLOCKED_AUTOMATIC_RELOCK_DISABLED == lockState) {
                    mChallenge = null; //setting the challenge to a null or else the user will be prompted with a unlock failure message
                    mCurrentLockState = lockState;
                    mIsBeaconLocked = false;
                    mBeaconHelp.setVisibility(View.GONE);
                    mBeaconConfigurationContainer.setVisibility(View.VISIBLE);
                    mFrameTypeContainer.setVisibility(View.VISIBLE);
                }
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                setHasOptionsMenu(false);
                getActivity().invalidateOptionsMenu();
                if(mClearActiveSlot != null) {
                    mClearActiveSlot.setImageBitmap(null);
                    mClearActiveSlot = null;
                }
                if(mRefreshActiveSlot != null) {
                    mRefreshActiveSlot.setImageBitmap(null);
                    mRefreshActiveSlot = null;
                }
                mBeaconHelp.setVisibility(View.VISIBLE);
                mBeaconConfigurationContainer.setVisibility(View.GONE);
                mFrameTypeContainer.setVisibility(View.GONE);
                //clear all resources on disconnection
                mBeaconPublicEcdhKey = null;
                mDecryptedIdentityKey = null;
                mServiceEcdhKey = null;
                mIsBeaconLocked = true;
                if(mActiveSlotsTypes != null)
                    mActiveSlotsTypes.clear();
                break;
        }
    }

    @Override
    public void unlockBeacon(byte[] encryptedLockCode, final byte[] beaconLockCode) {
        if(encryptedLockCode != null) {
            if (mProgressDialog != null && !mProgressDialog.isShowing()) {
                mProgressDialog.setTitle(getString(R.string.unlocking_beacon));
                mProgressDialog.setMessage(getString(R.string.unlock_and_read_all_slots));
                mProgressDialog.show();
            }
            mUnlockCode = beaconLockCode;
            mBinder.unlockBeacon(encryptedLockCode, beaconLockCode);
        } else {
            mBinder.setBeaconLockCode(beaconLockCode);
            mUnlockCode = beaconLockCode;
            final byte [] data = ParserUtils.aes128decrypt(mEncryptedIdentityKey, new SecretKeySpec(mUnlockCode, "AES"));
            mDecryptedIdentityKey = data;
            mCreateShowcase = true;
            if(mUnlockCode != null)
                materialShowcaseSequence.start();
                //createShowcaseSequence();
        }
    }

    @Override
    public void cancelUnlockBeacon() {
        final Activity activity = getActivity();
        final Intent service = new Intent(activity, UpdateService.class);
        activity.stopService(service);
    }

    @Override
    public void configureUidSlot(byte[] uidSlotData) {
        mActiveSlotsTypes.set(mActiveSlot, getString(R.string.type_uid));
        mBinder.configureActiveSlot(uidSlotData, getString(R.string.type_uid));
    }

    @Override
    public void configureUrlSlot(byte[] urlSlotData) {
        mActiveSlotsTypes.set(mActiveSlot, getString(R.string.type_url));
        mBinder.configureActiveSlot(urlSlotData, getString(R.string.type_url));
    }

    @Override
    public void configureTlmSlot(byte[] tlmSlotData) {
        mActiveSlotsTypes.set(mActiveSlot, getString(R.string.type_tlm));
        mBinder.configureActiveSlot(tlmSlotData, getString(R.string.type_tlm));
    }

    @Override
    public void configureEidSlot(final byte[] eidSlotData) {

        mConfigureEidSlot = true;
        if (mProximityApiClient != null) {
            if (NetworkUtils.checkNetworkConnectivity(getActivity())) {
                if (mProgressDialog != null) {
                    mProgressDialog.setTitle(getString(R.string.prog_dialog_config_eid_title));
                    mProgressDialog.setMessage(getString(R.string.retrieve_resolver_keys));
                    mProgressDialog.show();
                    mProgressDialogHandler.postDelayed(mRunnableHandler, 10000);
                }
                mProximityApiClient.getEphemeralIdRegistrationParams(new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {

                    }

                    @Override
                    public void onResponse(Response response) throws IOException {

                        try {
                            JSONObject jsonResponse = new JSONObject(response.body().string());
                            if (!response.isSuccessful()) {

                                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                                    mProgressDialog.dismiss();
                                    mProgressDialogHandler.removeCallbacks(mRunnableHandler);
                                }

                                final JSONObject jsonError = jsonResponse.getJSONObject("error");
                                final int errorCode = jsonError.getInt("code");
                                final String message = jsonError.getString("message");
                                ProximityApiErrorDialogFragment errorDialogFragment;
                                switch (errorCode) {
                                    case Utils.ERROR_UNAUTHORIZED:
                                        /*errorDialogFragment = ProximityApiErrorDialogFragment.newInstance(String.valueOf(errorCode), message, "");
                                        errorDialogFragment.show(getChildFragmentManager(), null);*/
                                        if(mServerAuthCode != null) {
                                            final String refreshToken = getActivity().getSharedPreferences(Utils.ACCESS_TOKEN_INFO, Context.MODE_PRIVATE).getString(Utils.REFRESH_TOKEN, "");
                                            if(!refreshToken.isEmpty())
                                                new RefreshAccessTokenTask(mRefreshAccessTokenCallback, refreshToken, getString(R.string.server_client_id), mServerAuthCode).execute();
                                        } else {
                                            showGoogleSignInDialogFragment();
                                        }
                                        return;
                                    default:
                                        errorDialogFragment = ProximityApiErrorDialogFragment.newInstance(String.valueOf(errorCode), message, "");
                                        errorDialogFragment.show(getChildFragmentManager(), null);
                                        return;
                                }
                            }
                            mConfigureEidSlot = false;
                            Log.d(TAG, "getEphemeralIdRegistrationParams response: " + jsonResponse.toString(2));
                            String serviceEcdhPublicKey = jsonResponse.getString("serviceEcdhPublicKey");
                            mServiceEcdhKey = ParserUtils.base64Decode(serviceEcdhPublicKey);
                            Log.d(TAG, "Service ECDH Key: " + ParserUtils.bytesToHex(mServiceEcdhKey, 0, 32, false));

                            if (mProgressDialog != null && mProgressDialog.isShowing())
                                mProgressDialog.setMessage(getString(R.string.writing_rw_adv_slot_char));

                            if (eidSlotData.length == 34) {
                                mEikGenerated = false;
                                System.arraycopy(mServiceEcdhKey, 0, eidSlotData, 1, mServiceEcdhKey.length);
                                mActiveSlotsTypes.set(mActiveSlot, "EID");
                                mBinder.configureActiveSlot(eidSlotData, "EID");
                            } else {
                                byte[] beaconPrivateEcdhKey = new byte[32];
                                new Random().nextBytes(beaconPrivateEcdhKey);
                                Log.d(TAG, "Beacon ECDH Private Key: " + ParserUtils.bytesToHex(beaconPrivateEcdhKey, 0, 32, false));
                                generator = new EddystoneEidrGenerator(mServiceEcdhKey, beaconPrivateEcdhKey);
                                mBeaconPublicEcdhKey = generator.getBeaconPublicKey();
                                Log.d(TAG, "Beacon ECDH Public Key: " + ParserUtils.bytesToHex(mBeaconPublicEcdhKey, 0, 32, false));
                                byte[] identityKey = generator.getIdentityKey();
                                mEikGenerated = true;
                                if(identityKey != null) {
                                    Log.d(TAG, "Unencrypted Idenity Key: " + ParserUtils.bytesToHex(identityKey, 0, 16, false));
                                    Log.v(TAG, "Encrypted Identity key: " + ParserUtils.bytesToHex(identityKey, 0, 16, true));
                                    identityKey = ParserUtils.aes128Encrypt(identityKey, new SecretKeySpec(mUnlockCode, "AES"));
                                    System.arraycopy(identityKey, 0, eidSlotData, 1, identityKey.length);
                                } else {
                                    showToast(getString(R.string.fail_eid_configuration));
                                    return;
                                }
                                mActiveSlotsTypes.set(mActiveSlot, "EID");
                                mBinder.configureActiveSlot(eidSlotData, "EID");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else showToast(getString(R.string.check_internet_connectivity));
        } else {
            mEidSlotData = eidSlotData;
            if(mSilentSignInFailed){
                GoogleSignInDialogFragment fragment = GoogleSignInDialogFragment.newInstance(getString(R.string.sign_in_rationale));
                fragment.show(getChildFragmentManager(), null);
            }else {

                final Project project = selectProject();
                if(project != null) {
                    createProximityAPIClient();
                    if (mProximityApiClient != null) {
                        configureEidSlot(eidSlotData);
                    }
                } else return;
            }
        }
    }

    @Override
    public void lockBeacon(byte[] lockCode) {
        if (lockCode.length == 17)
            System.arraycopy(lockCode, 1, mUnlockCode, 0, 16);
        mBinder.lockBeacon(lockCode);
    }

    @Override
    public void registerBeaconListener(byte[] uid) {
        registerEidBeacon(uid);
    }

    @Override
    public void createAttachmentForBeacon(final String mBeaconName, final byte[] attachmentData) {
        if (mProximityApiClient != null) {
            mProgressDialog.setTitle(getString(R.string.register_attachment_title));
            mProgressDialog.show();
            mProgressDialogHandler.postDelayed(mRunnableHandler, 10000);
            mProximityApiClient.createAttachment(mCreateAttachmentCallback, mBeaconName, createBeaconAttachment(attachmentData));
        }
    }

    private final Callback mCreateAttachmentCallback = new Callback() {
        @Override
        public void onFailure(Request request, IOException e) {
            Log.v(TAG, "Beacon attachmentm: " + request.toString());
        }

        @Override
        public void onResponse(Response response) throws IOException {
            try {
                mProgressDialog.dismiss();
                mProgressDialogHandler.removeCallbacks(mRunnableHandler);
                String body = response.body().string();
                final JSONObject jsonResponse = new JSONObject(body);
                if (!response.isSuccessful()){
                    final JSONObject jsonError = jsonResponse.getJSONObject("error");
                    final int errorCode = jsonError.getInt("code");
                    final String message = jsonError.getString("message");
                    ProximityApiErrorDialogFragment errorDialogFragment;
                    switch (errorCode) {
                        case Utils.ERROR_UNAUTHORIZED:
                            return;
                        default:
                            errorDialogFragment = ProximityApiErrorDialogFragment.newInstance(String.valueOf(errorCode), message, "");
                            errorDialogFragment.show(getChildFragmentManager(), null);
                            return;
                    }
                }
                showToast(getString(R.string.attachment_siccess));

                Log.v(TAG, "Beacon attachmentm: " + response.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private JSONObject createBeaconAttachment(final byte[] attachment) {

        JSONObject body;
        try {
            body = new JSONObject()
                    .put("namespacedType", mProject.getProjectNamespace() + "/string")
                    .put("data", ParserUtils.base64Encode(attachment));
            Log.d(TAG, "request:" + body.toString(2));
        } catch (JSONException e) {
            return null;
        }
        return body;
    }

    @Override
    public void configureRadioTxPower(final byte[] radioTxPower, final boolean advanceTxPowerSupported) {
        if (!advanceTxPowerSupported)
            mBinder.configureRadioTxPower(radioTxPower);
        else
            mBinder.configureAdvancedAdvertisedTxPower(radioTxPower);
    }

    @Override
    public void clearSlot() {
        mBinder.configureActiveSlot(new byte[]{0x00}, "EMPTY"); //configuring active slot with a 0 byte will clear the slot
    }

    @Override
    public void configureAdvertisingInterval(final byte[] advertisingInterval) {
        mBinder.configureAdvertistingInterval(advertisingInterval);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Utils.REQUEST_PERMISSION_REQ_CODE: {
                if(permissions.length > 0)
                    for(int i = 0; i < permissions.length; i++){
                        if (permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                                onPermissionGranted(permissions[i]);
                            else showToast(getString(R.string.rationale_permission_denied));
                        }
                    }
                break;
            }
        }
    }

    @Override
    protected void onPermissionGranted(String permission) {

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case Utils.REQUEST_ENABLE_BT:
                if(data != null)
                    if (resultCode != Activity.RESULT_OK) {
                        showToast(getString(R.string.rationale_permission_denied));
                        onDestroy();
                    } else {
                        if(ensurePermission(new String [] {Manifest.permission.ACCESS_COARSE_LOCATION})) {
                            if(isLocationEnabled()) {
                                final ScannerFragment scannerFragment = ScannerFragment.getInstance(EDDYSTONE_GATT_CONFIG_SERVICE_UUID);
                                scannerFragment.show(getChildFragmentManager(), null);
                            } else {
                                showToast(getString(R.string.enable_location_services));
                            }
                        }
                    }
                break;
            case Utils.REQUEST_UPDATE_SETTINGS:
                if (resultCode == Activity.RESULT_OK){
                    final boolean tutorials_enabled = data.getExtras().getBoolean("TUTORIALS_ENABLED", false);
                    Log.v(TAG, "background scanning enabled? " + tutorials_enabled);
                    if(tutorials_enabled){
                        mShowcaseConfig = null;
                        materialShowcaseSequence = null;
                        createShowcaseSequence();
                        materialShowcaseSequence.start();
                    }
                }
                break;
            case Utils.SIGN_IN:
                GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
                handleSignInResult(result);
                break;
        }
    }

    /**
     * Checks whether the Bluetooth adapter is enabled.
     */
    private boolean isBleEnabled() {
        final BluetoothManager bm = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter ba = bm.getAdapter();
        return ba != null && ba.isEnabled();
    }

    /**
     * Tries to start Bluetooth adapter.
     */
    private void enableBle() {
        final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent, Utils.REQUEST_ENABLE_BT);
    }

    private boolean isLocationEnabled() {
        if (checkIfVersionIsMarshmallowOrAbove()) {
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;

            try {
                locationMode = Settings.Secure.getInt(getActivity().getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (final Settings.SettingNotFoundException e) {
                // do nothing
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }
        return true;
    }

    private boolean checkIfVersionIsMarshmallowOrAbove() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    final BroadcastReceiver mLocationProviderChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if(!isLocationEnabled())
                showToast(getString(R.string.enable_location_services));
        }
    };

    public Project readProjectInformation() {
        SharedPreferences sp = getActivity().getSharedPreferences(Utils.PROJECT_INFO, Context.MODE_PRIVATE);
        final String projectName = sp.getString(Utils.PROJECT_NAME, "");
        final String projectId = sp.getString(Utils.PROJECT_ID, "");
        final String projectNamespace = sp.getString(Utils.PROJECT_NAMESPACE, "");
        if(projectName.isEmpty() || projectId.isEmpty() || projectNamespace.isEmpty())
        {
            return null;
        }
        return mProject = new Project(projectName, projectId, projectNamespace);
    }

    @Override
    public void handleAuthorizationError(String errorCode) {
        final int error = Integer.parseInt(errorCode);
        switch (error){
            case Utils.ERROR_UNAUTHORIZED:
                if(mServerAuthCode != null) {
                    final String refreshToken = getActivity().getSharedPreferences(Utils.ACCESS_TOKEN_INFO, Context.MODE_PRIVATE).getString(Utils.REFRESH_TOKEN, "");
                    if(!refreshToken.isEmpty())
                        new RefreshAccessTokenTask(mRefreshAccessTokenCallback, refreshToken, getString(R.string.server_client_id), mServerAuthCode).execute();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onSignInCompleteHandler(final String serverAuthCode) {
        mServerAuthCode = serverAuthCode;
        mSilentSignInFailed = false;
        ProjectSelectionDialogFragment projectSelectionDialogFragment = ProjectSelectionDialogFragment.newInstance();
        projectSelectionDialogFragment.show(getChildFragmentManager(), null);
    }

    private boolean checkIfAccessTokenExists(){
        final String accessToken = getActivity().getSharedPreferences(Utils.ACCESS_TOKEN_INFO, Context.MODE_PRIVATE).getString(Utils.ACCESS_TOKEN, "");
        if(!accessToken.isEmpty())
            return true;
        else return false;
    }

    private Project selectProject(){
        mProject = readProjectInformation();
        if (mProject == null) {
            ProjectSelectionDialogFragment projectSelectionDialogFragment = ProjectSelectionDialogFragment.newInstance();
            projectSelectionDialogFragment.show(getChildFragmentManager(), null);
            return null;
        } else return mProject;
    }

    private void createProximityAPIClient(){
        if(mProximityApiClient == null) {
            if (mProject != null) {
                //final String accessToken = getActivity().getSharedPreferences(Utils.ACCESS_TOKEN_INFO, Context.MODE_PRIVATE).getString(Utils.ACCESS_TOKEN, "");
                mProximityApiClient = new ProximityBeaconImpl(getActivity(), mProject);
            } else {
                ProjectSelectionDialogFragment projectSelectionDialogFragment = ProjectSelectionDialogFragment.newInstance();
                projectSelectionDialogFragment.show(getChildFragmentManager(), null);
            }
        } else {
            mProximityApiClient.refreshProject(mProject);
        }
    }

    @Override
    public void handleProjectSelection() {
        showToast(getString(R.string.success_project_selection));
        readProjectInformation();
        createProximityAPIClient();
        if(mRegisterBeacon)
            registerBeacons();
        if(mConfigureEidSlot) {
            final byte [] eidSlotData = mEidSlotData;
            configureEidSlot(eidSlotData);
        }
    }

    @Override
    public void cancelProjectSelection() {
        mRegisterBeacon = false;
    }

    @Override
    public String getServerAuthCode() {
        return mServerAuthCode;
    }

    @Override
    public void displayGoogleSignInDialog() {
        showGoogleSignInDialogFragment();
    }

    @Override
    public void displayErrorDialog(final String errorCode, final String message, final String status) {
        ProximityApiErrorDialogFragment errorDialogFragment = ProximityApiErrorDialogFragment.newInstance(errorCode, message, status);
        errorDialogFragment.show(getChildFragmentManager(), null);
    }

    private void registerBeacons() {

        if (mFrameTypeView.getText().toString().equals("EMPTY")) {
            return;
        }

        switch (mFrameType) {
            case TYPE_UID:
                if(mSilentSignInFailed){
                    GoogleSignInDialogFragment fragment = GoogleSignInDialogFragment.newInstance(getString(R.string.sign_in_rationale));
                    fragment.show(getChildFragmentManager(), null);
                } else {
                    registerUidBeacon();
                }
                break;
            case TYPE_EID:
                if(mSilentSignInFailed){
                    GoogleSignInDialogFragment fragment = GoogleSignInDialogFragment.newInstance(getString(R.string.sign_in_rationale));
                    fragment.show(getChildFragmentManager(), null);
                } else {
                    RegisterBeaconDialogFragment registerBeaconDialogFragment = RegisterBeaconDialogFragment.newInstance();
                    registerBeaconDialogFragment.show(getChildFragmentManager(), null);
                }
                break;
            default:
                final String message = "Cannot register URL/TLM/eTLM beacon type";
                showToast(message);
                break;

        }
    }

    private void switchProjects() {
        if(mSilentSignInFailed){
            GoogleSignInDialogFragment fragment = GoogleSignInDialogFragment.newInstance(getString(R.string.sign_in_rationale));
            fragment.show(getChildFragmentManager(), null);
        } else {
            ProjectSelectionDialogFragment projectSelectionDialogFragment = ProjectSelectionDialogFragment.newInstance();
            projectSelectionDialogFragment.show(getChildFragmentManager(), null);
        }
    }

}
