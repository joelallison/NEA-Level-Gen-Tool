package com.joelallison.generation;

import com.joelallison.display.Tileset;

public class Layer<Setting extends GenSetting> {

    //an array of layers - lower index means lower down, just makes sense as it expands into higher numbers

    //when you move the layer, a layer gets stored separately, another gets copied over to where it will move,
    // and then the separate one gets stored in its final place, writing over the recently moved one's old duplicate

    //layer has name, settings, chosen tileset and tile children that function in a way specific to the gen type
    //the layer itself can be moved up or down
    //layer settings edited on the left, upon selecting the layer
    //children boundaries etc. are edited on the right as part of the layer box.
    //show/hide layer button?
    //clipping mode?
    //the spacing and wave collapse feature?

    private String layerName;
    public Setting settings;
    public Tileset tileSet;
    private boolean showLayer;



}