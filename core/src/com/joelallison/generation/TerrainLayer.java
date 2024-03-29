package com.joelallison.generation;

import com.badlogic.gdx.math.Vector2;
import com.joelallison.graphics.Tileset;
import tools.OpenSimplex2S; //K.jpg's OpenSimplex 2, smooth variant ("SuperSimplex") - https://github.com/KdotJPG/OpenSimplex2/blob/master/java/OpenSimplex2S.java

import java.util.*;

import static com.joelallison.screens.AppScreen.tilesets;

public class TerrainLayer extends Layer {
    private float scale;
    private int octaves;
    private float lacunarity;
    private int wrap;
    private boolean invert;
    public float[][] valueMap;
    public List<Tileset.TerrainTileSpec> tileSpecs;

    static final public float SCALE_MAX = 256f;
    static final public float SCALE_MIN = 0.005f;
    static final public int OCTAVES_MAX = 3;
    static final public int OCTAVES_MIN = 1;
    static final public float LACUNARITY_MAX = 10f;
    static final public float LACUNARITY_MIN = 0.01f;
    static final public int WRAP_MAX = 20;
    static final public int WRAP_MIN = 1;

    //for custom loading, in which tilespecs is declared separately
    public TerrainLayer(int layerID, String name, Long seed, float scaleVal, int octavesVal, float lacunarityVal, int wrapVal, boolean invert) {
        super(name, seed, layerID);
        this.scale = scaleVal;
        this.octaves = octavesVal;
        this.lacunarity = lacunarityVal;
        this.wrap = wrapVal;
        this.invert = invert;

        this.tileSpecs = new ArrayList<>();
    }

    //for loading with very little input
    public TerrainLayer(Long seed) {
        super(seed);
        Random random = new Random();
        this.name = "Terrain Layer";
        if(seed == -1L){
            this.seed = random.nextLong();
        } else {
            this.seed = seed;
        }

        //while I feel that the max and min values I've defined are good,
        // I also think that the layer that users might end up starting with should be a bit tamer (so I've scaled down the value ranges).
        this.scale = random.nextFloat(10*SCALE_MIN, SCALE_MAX/10);
        this.octaves = random.nextInt(OCTAVES_MIN, OCTAVES_MAX);
        this.lacunarity = random.nextFloat(10*LACUNARITY_MIN, LACUNARITY_MAX/10);
        this.wrap = random.nextInt(WRAP_MIN, WRAP_MAX/4);
        this.invert = random.nextBoolean();

        tileSpecs = new ArrayList<>();
    }

    @Override
    public void defaultTileValues() {
        Set<String> tiles = tilesets.get(this.tilesetName).map.keySet();

        //set tile values to be evenly distributed
        int tileNum = 0;
        for (String tile : tiles) {
            this.tileSpecs.add(new Tileset.TerrainTileSpec(tile, (float) tileNum * (1f / tiles.size())));
            tileNum++;
        }
    }

    @Override
    public void sortTileSpecs() {
        //using method outlined here: https://www.java67.com/2015/01/how-to-sort-hashmap-in-java-based-on.html
        final Comparator<Tileset.TerrainTileSpec> THRESHOLD_COMPARATOR  = new Comparator<Tileset.TerrainTileSpec>() {
            @Override
            public int compare(Tileset.TerrainTileSpec t1, Tileset.TerrainTileSpec t2){
                return t2.lowerBound.compareTo(t1.lowerBound);
            }
        };

        this.tileSpecs.sort(THRESHOLD_COMPARATOR);
    }

    public void genValueMap(Long seed, Vector2 dimensions, int xOffset, int yOffset) {
        valueMap = genTerrain(seed, dimensions, xOffset, yOffset, this.getScale(), this.getOctaves(), this.getLacunarity(), this.getWrap(), this.isInverted());
    }

    public float getScale() {
        return scale;
    }
    public void setScale(float scale) {
        this.scale = scale;
    }
    public int getOctaves() {
        return octaves;
    }
    public void setOctaves(int octaves) {
        this.octaves = octaves;
    }
    public float getLacunarity() {
        return lacunarity;
    }
    public void setLacunarity(float lacunarity) {
        this.lacunarity = lacunarity;
    }
    public int getWrap() {
        return wrap;
    }
    public void setWrap(int wrap) {
        this.wrap = wrap;
    }
    public boolean isInverted() {
        return invert;
    }
    public void setInvert(boolean invert) {
        this.invert = invert;
    }


    //generation stuff is from here onwards
    public static float[][] genTerrain(long seed, Vector2 Dimensions, int xOffset, int yOffset, float scale, int octaves, float lacunarity, int wrapFactor, boolean invertWrap) {
        //greater scale zooms in, halved scale from normal could be used for map data. I think scale of 4 is best for most stuff
        //higher octaves adds more detail to the noise, but 2 is the best option for this [in most cases]
        //higher persistence makes the values tend towards higher values
        //lower lacunarity is smoother looking - higher lacunarity for things like trees, lower for grass

        //if you double the scale but double the lacunarity, the output is basically  the same. if you double the scale and halve the lacunarity, it's like you quadrupled the scale.

        float[][] noiseMap = new float[(int) Dimensions.x][(int) Dimensions.y];

        float minNoiseHeight = Float.MAX_VALUE;
        float maxNoiseHeight = Float.MIN_VALUE;

        for (int y = yOffset; y < Dimensions.y + yOffset; y++) {
            for (int x = xOffset; x < Dimensions.x + xOffset; x++) {

                float amplitude = 1;
                float frequency = 1;
                float noiseHeight = 0;

                for (int i = 0; i < octaves; i++) {

                    float sampleX = (x-(Dimensions.x/2f)) / scale * frequency;
                    float sampleY = (y-(Dimensions.y/2f)) / scale * frequency;

                    // x2 - 1 moves the noise to be between 0 and 1, the noise function returns values between -1 and 1.
                    float noiseValue = (OpenSimplex2S.noise2_ImproveX(seed, sampleX, sampleY) * 2f - 1f);
                    noiseHeight = noiseValue * amplitude;

                    frequency *= lacunarity;
                }

                if (noiseHeight > maxNoiseHeight) {
                    maxNoiseHeight = noiseHeight;
                } else if (noiseHeight < minNoiseHeight) {
                    minNoiseHeight = noiseHeight;
                }

                noiseMap[x-xOffset][y-yOffset] = noiseHeight;
            }
        }

            for (int y = 0; y < Dimensions.y; y++) { //normalises the noise
                for (int x = 0; x < Dimensions.x; x++) {
                    //does the wrap
                    noiseMap[x][y] = wrapValue((inverseLERP(noiseMap[x][y], minNoiseHeight, maxNoiseHeight)), wrapFactor, invertWrap);
                }
            }

        return noiseMap;
    }

    public static float inverseLERP(float x, float a, float b) {
        return (x - a) / (b - a);
    }

    public static float wrapValue(float input, int factor, boolean invert) {
        float value = input;
        if (factor != 1) {
            value = Math.abs(((input * factor) - factor / 2));
        }
        if (invert) {
            return (value * -1) + 1;
        } else {
            return value;
        }

    }

}

