/*
 *  Copyright (C) 2019 Team Gateship-One
 *  (Hendrik Borghorst & Frederik Luetkes)
 *
 *  The AUTHORS.md file contains a detailed contributors list:
 *  <https://gitlab.com/gateship-one/malp/blob/master/AUTHORS.md>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.gateshipone.malp.application.artwork;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.artwork.network.artprovider.FanartProvider;
import org.gateshipone.malp.application.artwork.network.artprovider.FanartTVProvider;
import org.gateshipone.malp.application.utils.NetworkUtils;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.io.File;

public class FanartManager implements FanartProvider.FanartFetchError {

    public interface OnFanartCacheChangeListener {
        void fanartCacheChanged(final MPDTrack track, final int count);
    }

    private static FanartManager mInstance;

    private final Context mContext;

    private boolean mUseFanartProvider;

    private boolean mWifiOnly;

    private FanartCache mFanartCache;

    private FanartManager(final Context context) {
        mContext = context.getApplicationContext();

        mFanartCache = FanartCache.getInstance(mContext);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        final String artistProvider = sharedPref.getString(mContext.getString(R.string.pref_artist_provider_key), mContext.getString(R.string.pref_artwork_provider_artist_default));
        mUseFanartProvider = !artistProvider.equals(mContext.getString(R.string.provider_off));
        mWifiOnly = sharedPref.getBoolean(mContext.getString(R.string.pref_download_wifi_only_key), mContext.getResources().getBoolean(R.bool.pref_download_wifi_default));
    }

    public static synchronized FanartManager getInstance(final Context context) {
        if (null == mInstance) {
            mInstance = new FanartManager(context);
        }

        return mInstance;
    }

    /**
     * TODO is this method necessary
     */
    public void setWifiOnly(boolean wifiOnly) {
        mWifiOnly = wifiOnly;
    }

    /**
     * TODO is this method necessary
     */
    public void setArtistProvider(String artistProvider) {
        mUseFanartProvider = !artistProvider.equals(mContext.getString(R.string.provider_off));
    }

    /**
     * TODO is this method necessary
     */
    public void initialize(String artistProvider, boolean wifiOnly) {
        mUseFanartProvider = !artistProvider.equals(mContext.getString(R.string.provider_off));
        mWifiOnly = wifiOnly;
    }

    public Bitmap getFanartImage(final String mbid, final int index) {
        final File file = mFanartCache.getFanart(mbid, index);

        if (null == file) {
            return null;
        }
        return BitmapFactory.decodeFile(file.getPath());
    }

    public int getFanartCount(final String mbid) {
        return mFanartCache.getFanartCount(mbid);
    }

    /**
     * TODO add callbacks
     */
    public void syncFanart(final MPDTrack track, final OnFanartCacheChangeListener fanartCacheChangeListener) {
        if (!mUseFanartProvider && !NetworkUtils.isDownloadAllowed(mContext, mWifiOnly)) {
            return;
        }

        if (track.getTrackAlbumMBID().isEmpty()) {
            // resolve mbid
            FanartTVProvider.getInstance(mContext).getTrackArtistMBID(track, trackMBID -> {
                track.setTrackAlbumArtistMBID(trackMBID);

                loadFanartImages(track, fanartCacheChangeListener);
            }, this);
        } else {
            loadFanartImages(track, fanartCacheChangeListener);
        }
    }

    @Override
    public void imageListFetchError() {
        // TODO add error handling
    }

    @Override
    public void fanartFetchError(MPDTrack track) {
        // TODO add error handling
    }

    private void loadFanartImages(final MPDTrack track, final OnFanartCacheChangeListener fanartCacheChangeListener) {
        // initial return the cache count
        fanartCacheChangeListener.fanartCacheChanged(track, mFanartCache.getFanartCount(track.getTrackArtistMBID()));

        FanartTVProvider.getInstance(mContext).getArtistFanartURLs(track.getTrackArtistMBID(),
                artistURLs -> {
                    for (final String url : artistURLs) {
                        // Check if the given image is in the cache already.
                        if (mFanartCache.inCache(track.getTrackArtistMBID(), String.valueOf(url.hashCode()))) {
                            continue;
                        }

                        loadSingleFanartImage(track, url, fanartCacheChangeListener);
                    }
                }, this);
    }

    private void loadSingleFanartImage(final MPDTrack track, final String imageURL, final OnFanartCacheChangeListener fanartCacheChangeListener) {
        FanartTVProvider.getInstance(mContext).getFanartImage(track, imageURL,
                response -> {
                    //TODO scale the image
                    mFanartCache.addFanart(track.getTrackArtistMBID(), String.valueOf(response.hashCode()), response.image);

                    fanartCacheChangeListener.fanartCacheChanged(track, mFanartCache.getFanartCount(track.getTrackArtistMBID()));
                },
                error -> {
                    // TODO add error handling
                });
    }
}
