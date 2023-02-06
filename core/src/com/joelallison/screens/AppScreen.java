package com.joelallison.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.joelallison.graphics.Tileset;
import com.joelallison.generation.Layer;
import com.joelallison.user.Creation;
import com.joelallison.user.UserInput;
import com.joelallison.generation.TerrainLayer;
import com.joelallison.screens.UserInterface.AppInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.joelallison.io.JsonHandling.tilesetsJsonToMap;

public class AppScreen implements Screen {
	Stage mainUIStage;

	//viewport displays the generated tiles
	public ExtendViewport viewport;
	public static OrthographicCamera camera;
	SpriteBatch batch;
	ShapeRenderer sr; //for misc UI additions
	public static Creation creation;
	public static final int TILE_SIZE = 32;
	public static final int CHUNK_SIZE = 7;
	public static final Vector2 LEVEL_ASPECT_RATIO = new Vector2(4, 3);
	public static final int LEVEL_ASPECT_SCALAR = 2;
	public static final Vector2 MAP_DIMENSIONS = new Vector2((int) CHUNK_SIZE * LEVEL_ASPECT_SCALAR * LEVEL_ASPECT_RATIO.x, (int) CHUNK_SIZE* LEVEL_ASPECT_SCALAR * LEVEL_ASPECT_RATIO.y);
	int xPos, yPos;
	public static UserInput userInput;
	float stateTime;

	private ShaderProgram shaderProgram;
	public static HashMap<String, Tileset> tilesets;
	AppInterface userInterface = new AppInterface();
	public AppScreen() {
		camera = new OrthographicCamera(1920, 1080);
		viewport = new ExtendViewport(1920, 1080, camera);

		batch = new SpriteBatch();
		sr = new ShapeRenderer();

		camera.zoom = 1.2f; //default viewport size
		viewport.apply(true);

		// declare default coords
		xPos = 0;
		yPos = 0;

		//declare player stuff
		userInput = new UserInput(0, 0);

		tilesets = tilesetsJsonToMap("core/src/com/joelallison/graphics/tilesets.json");
		for (Map.Entry<String, Tileset> set : tilesets.entrySet()) {
			set.getValue().initTileset();
		}

		creation = new Creation("Creation");

		String vertexShader = Gdx.files.internal("core/src/com/joelallison/graphics/shaders/vertex.glsl").readString();
		String hueshiftShader = Gdx.files.internal("core/src/com/joelallison/graphics/shaders/hueshift.glsl").readString();
		//shaderProgram = new ShaderProgram(vertexShader, hueshiftShader);
		//shaderProgram.pedantic = false;

		//batch.setShader(shaderProgram);

		stateTime = 0f;

		mainUIStage = userInterface.genStage(mainUIStage);
		userInterface.genUI(mainUIStage);
	}

	@Override
	public void render(float delta) {
		stateTime += Gdx.graphics.getDeltaTime();
		viewport.apply();
		camera.zoom = MathUtils.clamp(camera.zoom, 0.2f, 4f); //set limits for the size of the viewport
		batch.setProjectionMatrix(viewport.getCamera().combined);
		sr.setProjectionMatrix(viewport.getCamera().combined);
		camera.update();

		userInput.handleInput();
		xPos = userInput.getxPosition();
		yPos = userInput.getyPosition();

		ScreenUtils.clear(creation.layers.get(0).tileset.getColor());

		batch.begin();
		drawLayers();
		batch.end();

		drawUIShapes();

		//update the ui and draw it on top of everything else
		creation.makeLayerNamesUnique();
		userInterface.update(delta);
		mainUIStage.draw();
		mainUIStage.act();
	}

	public void drawUIShapes() {
		sr.begin(ShapeRenderer.ShapeType.Line);

		//draw border around the generated outcome
		sr.setColor(Color.WHITE);
		sr.rect(0f, 0f, MAP_DIMENSIONS.x*TILE_SIZE, MAP_DIMENSIONS.y*TILE_SIZE);

		sr.end();
	}

	public void drawLayers() {
		for (int i = 0; i < creation.layers.size(); i++) {
			((TerrainLayer) creation.layers.get(i)).generateValueMap(MAP_DIMENSIONS, xPos, yPos);
		}

		boolean[][] tileAbove = new boolean[(int) MAP_DIMENSIONS.x][(int) MAP_DIMENSIONS.y];

		for (int x = 0; x < MAP_DIMENSIONS.x; x++) {
			for (int y = 0; y < MAP_DIMENSIONS.y; y++) {
				// top layer to bottom layer
				for (int i = creation.layers.size() - 1; i >= 0; i--) {
					if(creation.layers.get(i).layerShown()){
						if (tileAbove[x][y] == false) { // no need to draw if there's already a tile above
							switch (getLayerType(creation.layers.get(i))) {
								case "Terrain":
									TextureRegion tile = getTextureForTerrainValue(creation.layers.get(i), x, y);
									if (tile != null) {
										//shaderProgram.setUniformf("hue", 0.4f);
										//shaderProgram.setUniformf("u_time", stateTime);
										//shaderProgram.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
										batch.draw(tile, x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
										tileAbove[x][y] = true;
									}
									break;
								default:

							}
						}
					}
				}
			}
		}
	}

	public TextureRegion getTextureForTerrainValue(Layer layer, int x, int y) {
		for (int i = layer.tileChildren.length-1; i >= 0; i--) { // highest layer first
			if (((TerrainLayer)layer).valueMap[x][y] > layer.tileChildren[i].lowerBound){
				return ((TerrainLayer) layer).getTextureFromIndex(i);
			}
		}

		return null; // if there should be no tile drawn at this position for this layer
	}

	public static String getLayerType(Layer layer) {
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
		for (Layer layer: creation.layers) {
			((TerrainLayer) layer).tileset.getSpriteSheet().dispose();
		}
	}
}