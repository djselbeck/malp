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

package org.gateshipone.malp.application.loaders;


import android.content.Context;
import androidx.loader.content.Loader;

import java.lang.ref.WeakReference;
import java.util.List;

import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseOutputList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDOutput;


public class OutputsLoader extends Loader<List<MPDOutput>> {

    private OutputResponseHandler mOutputResponseHandler;


    public OutputsLoader(Context context) {
        super(context);
        mOutputResponseHandler = new OutputResponseHandler(this);
    }


    private static class OutputResponseHandler extends MPDResponseOutputList {
        private WeakReference<OutputsLoader> mOutputsLoader;

        private OutputResponseHandler(OutputsLoader loader) {
            mOutputsLoader = new WeakReference<>(loader);
        }

        @Override
        public void handleOutputs(List<MPDOutput> outputList) {
            OutputsLoader loader = mOutputsLoader.get();

            if (loader != null) {
               loader.deliverResult(outputList);
            }
        }
    }


    @Override
    public void onStartLoading() {
        forceLoad();
    }

    @Override
    public void onStopLoading() {

    }

    @Override
    public void onForceLoad() {
        MPDQueryHandler.getOutputs(mOutputResponseHandler);
    }
}
