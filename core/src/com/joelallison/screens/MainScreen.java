package com.joelallison.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.google.gson.Gson;
import com.joelallison.display.Tileset;
import com.joelallison.generation.Layer;
import com.joelallison.user.UserControls;
import com.joelallison.generation.FileHandling;
import com.joelallison.generation.TerrainLayer;
import com.joelallison.screens.UserInterface.MainInterface;

public class MainScreen implements Screen {
	Stage mainUIStage;
	public ExtendViewport viewport;
	public static OrthographicCamera camera;
	SpriteBatch batch;
	ShapeRenderer sr; //for misc UI additions
	ExtendViewport levelViewport;
	OrthographicCamera levelCamera;
	public static Layer[] layers = new Layer[1];

	public static final int TILE_SIZE = 32;
	public static final int CHUNK_SIZE = 7;
	public static final Vector2 LEVEL_ASPECT_RATIO = new Vector2(4, 3);
	public static final int LEVEL_ASPECT_SCALAR = 2;
	public static final Vector2 MAP_DIMENSIONS = new Vector2(CHUNK_SIZE * LEVEL_ASPECT_SCALAR * LEVEL_ASPECT_RATIO.x, CHUNK_SIZE* LEVEL_ASPECT_SCALAR * LEVEL_ASPECT_RATIO.y);

	public static final Vector2 WINDOW_ASPECT_RATIO = new Vector2(16, 9);
	int xPos, yPos;
	public static UserControls userControls;
	float stateTime;
	Gson gson = new Gson();
	Tileset[] tilesets;
	MainInterface userInterface = new MainInterface();
	public MainScreen() {
		camera = new OrthographicCamera(1920, 1080);
		viewport = new ExtendViewport(1920, 1080, camera);

		batch = new SpriteBatch();

		camera.zoom = 1.2f;
		viewport.apply(true);

		levelCamera = new OrthographicCamera(MAP_DIMENSIONS.x, MAP_DIMENSIONS.y);
		levelViewport = new ExtendViewport(MAP_DIMENSIONS.x*TILE_SIZE, MAP_DIMENSIONS.y*TILE_SIZE, levelCamera);

		xPos = 0;
		yPos = 0;

		//declare player stuff
		userControls = new UserControls(0, 0);

		tilesets = gson.fromJson(FileHandling.readJSONTileData("core/src/com/joelallison/display/tilesets.json"), Tileset[].class);
		for (Tileset t : tilesets) { t.initTexture(); }

		batch = new SpriteBatch();
		sr = new ShapeRenderer();

		stateTime = 0f;

		layers[0] = new TerrainLayer("Terrain!!", 3L, 20f, 2, 2f, 1, false);
		mainUIStage = userInterface.genStage(mainUIStage);
		userInterface.genUI(mainUIStage);

	}

	public void getTiles() {
		((TerrainLayer) layers[0]).tileset = tilesets[0];
		((TerrainLayer) layers[0]).tileset.setColor(new Color(0.1215686f, 0.09411765f, 0.07843137f, 1));
		((TerrainLayer) layers[0]).tileChildren = new Tileset.TileChild[] {
				new Tileset.TileChild("plant", 0.35f),
				new Tileset.TileChild("bush", 0.4f),
				new Tileset.TileChild("tree_1", 0.6f),
				new Tileset.TileChild("tree_2", 0.7f)
		};
	}

	@Override
	public void render(float delta) {
		stateTime += Gdx.graphics.getDeltaTime();

		viewport.apply();
		camera.zoom = MathUtils.clamp(camera.zoom, 0.2f, 4f); //set limits for the size of the viewport
		batch.setProjectionMatrix(viewport.getCamera().combined);
		sr.setProjectionMatrix(viewport.getCamera().combined);
		camera.update();

		userControls.handleInput();
		xPos = userControls.getxPosition();
		yPos = userControls.getyPosition();

		getTiles();
		ScreenUtils.clear(((TerrainLayer) layers[0]).tileset.getColor());

		batch.begin();

		drawLayers();

		batch.end();

		//draw border around the generated outcome
		sr.begin(ShapeRenderer.ShapeType.Line);
		sr.setColor(Color.WHITE);
		sr.rect(0f, 0f, MAP_DIMENSIONS.x*TILE_SIZE, MAP_DIMENSIONS.y*TILE_SIZE);
		sr.end();

		//update the ui and draw it on top of everything else
		userInterface.update(delta);
		mainUIStage.draw();
		mainUIStage.act();

	}

	public void drawLayers() {
		//go from top layer to bottom layer
		for (int i = layers.length; i > 0; i--) {

		}

		((TerrainLayer) layers[0]).generateValueMap(MAP_DIMENSIONS, xPos, yPos);

		for (int x = 0; x < MAP_DIMENSIONS.x; x++) {
			for (int y = 0; y < MAP_DIMENSIONS.y; y++) {
				for (int i = layers.length-1; i >= 0; i--) {
					switch (getLayerType(layers[i])) {
						case "Terrain":
							TextureRegion tile = getTextureForTerrainValue(layers[i], x, y);
							if (tile != null) {
								batch.draw(tile, x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
							}

							break;
						default:


					}
				}
			}
		}
	}

	public TextureRegion getTextureForTerrainValue(Layer layer, int x, int y) {
		// if (layer.upperBound == null) {

		for (int i = layer.tileChildren.length-1; i >= 0; i--) { // highest layer first
			if (((TerrainLayer)layer).valueMap[x][y] > layer.tileChildren[i].lowerBound){
				return ((TerrainLayer) layer).getTextureFromIndex(i);
			}
		}

		return null; // if there should be no tile drawn at this position for this layer
	}

	public static String getLayerType(Layer layer){
		return layer.getClass().getName().replace("com.joelallison.generation.","").replace("Layer", "");
	}

	@Override
	public void show() {
		// when the screen is shown
	}

	@Override
	public void hide() {
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}

	@Override
	public void resize(int width, int height) {
		viewport.update(width, height);
		camera.position.set(MAP_DIMENSIONS.x * TILE_SIZE / 2, MAP_DIMENSIONS.y * TILE_SIZE / 2, 0);
		viewport.getCamera().update();
	}
	
	@Override
	public void dispose () {
		((TerrainLayer) layers[0]).tileset.getSpriteSheet().dispose();
	}
}