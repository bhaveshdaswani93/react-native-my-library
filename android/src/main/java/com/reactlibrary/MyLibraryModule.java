package com.reactlibrary;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.braintreepayments.api.dropin.DropInActivity;
import com.braintreepayments.api.dropin.DropInRequest;
import com.braintreepayments.api.dropin.DropInResult;
import com.braintreepayments.api.models.CardNonce;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.ThreeDSecureInfo;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import java.util.HashMap;
import java.util.Map;


public class MyLibraryModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private static final String DURATION_SHORT_KEY = "SHORT";
    private static final String DURATION_LONG_KEY = "LONG";
    private boolean isVerifyingThreeDSecure = false;
    private Promise mPromise;
    private static final int DROP_IN_REQUEST = 0x444;




    public MyLibraryModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addActivityEventListener(mActivityListener);
    }

    @Override
    public String getName() {

        return "MyLibrary";
    }

    @ReactMethod
    public void sampleMethod(String stringArgument, int numberArgument, Callback callback) {
        // TODO: Implement some actually useful functionality
        callback.invoke("Received numberArgument: " + numberArgument + " stringArgument: " + stringArgument);
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put(DURATION_SHORT_KEY, Toast.LENGTH_SHORT);
        constants.put(DURATION_LONG_KEY, Toast.LENGTH_LONG);
        return constants;
    }

    @ReactMethod
    public void show(String message, int duration) {
        Toast.makeText(getReactApplicationContext(), message, duration).show();
    }

    @ReactMethod
    public void openBraintree(final ReadableMap options,final Promise promise) {
        isVerifyingThreeDSecure = false;

        if (!options.hasKey("clientToken")) {
            promise.reject("NO_CLIENT_TOKEN", "You must provide a client token");
            return;
        }

        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            promise.reject("NO_ACTIVITY", "There is no current activity");
            return;
        }

        DropInRequest dropInRequest = new DropInRequest().clientToken(options.getString("clientToken"));

        if (options.hasKey("threeDSecure")) {
            final ReadableMap threeDSecureOptions = options.getMap("threeDSecure");
            if (!threeDSecureOptions.hasKey("amount")) {
                promise.reject("NO_3DS_AMOUNT", "You must provide an amount for 3D Secure");
                return;
            }

            isVerifyingThreeDSecure = true;

            dropInRequest
                    .amount(String.valueOf(threeDSecureOptions.getDouble("amount")))
                    .requestThreeDSecureVerification(true);
        }

        mPromise = promise;
        currentActivity.startActivityForResult(dropInRequest.getIntent(currentActivity), DROP_IN_REQUEST);
    }

    private final ActivityEventListener mActivityListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (requestCode != DROP_IN_REQUEST || mPromise == null) {
                return;
            }

            if (resultCode == Activity.RESULT_OK) {
                DropInResult result = data.getParcelableExtra(DropInResult.EXTRA_DROP_IN_RESULT);
                PaymentMethodNonce paymentMethodNonce = result.getPaymentMethodNonce();

                if (isVerifyingThreeDSecure && paymentMethodNonce instanceof CardNonce) {
                    CardNonce cardNonce = (CardNonce) paymentMethodNonce;
                    ThreeDSecureInfo threeDSecureInfo = cardNonce.getThreeDSecureInfo();
                    if (!threeDSecureInfo.isLiabilityShiftPossible()) {
                        mPromise.reject("3DSECURE_NOT_ABLE_TO_SHIFT_LIABILITY", "3D Secure liability cannot be shifted");
                    } else if (!threeDSecureInfo.isLiabilityShifted()) {
                        mPromise.reject("3DSECURE_LIABILITY_NOT_SHIFTED", "3D Secure liability was not shifted");
                    } else {
                        resolvePayment(paymentMethodNonce);
                    }
                } else {
                    resolvePayment(paymentMethodNonce);
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                mPromise.reject("USER_CANCELLATION", "The user cancelled");
            } else {
                Exception exception = (Exception) data.getSerializableExtra(DropInActivity.EXTRA_ERROR);
                mPromise.reject(exception.getMessage(), exception.getMessage());
            }

            mPromise = null;
        }
    };

    private final void resolvePayment(PaymentMethodNonce paymentMethodNonce) {
//        mPromise.reject("USER_CANCELLATION", "The user cancelled");
        WritableMap jsResult = Arguments.createMap();
        jsResult.putString("nonce", paymentMethodNonce.getNonce());
        jsResult.putString("type", paymentMethodNonce.getTypeLabel());
        jsResult.putString("description", paymentMethodNonce.getDescription());
        jsResult.putBoolean("isDefault", paymentMethodNonce.isDefault());

        mPromise.resolve(jsResult);
    }
}
