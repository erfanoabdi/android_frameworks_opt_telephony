/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.TimestampedValue;

import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * An interface for the Android component that handles NITZ and related signals for time and time
 * zone detection.
 *
 * {@hide}
 */
public interface NitzStateMachine {

    /**
     * Called when the network country is set on the Phone. Although set, the network country code
     * may be invalid.
     *
     * @param countryChanged true when the country code is known to have changed, false if it
     *     probably hasn't
     */
    void handleNetworkCountryCodeSet(boolean countryChanged);

    /**
     * Informs the {@link NitzStateMachine} that the network has become available.
     */
    void handleNetworkAvailable();

    /**
     * Informs the {@link NitzStateMachine} that the country code from network has become
     * unavailable.
     */
    void handleNetworkCountryCodeUnavailable();

    /**
     * Handle a new NITZ signal being received.
     */
    void handleNitzReceived(@NonNull TimestampedValue<NitzData> nitzSignal);

    /**
     * Handle the user putting the device into or out of airplane mode
     * @param on true if airplane mode has been turned on, false if it's been turned off.
     */
    void handleAirplaneModeChanged(boolean on);

    /**
     * Dumps the current in-memory state to the supplied PrintWriter.
     */
    void dumpState(PrintWriter pw);

    /**
     * Dumps the time / time zone logs to the supplied IndentingPrintWriter.
     */
    void dumpLogs(FileDescriptor fd, IndentingPrintWriter ipw, String[] args);

    /**
     * A proxy over read-only device state that allows things like system properties, elapsed
     * realtime clock to be faked for tests.
     */
    interface DeviceState {

        /**
         * If time between NITZ updates is less than {@link #getNitzUpdateSpacingMillis()} the
         * update may be ignored.
         */
        int getNitzUpdateSpacingMillis();

        /**
         * If {@link #getNitzUpdateSpacingMillis()} hasn't been exceeded but update is >
         * {@link #getNitzUpdateDiffMillis()} do the update
         */
        int getNitzUpdateDiffMillis();

        /**
         * Returns true if the {@code gsm.ignore-nitz} system property is set to "yes".
         */
        boolean getIgnoreNitz();

        @Nullable String getNetworkCountryIsoForPhone();

        /**
         * Returns the same value as {@link SystemClock#elapsedRealtime()}.
         */
        long elapsedRealtime();

        /**
         * Returns the same value as {@link System#currentTimeMillis()}.
         */
        long currentTimeMillis();
    }

    /**
     * The real implementation of {@link DeviceState}.
     *
     * {@hide}
     */
    class DeviceStateImpl implements DeviceState {
        private static final int NITZ_UPDATE_SPACING_DEFAULT = 1000 * 60 * 10;
        private final int mNitzUpdateSpacing;

        private static final int NITZ_UPDATE_DIFF_DEFAULT = 2000;
        private final int mNitzUpdateDiff;

        private final Phone mPhone;
        private final TelephonyManager mTelephonyManager;
        private final ContentResolver mCr;

        public DeviceStateImpl(Phone phone) {
            mPhone = phone;

            Context context = phone.getContext();
            mTelephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            mCr = context.getContentResolver();
            mNitzUpdateSpacing =
                    SystemProperties.getInt("ro.nitz_update_spacing", NITZ_UPDATE_SPACING_DEFAULT);
            mNitzUpdateDiff =
                    SystemProperties.getInt("ro.nitz_update_diff", NITZ_UPDATE_DIFF_DEFAULT);
        }

        @Override
        public int getNitzUpdateSpacingMillis() {
            return Settings.Global.getInt(mCr, Settings.Global.NITZ_UPDATE_SPACING,
                    mNitzUpdateSpacing);
        }

        @Override
        public int getNitzUpdateDiffMillis() {
            return Settings.Global.getInt(mCr, Settings.Global.NITZ_UPDATE_DIFF, mNitzUpdateDiff);
        }

        @Override
        public boolean getIgnoreNitz() {
            String ignoreNitz = SystemProperties.get("gsm.ignore-nitz");
            return ignoreNitz != null && ignoreNitz.equals("yes");
        }

        @Override
        @Nullable
        public String getNetworkCountryIsoForPhone() {
            return mTelephonyManager.getNetworkCountryIsoForPhone(mPhone.getPhoneId());
        }

        @Override
        public long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }
}
