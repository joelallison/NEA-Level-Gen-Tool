package com.joelallison;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.joelallison.level.FileHandling;
import com.joelallison.main.Init;

import static com.joelallison.main.GameScreen.TILE_SIZE;
import static com.joelallison.main.GameScreen.VISIBLE_WORLD_DIMENSIONS;

public class DesktopLauncher {
	public static void main (String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode((int) (VISIBLE_WORLD_DIMENSIONS.x*TILE_SIZE + TILE_SIZE*4), (int) (VISIBLE_WORLD_DIMENSIONS.y*TILE_SIZE + TILE_SIZE*4));
		config.useVsync(true);
		config.setForegroundFPS(60);


		//FileHandling.createFile("aaaaa.txt");
		FileHandling.writeToFile("aaaaa.txt", new String[]{"AWANFHIAUHKBWKFAKB!!!!", "hi", "nice to meet you"});

		config.setTitle("World Gen Tool");
		config.setWindowIcon("assets/tree.png");
		new Lwjgl3Application(new Init(), config);
	}


}
