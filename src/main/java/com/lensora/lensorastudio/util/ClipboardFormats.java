package com.lensora.lensorastudio.util;

import javafx.scene.input.DataFormat;

public final class ClipboardFormats 
{

    private ClipboardFormats() {}

    public static final DataFormat CUT          = DataFormat.lookupMimeType("lensora/cut") != null
                                                ? DataFormat.lookupMimeType("lensora/cut")
                                                : new DataFormat("lensora/cut");
}
