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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;

import org.gateshipone.malp.BuildConfig;
import org.gateshipone.malp.R;
import org.gateshipone.malp.application.artwork.network.ArtworkRequestModel;
import org.gateshipone.malp.application.artwork.network.InsertImageTask;
import org.gateshipone.malp.application.artwork.network.MALPRequestQueue;
import org.gateshipone.malp.application.artwork.network.artprovider.ArtProvider;
import org.gateshipone.malp.application.artwork.network.artprovider.FanartTVProvider;
import org.gateshipone.malp.application.artwork.network.artprovider.HTTPAlbumImageProvider;
import org.gateshipone.malp.application.artwork.network.artprovider.LastFMProvider;
import org.gateshipone.malp.application.artwork.network.artprovider.MPDAlbumImageProvider;
import org.gateshipone.malp.application.artwork.network.artprovider.MusicBrainzProvider;
import org.gateshipone.malp.application.artwork.network.responses.ImageResponse;
import org.gateshipone.malp.application.artwork.storage.ArtworkDatabaseManager;
import org.gateshipone.malp.application.artwork.storage.ImageNotFoundException;
import org.gateshipone.malp.application.utils.BitmapUtils;
import org.gateshipone.malp.application.utils.NetworkUtils;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;
import org.json.JSONException;

import java.util.ArrayList;

public class ArtworkManager implements ArtProvider.ArtFetchError, InsertImageTask.ImageSavedCallback {
    private static final String TAG = ArtworkManager.class.getSimpleName();
    /**
     * Set this flag to enable debugging in this class. DISABLE before releasing
     */
    private static final boolean DEBUG_ENABLED = BuildConfig.DEBUG;

    /**
     * Interface used for adapters to be notified about data set changes
     */
    public interface onNewArtistImageListener {
        void newArtistImage(MPDArtist artist);
    }

    /**
     * Interface used for adapters to be notified about data set changes
     */
    public interface onNewAlbumImageListener {
        void newAlbumImage(MPDAlbum album);
    }


    /**
     * Broadcast constants
     */
    public static final String ACTION_NEW_ARTWORK_READY = "org.gateshipone.malp.action_new_artwork_ready";

    public static final String INTENT_EXTRA_KEY_ALBUM_NAME = "org.gateshipone.malp.extra.album_name";

    private static final String INTENT_EXTRA_KEY_ARTIST_MBID = "org.gateshipone.malp.extra.artist_mbid";

    private static final String INTENT_EXTRA_KEY_ARTIST_NAME = "org.gateshipone.malp.extra.artist_name";

    private static final String INTENT_EXTRA_KEY_ALBUM_MBID = "org.gateshipone.malp.extra.album_mbid";

    /**
     * Private static singleton instance that can be used by other classes via the
     * getInstance method.
     */
    private static ArtworkManager mInstance;

    /**
     * Settings string which artist download provider to use
     */
    private String mArtistProvider;

    /**
     * Settings string which album download provider to use
     */
    private String mAlbumProvider;

    /**
     * Settings value if artwork download is only allowed via wifi/wired connection.
     */
    private boolean mWifiOnly;


    /**
     * Manager for the SQLite database handling
     */
    private ArtworkDatabaseManager mDBManager;

    /**
     * List of observers that needs updating if a new ArtistImage is downloaded.
     */
    private final ArrayList<onNewArtistImageListener> mArtistListeners;

    /**
     * List of observers that needs updating if a new AlbumImage is downloaded.
     */
    private final ArrayList<onNewAlbumImageListener> mAlbumListeners;

    /**
     * Private {@link Context} used for all kinds of things like Broadcasts.
     * It is using the ApplicationContext so it should be safe against
     * memory leaks.
     */
    private Context mContext;

    private ArtworkManager(Context context) {

        mDBManager = ArtworkDatabaseManager.getInstance(context.getApplicationContext());

        mArtistListeners = new ArrayList<>();
        mAlbumListeners = new ArrayList<>();

        mContext = context.getApplicationContext();

        ConnectionStateReceiver receiver = new ConnectionStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(receiver, filter);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        mArtistProvider = sharedPref.getString(context.getString(R.string.pref_artist_provider_key), context.getString(R.string.pref_artwork_provider_artist_default));
        mAlbumProvider = sharedPref.getString(context.getString(R.string.pref_album_provider_key), context.getString(R.string.pref_artwork_provider_album_default));
        mWifiOnly = sharedPref.getBoolean(context.getString(R.string.pref_download_wifi_only_key), context.getResources().getBoolean(R.bool.pref_download_wifi_default));

        MPDAlbumImageProvider.getInstance().setResponseLooper(Looper.getMainLooper());
    }

    public static synchronized ArtworkManager getInstance(Context context) {
        if (null == mInstance) {
            mInstance = new ArtworkManager(context);
        }
        return mInstance;
    }

    public void setWifiOnly(boolean wifiOnly) {
        mWifiOnly = wifiOnly;
    }

    public void setAlbumProvider(String albumProvider) {
        mAlbumProvider = albumProvider;
    }

    public void setArtistProvider(String artistProvider) {
        mArtistProvider = artistProvider;
    }

    public void initialize(String artistProvider, String albumProvider, boolean wifiOnly) {
        mArtistProvider = artistProvider;
        mAlbumProvider = albumProvider;
        mWifiOnly = wifiOnly;
    }


    /**
     * Removes the image for the album and tries to reload it from the internet
     *
     * @param album {@link MPDAlbum} to reload the image for
     */
    public void resetAlbumImage(final MPDAlbum album) {
        if (null == album) {
            return;
        }

        // Clear the old image
        mDBManager.removeAlbumImage(mContext, album);

        // Clear the old image from the cache
        BitmapCache.getInstance().removeAlbumBitmap(album);

        // Reload the image from the internet
        fetchImage(album);
    }


    /**
     * Removes the image for the artist and tries to reload it from the internet
     *
     * @param artist {@link MPDArtist} to reload the image for
     */
    public void resetArtistImage(final MPDArtist artist) {
        if (null == artist) {
            return;
        }

        // Clear the old image
        mDBManager.removeArtistImage(mContext, artist);

        // Clear the old image from the cache
        BitmapCache.getInstance().removeArtistImage(artist);

        // Reload the image from the internet
        fetchImage(artist);
    }

    public Bitmap getImage(final MPDArtist artist, int width, int height, boolean skipCache) throws ImageNotFoundException {
        if (null == artist) {
            return null;
        }

        if (!skipCache) {
            // Try cache first
            Bitmap cacheImage = BitmapCache.getInstance().requestArtistImage(artist);
            if (cacheImage != null && width <= cacheImage.getWidth() && height <= cacheImage.getWidth()) {
                return cacheImage;
            }
        }

        final String image = mDBManager.getArtistImage(mContext, artist);

        // Checks if the database has an image for the requested artist
        if (null != image) {
            // Create a bitmap from the data blob in the database
            Bitmap bm = BitmapUtils.decodeSampledBitmapFromFile(image, width, height);
            BitmapCache.getInstance().putArtistImage(artist, bm);
            return bm;
        }
        return null;
    }

    public Bitmap getImage(final MPDTrack track, int width, int height, boolean skipCache) throws ImageNotFoundException {
        if (null == track || track.getTrackAlbum().isEmpty()) {
            return null;
        }
        if (DEBUG_ENABLED) {
            Log.v(TAG,"Artwork track request: " + track.getFilename() + " skipCache: " + skipCache);
        }

        if (!skipCache) {
            // Try cache first
            Bitmap cacheBitmap = BitmapCache.getInstance().requestTrackBitmap(track);
            if (null != cacheBitmap && width <= cacheBitmap.getWidth() && height <= cacheBitmap.getWidth()) {
                if (DEBUG_ENABLED) {
                    Log.v(TAG,"Image found in cache");
                }
                return cacheBitmap;
            }
        }

        final String image = mDBManager.getTrackImage(mContext, track);

        // Checks if the database has an image for the requested album
        if (null != image) {
            if (DEBUG_ENABLED) {
                Log.v(TAG,"Image found in database");
            }
            // Create a bitmap from the data blob in the database
            Bitmap bm = BitmapUtils.decodeSampledBitmapFromFile(image, width, height);
            BitmapCache.getInstance().putTrackBitmap(track, bm);
            return bm;
        }
        return null;
    }

    public Bitmap getImage(final MPDAlbum album, int width, int height, boolean skipCache) throws ImageNotFoundException {
        if (null == album || album.getName().isEmpty()) {
            return null;
        }

        if (!skipCache) {
            // Try cache first
            Bitmap cacheBitmap = BitmapCache.getInstance().requestAlbumBitmap(album);
            if (null != cacheBitmap && width <= cacheBitmap.getWidth() && height <= cacheBitmap.getWidth()) {
                return cacheBitmap;
            }
        }

        final String image = mDBManager.getAlbumImage(mContext, album);

        // Checks if the database has an image for the requested album
        if (null != image) {
            // Create a bitmap from the data blob in the database
            Bitmap bm = BitmapUtils.decodeSampledBitmapFromFile(image, width, height);
            BitmapCache.getInstance().putAlbumBitmap(album, bm);
            return bm;
        }
        return null;
    }

    /**
     * Starts an asynchronous fetch for the image of the given artist.
     *
     * @param artist             Artist to fetch an image for.
     * @param imageSavedCallback Callback if an image was saved.
     * @param errorCallback      Callback if an error occured.
     */
    void fetchImage(final MPDArtist artist,
                    final InsertImageTask.ImageSavedCallback imageSavedCallback,
                    final ArtProvider.ArtFetchError errorCallback) {
        if (!NetworkUtils.isDownloadAllowed(mContext, mWifiOnly)) {
            return;
        }

        final ArtworkRequestModel requestModel = new ArtworkRequestModel(artist);

        if (mArtistProvider.equals(mContext.getString(R.string.pref_artwork_provider_fanarttv_key))) {
            FanartTVProvider.getInstance(mContext).fetchImage(requestModel, mContext,
                    response -> new InsertImageTask(mContext, imageSavedCallback).execute(response),
                    errorCallback);
        }
    }

    /**
     * Starts an asynchronous fetch for the image of the given artist.
     * This method will use internal callbacks.
     *
     * @param artist Artist to fetch an image for.
     */
    public void fetchImage(final MPDArtist artist) {
        fetchImage(artist, this, this);
    }

    /**
     * Starts an asynchronous fetch for the image of the given album.
     *
     * @param album              Album to fetch an image for.
     * @param imageSavedCallback Callback if an image was saved.
     * @param errorCallback      Callback if an error occured.
     */
    void fetchImage(final MPDAlbum album,
                    final InsertImageTask.ImageSavedCallback imageSavedCallback,
                    final ArtProvider.ArtFetchError errorCallback) {
        if (!NetworkUtils.isDownloadAllowed(mContext, mWifiOnly)) {
            return;
        }

        ArtworkRequestModel requestModel = new ArtworkRequestModel(album);

        if (mAlbumProvider.equals(mContext.getString(R.string.pref_artwork_provider_musicbrainz_key))) {
            MusicBrainzProvider.getInstance(mContext).fetchImage(requestModel, mContext,
                    response -> new InsertImageTask(mContext, imageSavedCallback).execute(response),
                    errorCallback);
        } else if (mAlbumProvider.equals(mContext.getString(R.string.pref_artwork_provider_lastfm_key))) {
            LastFMProvider.getInstance(mContext).fetchImage(requestModel, mContext,
                    response -> new InsertImageTask(mContext, imageSavedCallback).execute(response),
                    errorCallback);
        }
    }

    /**
     * Starts an asynchronous fetch for the image of the given album.
     * This method will use internal callbacks.
     *
     * @param album Album to fetch an image for.
     */
    public void fetchImage(final MPDAlbum album) {
        fetchImage(album, this, this);
    }

    public void fetchImage(final MPDTrack track) {
        fetchImage(track, this, this);
    }

    public void fetchImage(final MPDTrack track,
                           final InsertImageTask.ImageSavedCallback imageSavedCallback,
                           final ArtProvider.ArtFetchError errorCallback) {
        final ArtworkRequestModel requestModel = new ArtworkRequestModel(track);

        if (DEBUG_ENABLED) {
            Log.v(TAG,"fetchImage for track: " + track.getFilename());
        }
        if (MPDAlbumImageProvider.getInstance().getActive()) {
            if (DEBUG_ENABLED) {
                Log.v(TAG, "MPDAlbumImageProvider used");
            }
            // Check if MPD cover transfer is activated
            MPDAlbumImageProvider.getInstance().fetchImage(requestModel, mContext,
                    response -> new InsertImageTask(mContext, imageSavedCallback).execute(response),
                    errorCallback);
        } else if (HTTPAlbumImageProvider.getInstance(mContext).getActive()) {
            // Check if user-specified HTTP cover download is activated
            HTTPAlbumImageProvider.getInstance(mContext).fetchImage(requestModel, mContext,
                    response -> new InsertImageTask(mContext, imageSavedCallback).execute(response),
                    errorCallback);
        } else {
            // Use a dummy album to fetch the image
            final MPDAlbum album = new MPDAlbum(track.getTrackAlbum());
            album.setMBID(track.getTrackAlbumMBID());
            album.setArtistName(track.getTrackAlbumArtist());

            fetchImage(album, imageSavedCallback, errorCallback);
        }
    }

    /**
     * Registers a listener that gets notified when a new artist image was added to the dataset.
     *
     * @param listener Listener to register
     */
    public void registerOnNewArtistImageListener(onNewArtistImageListener listener) {
        if (null != listener) {
            synchronized (mArtistListeners) {
                mArtistListeners.add(listener);
            }
        }
    }

    /**
     * Unregisters a listener that got notified when a new artist image was added to the dataset.
     *
     * @param listener Listener to unregister
     */
    public void unregisterOnNewArtistImageListener(onNewArtistImageListener listener) {
        if (null != listener) {
            synchronized (mArtistListeners) {
                mArtistListeners.remove(listener);
            }
        }
    }

    /**
     * Registers a listener that gets notified when a new album image was added to the dataset.
     *
     * @param listener Listener to register
     */
    public void registerOnNewAlbumImageListener(onNewAlbumImageListener listener) {
        if (null != listener) {
            synchronized (mArtistListeners) {
                mAlbumListeners.add(listener);
            }
        }
    }

    /**
     * Unregisters a listener that got notified when a new album image was added to the dataset.
     *
     * @param listener Listener to unregister
     */
    public void unregisterOnNewAlbumImageListener(onNewAlbumImageListener listener) {
        if (null != listener) {
            synchronized (mArtistListeners) {
                mAlbumListeners.remove(listener);
            }
        }
    }

    @Override
    public void onImageSaved(final ArtworkRequestModel artworkRequestModel, final Context applicationContext) {
        broadcastNewArtwokInfo(artworkRequestModel, applicationContext);

        switch (artworkRequestModel.getType()) {
            case ALBUM:
                synchronized (mAlbumListeners) {
                    for (onNewAlbumImageListener albumListener : mAlbumListeners) {
                        albumListener.newAlbumImage(((MPDAlbum) artworkRequestModel.getGenericModel()));
                    }
                }
                break;
            case ARTIST:
                synchronized (mArtistListeners) {
                    for (onNewArtistImageListener artistListener : mArtistListeners) {
                        artistListener.newArtistImage((MPDArtist) artworkRequestModel.getGenericModel());
                    }
                }
                break;
            case TRACK:
                final MPDTrack track = (MPDTrack) artworkRequestModel.getGenericModel();
                final MPDAlbum album = new MPDAlbum(track.getTrackAlbum());
                album.setMBID(track.getTrackAlbumMBID());
                album.setArtistName(track.getTrackAlbumArtist());
                synchronized (mAlbumListeners) {
                    for (onNewAlbumImageListener albumListener : mAlbumListeners) {
                        albumListener.newAlbumImage(album);
                    }
                }
                break;
        }
    }

    @Override
    public void fetchJSONException(ArtworkRequestModel model, Context context, JSONException exception) {
        Log.e(TAG, "JSONException fetching: " + model.getLoggingString());
        ImageResponse imageResponse = new ImageResponse();
        imageResponse.model = model;
        imageResponse.image = null;
        imageResponse.url = null;
        new InsertImageTask(context, this).execute(imageResponse);
    }

    @Override
    public void fetchVolleyError(ArtworkRequestModel model, Context context, VolleyError error) {
        Log.e(TAG, "VolleyError for request: " + model.getLoggingString());

        if (error != null) {
            NetworkResponse networkResponse = error.networkResponse;
            if (networkResponse != null && networkResponse.statusCode == 503) {
                cancelAllRequests(context);
                return;
            }
        }

        ImageResponse imageResponse = new ImageResponse();
        imageResponse.model = model;
        imageResponse.image = null;
        imageResponse.url = null;
        new InsertImageTask(context, this).execute(imageResponse);
    }

    /**
     * This will cancel the last used album/artist image providers. To make this useful on connection change
     * it is important to cancel all requests when changing the provider in settings.
     */
    public void cancelAllRequests(Context context) {
        MALPRequestQueue.getInstance(context).cancelAll(request -> true);
    }

    /**
     * Used to broadcast information about new available artwork to {@link BroadcastReceiver} like
     * the {@link org.gateshipone.malp.application.background.WidgetProvider} to reload its artwork.
     *
     * @param model   The model that an image was inserted for.
     * @param context Context used for broadcasting
     */
    private void broadcastNewArtwokInfo(ArtworkRequestModel model, Context context) {
        Intent newImageIntent = new Intent(ACTION_NEW_ARTWORK_READY);

        switch (model.getType()) {
            case ALBUM:
                MPDAlbum album = (MPDAlbum) model.getGenericModel();
                newImageIntent.putExtra(INTENT_EXTRA_KEY_ALBUM_MBID, album.getMBID());
                newImageIntent.putExtra(INTENT_EXTRA_KEY_ALBUM_NAME, album.getName());
                break;
            case ARTIST:
                MPDArtist artist = (MPDArtist) model.getGenericModel();
                newImageIntent.putExtra(INTENT_EXTRA_KEY_ARTIST_MBID, artist.getMBIDCount() > 0 ? artist.getMBID(0) : "");
                newImageIntent.putExtra(INTENT_EXTRA_KEY_ARTIST_NAME, artist.getArtistName());
                break;
            case TRACK:
                final MPDTrack track = (MPDTrack) model.getGenericModel();
                newImageIntent.putExtra(INTENT_EXTRA_KEY_ALBUM_MBID, track.getTrackAlbumMBID());
                newImageIntent.putExtra(INTENT_EXTRA_KEY_ALBUM_NAME, track.getTrackAlbum());
                break;
        }

        context.sendBroadcast(newImageIntent);
    }

    /**
     * Called if the connection state of the device is changing. This ensures no data is downloaded
     * if it is not intended (mobile data connection).
     */
    private class ConnectionStateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!NetworkUtils.isDownloadAllowed(context, mWifiOnly)) {
                // Cancel all downloads
                Log.v(TAG, "Cancel all downloads because of connection change");
                cancelAllRequests(context);
            }
        }
    }
}
