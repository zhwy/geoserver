package org.geoserver.web.publishtool;

import org.geoserver.catalog.Catalog;
import org.geotools.data.util.DefaultProgressListener;
import org.opengis.util.ProgressListener;

public class Convert2Shp {

    private Catalog catalog;

    public Convert2Shp(Catalog catalog) {
        catalog = catalog;
        ProgressListener listener = new DefaultProgressListener();
    }
}
