package com.joelallison.io;

import com.badlogic.gdx.math.Vector2;
import com.joelallison.generation.Layer;
import com.joelallison.generation.MazeLayer;
import com.joelallison.generation.TerrainLayer;
import com.joelallison.graphics.Tileset;
import com.joelallison.screens.AppScreen;
import com.joelallison.generation.World;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;

import static com.joelallison.screens.userInterface.AppUI.saveProgress;

public class Database {
    static Connection connection;
    static Statement statement;
    //specific values for my server so I don't have to type them out more than once!
    public static String jdbcURL = "jdbc:postgresql://localhost:5432/levelgentool";
    public static String username = "postgres";
    public static String password = "password";

    public static void makeConnection(String jdbcURL, String username, String password) {
        try {
            connection = DriverManager.getConnection(jdbcURL, username, password);
            System.out.println("Connected to PostgreSQL server");

        } catch (SQLException e) {
            System.out.println("PostgreSQL Server Connection error");
            e.printStackTrace();
        }
    }

    public static ResultSet doSqlQuery(String sql) {
        try {
            statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(sql);
            return rs;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean doSqlStatement(String sql) {
        try {
            statement = connection.createStatement();
            int rows = statement.executeUpdate(sql);
            if (rows > 0) {
                //Statement successfully executed!
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("PostgreSQL Server Connection error");
        }

        return false;
    }

    public static PreparedStatement doPreparedStatement(String sql) {
        try {
            return connection.prepareStatement(sql);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("PostgreSQL Server Connection error");
        }

        return null;
    }

    public static void closeConnection() throws SQLException {
        connection.close();
    }

    public static boolean renameWorld(String username, World world, String newName) {
        ResultSet worldInDatabase = doSqlQuery("SELECT * FROM world WHERE username = '" + username + "' AND world_name = '" + world.name + "';"); //search with old name
        ResultSet nameTaken = doSqlQuery("SELECT * FROM world WHERE username = '" + username + "' AND world_name = '" + newName + "';");
        try {
            if (worldInDatabase.next() && (nameTaken == null) && (!newName.equals(""))) {
                //if world is found in database and name is usable, update world with new name
                return doSqlStatement(
                        "UPDATE world " +
                                "SET world_name = '" + newName + "', last_accessed_timestamp = '" + Instant.now().toString() + "' "
                                + "WHERE username = '" + username + "' AND world_name = '" + world.name + "';" //update old name
                );
            } else if ((nameTaken == null) && (!newName.equals(""))) {
                return saveWorld(username, world);
            } else {
                return false;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public static boolean saveWorld(String username, World world) {
        //these were notes to self when writing, but I've left them in:
        //update world entry
        //for each layer, update layer
        //		then get specific layer id
        //		using that, edit the layer-type specific details
        //  	THAT INCLUDES TILE SPECS

        int localLayersCount = world.layers.size();
        int layersInDatabase = 0;

        boolean done = true;


        if (checkForWorldName(username, world.name)) { //if world is found in database, update it
            doSqlStatement(
                    "UPDATE world " +
                            "SET world_name = '" + world.name + "', last_accessed_timestamp = '" + Instant.now().toString() + "', world_seed = " + world.seed +
                            "WHERE username = '" + username + "' AND world_name = '" + world.name + "';"
            );
            try {
                //get number of layers in the saved version for later comparison
                ResultSet layerCountRS = doSqlQuery("SELECT COUNT(*) FROM layer WHERE world_name = '" + world.name + "' AND username = '" + username + "'");
                if (layerCountRS.next()) {
                    layersInDatabase = layerCountRS.getInt("count");
                }
                saveProgress = "Comparing number of layers with database...";
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        } else { //world not found? make a new entry into database
            saveProgress = "World not in database, saving as new world...";
            doSqlStatement("INSERT INTO world (username, world_name, created_timestamp, last_accessed_timestamp, world_seed)" +
                    "VALUES ("
                    + "'" + username + "', "
                    + "'" + world.name + "', "
                    + "'" + world.dateCreated.toString() + "', "
                    + "'" + Instant.now().toString() + "', "
                    + world.seed +
                    ");");
        }
        if (layersInDatabase > localLayersCount) {
            doSqlStatement("DELETE * FROM layer" +
                    "WHERE layer_number > " + (localLayersCount - 1));
        }
        for (int i = 0; i < localLayersCount; i++) {
            if (i >= layersInDatabase) {
                if (saveLayer(username, i, "INSERT INTO")) {
                    saveProgress = "Saved layer " + i + "...";
                } else {
                    saveProgress = "Error saving layer " + i + "...";
                    done = false;
                }
            } else {
                if (saveLayer(username, i, "UPDATE")) {
                    saveProgress = "Saved layer " + i + "...";
                } else {
                    saveProgress = "Error saving layer " + i + "...";
                    done = false;
                }
            }
        }

        if (done) {
            saveProgress = "Done!";
            return true;
        }

        return false;
    }

    public static boolean checkForWorldName(String username, String worldName) {
        ResultSet worldInDatabase = doSqlQuery("SELECT * FROM world WHERE username = '" + username + "' AND world_name = '" + worldName + "';");
        try {
            return worldInDatabase.next();
        } catch (SQLException e){
            return true; //if there's an error, consider it to be the same as world already existing
        }
    }

    static boolean saveLayer(String username, int layerIndex, String saveType) {
        char layerType = AppScreen.getLayerTypeChar(AppScreen.world.layers.get(layerIndex));
        if (saveType.equals("INSERT INTO")) {


            int layer_id = -1;
            //I found the auto-increment for this particular case (of having what's called a two-way exlusive arc) too complex to implement within PostgreSQL,
            //so I'm generating the layer_id with code
            try {
                ResultSet maxLayerID = doSqlQuery("SELECT MAX(layer_id)+1 FROM layer WHERE layer_type = '" + layerType + "';");
                maxLayerID.next();
                //gets next biggest layer_id
                layer_id = maxLayerID.getInt("?column?");
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }

            if (layer_id != -1 && doSqlStatement(saveType + " layer (username, world_name, layer_number, layer_name, show_layer, layer_type, layer_id, inherit_seed, seed, center_x, center_y, tileset_name)" +
                    "VALUES (" +
                    "'" + username + "', "
                    + "'" + AppScreen.world.name + "', "
                    + layerIndex + ", "
                    + "'" + AppScreen.world.layers.get(layerIndex).getName() + "', "
                    + Boolean.toString(AppScreen.world.layers.get(layerIndex).layerShown()) + ", "
                    + "'" + layerType + "', "
                    + layer_id + ", "
                    + AppScreen.world.layers.get(layerIndex).isSeedInherited() + ", "
                    + Long.toString(AppScreen.world.layers.get(layerIndex).getSeed()) + ", "
                    + AppScreen.world.layers.get(layerIndex).getCenter().x + ", "
                    + AppScreen.world.layers.get(layerIndex).getCenter().y + ", "
                    + "'" + AppScreen.world.layers.get(layerIndex).tilesetName + "')")) {
                switch (layerType) {
                    case 'T':
                        if (doSqlStatement(saveType + " terrain_layer (layer_type, layer_id, scale, octaves, lacunarity, wrap, invert)" +
                                "VALUES ("
                                + "'" + layerType + "', "
                                + layer_id + ", "
                                + ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).getScale() + ", "
                                + ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).getOctaves() + ", "
                                + ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).getLacunarity() + ", "
                                + ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).getWrap() + ", "
                                + ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).isInverted() + ");")) {
                            for (Tileset.TerrainTileSpec tileSpec : ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).tileSpecs) {
                                if (!doSqlStatement(saveType + " terrain_tile_specs (layer_id, tile_name, lower_bound)" +
                                        "VALUES ("
                                        + layer_id + ", "
                                        + "'" + tileSpec.name + "', "
                                        + tileSpec.lowerBound + ")"
                                )) {
                                    saveProgress = "Unable to save tile " + tileSpec.name + " | " + tileSpec.lowerBound;
                                    return false; //if a tile isn't able to be saved to the database
                                }
                            }
                            saveProgress = "Layer " + layerIndex + " saved.";
                            return true;
                        }
                        break;
                    case 'M':
                        if (doSqlStatement(saveType + " maze_layer (layer_type, layer_id, width, height, opaque)" +
                                "VALUES ("
                                + "'" + layerType + "', "
                                + layer_id + ", "
                                + ((MazeLayer) AppScreen.world.layers.get(layerIndex)).getWidth() + ", "
                                + ((MazeLayer) AppScreen.world.layers.get(layerIndex)).getHeight() + ", "
                                + Boolean.toString(((MazeLayer) AppScreen.world.layers.get(layerIndex)).isOpaque()) + ");")) {
                            for (Tileset.MazeTileSpec tileSpec : ((MazeLayer) AppScreen.world.layers.get(layerIndex)).tileSpecs) {
                                if (!doSqlStatement(saveType + " maze_tile_specs (layer_id, tile_name, neighbour_map)" +
                                        "VALUES ("
                                        + layer_id + ", "
                                        + "'" + tileSpec.name + "', "
                                        + "'" + tileSpec.neighbourMapToString() + "')"
                                )) {
                                    saveProgress = "Unable to save tile " + tileSpec.name;
                                    return false; //if a tile isn't able to be saved to the database
                                }
                            }
                            saveProgress = "Layer " + layerIndex + " saved.";
                            return true;
                        }
                        break;
                }
            }

            return true;
        } else if (saveType.equals("UPDATE")) {
            //UPDATE layer
            //SET x = y, etc.
            //WHERE layer_number = i, world_name, username

            //UPDATE layer_type
            //set x = y, etc.
            //WHERE layer_id = from layer

            //UPDATE layer_type_tile_spec
            //SET
            //WHERE layer_id

            if (doSqlStatement(saveType + " layer SET "
                    + "username = '" + username + "', "
                    + "world_name = '" + AppScreen.world.name + "', "
                    + "layer_number = " + layerIndex + ", "
                    + "layer_name = '" + AppScreen.world.layers.get(layerIndex).getName() + "', "
                    + "show_layer = " + Boolean.toString(AppScreen.world.layers.get(layerIndex).layerShown()) + ", "
                    + "layer_type = '" + layerType + "', "
                    + "layer_id = " + AppScreen.world.layers.get(layerIndex).getLayerID() + ", "
                    + "inherit_seed = " + AppScreen.world.layers.get(layerIndex).isSeedInherited() + ", "
                    + "seed = " + Long.toString(AppScreen.world.layers.get(layerIndex).getSeed()) + ", "
                    + "center_x = " + AppScreen.world.layers.get(layerIndex).getCenter().x + ", "
                    + "center_y = " + AppScreen.world.layers.get(layerIndex).getCenter().y + ", "
                    + "tileset_name = '" + AppScreen.world.layers.get(layerIndex).tilesetName + "' "
                    + "WHERE layer_type = '" + layerType + "' AND layer_id = " + AppScreen.world.layers.get(layerIndex).getLayerID() + ";"

            )) {
                switch (layerType) {
                    case 'T':
                        if (doSqlStatement(saveType + " terrain_layer SET "
                                + "layer_type = '" + layerType + "', "
                                + "layer_id = " + AppScreen.world.layers.get(layerIndex).getLayerID() + ", "
                                + "scale = " + ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).getScale() + ", "
                                + "octaves = " + ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).getOctaves() + ", "
                                + "lacunarity = " + ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).getLacunarity() + ", "
                                + "wrap = " + ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).getWrap() + ", "
                                + "invert = " + ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).isInverted()
                                + " WHERE layer_id = " + AppScreen.world.layers.get(layerIndex).getLayerID() + ";")) {
                            for (Tileset.TerrainTileSpec tileSpec : ((TerrainLayer) AppScreen.world.layers.get(layerIndex)).tileSpecs) {
                                if (!doSqlStatement(saveType + " terrain_tile_specs SET "
                                        + "layer_id = " + AppScreen.world.layers.get(layerIndex).getLayerID() + ", "
                                        + "tile_name = '" + tileSpec.name + "', "
                                        + "lower_bound = " + tileSpec.lowerBound
                                        + " WHERE layer_id = " + AppScreen.world.layers.get(layerIndex).getLayerID() + " AND tile_name = '" + tileSpec.name + "';"
                                )) {
                                    saveProgress = "Unable to save tile " + tileSpec.name + " | " + tileSpec.lowerBound;
                                    return false; //if a tile isn't able to be saved to the database
                                }
                            }
                            saveProgress = "Layer " + layerIndex + " saved.";
                            return true;
                        }
                        break;
                    case 'M':
                        if (doSqlStatement(saveType + " maze_layer SET "
                                + "layer_type = '" + layerType + "', "
                                + "layer_id = " + AppScreen.world.layers.get(layerIndex).getLayerID() + ", "
                                + "width = " + ((MazeLayer) AppScreen.world.layers.get(layerIndex)).getWidth() + ", "
                                + "height = " + ((MazeLayer) AppScreen.world.layers.get(layerIndex)).getHeight()
                                + " WHERE layer_id = " + AppScreen.world.layers.get(layerIndex).getLayerID() + ";")) {
                            for (Tileset.MazeTileSpec tileSpec : ((MazeLayer) AppScreen.world.layers.get(layerIndex)).tileSpecs) {
                                if (!doSqlStatement(saveType + " maze_tile_specs SET "
                                        + "layer_id = " + AppScreen.world.layers.get(layerIndex).getLayerID() + ", "
                                        + "tile_name = '" + tileSpec.name + "', "
                                        + "neighbour_map = '" + tileSpec.neighbourMapToString() + "'"
                                        + " WHERE layer_id = " + AppScreen.world.layers.get(layerIndex).getLayerID() + " AND tile_name = '" + tileSpec.name + "';"
                                )) {
                                    saveProgress = "Unable to save tile " + tileSpec.name;
                                    return false; //if a tile isn't able to be saved to the database
                                }
                            }
                            saveProgress = "Layer " + layerIndex + " saved.";
                            return true;
                        }
                        break;
                }


                return true;
            }
        }
        return false;
    }

    public static World getWorld(String worldName, String username, Long seed, Instant dateCreated) {
        ArrayList<Layer> layers = new ArrayList<>();

        ResultSet worldLayers = doSqlQuery(
                "SELECT * FROM layer " +
                        "WHERE \"username\" = '" + username + "' " +
                        "AND \"world_name\" = '" + worldName + "'" +
                        "ORDER BY layer_number ASC;"
        );

        try {
            while(worldLayers.next()) {
                //loading all layers found from the query into the arraylist
                switch(worldLayers.getString("layer_type").charAt(0)) {
                    //need to be processed differently based on type (different constructors etc.)
                    //I could have the different types of layer-loading as methods, but I don't need to get individual layers from the database in any other place so it's pointless
                    case 'T':
                        ResultSet terrainLayerRS = doSqlQuery(
                                "SELECT * FROM terrain_layer " +
                                        "WHERE \"layer_id\" = " + worldLayers.getInt("layer_id")
                        );

                        //as worldLayers.next() == true, and that layer is marked 'T', a terrain layer will be returned from the query, terrainLayerRS shouldn't be null
                        terrainLayerRS.next();

                        TerrainLayer terrainLayer = new TerrainLayer(
                                worldLayers.getInt("layer_id"),
                                worldLayers.getString("layer_name"),
                                worldLayers.getLong("seed"),
                                terrainLayerRS.getFloat("scale"),
                                terrainLayerRS.getInt("octaves"),
                                terrainLayerRS.getFloat("lacunarity"),
                                terrainLayerRS.getInt("wrap"),
                                terrainLayerRS.getBoolean("invert")
                        );

                        terrainLayer.tilesetName = worldLayers.getString("tileset_name");
                        terrainLayer.setInheritSeed(worldLayers.getBoolean("inherit_seed"));
                        terrainLayer.setCenter(new Vector2(worldLayers.getInt("center_x"), worldLayers.getInt("center_y")));

                        ResultSet terrainTileSpecsRS = doSqlQuery(
                                "SELECT * FROM terrain_tile_specs " +
                                        "WHERE \"layer_id\" = " + worldLayers.getInt("layer_id")
                        );

                        while(terrainTileSpecsRS.next()) {
                            terrainLayer.tileSpecs.add(new Tileset.TerrainTileSpec(terrainTileSpecsRS.getString("tile_name"), terrainTileSpecsRS.getFloat("lower_bound")));
                        }

                        layers.add(terrainLayer);
                        break;
                    case 'M':
                        ResultSet mazeLayerRS = doSqlQuery(
                                "SELECT * FROM maze_layer " +
                                        "WHERE \"layer_id\" = " + worldLayers.getInt("layer_id")
                        );

                        //as worldLayers.next() == true, and that layer is marked 'M', a maze layer will be returned from the query, mazeLayerRS shouldn't be null
                        mazeLayerRS.next();

                        MazeLayer mazeLayer
                                = new MazeLayer(
                                mazeLayerRS.getInt("layer_id"),
                                worldLayers.getString("layer_name"),
                                worldLayers.getLong("seed"),
                                mazeLayerRS.getInt("width"),
                                mazeLayerRS.getInt("height"),
                                worldLayers.getString("tileset_name"),
                                mazeLayerRS.getBoolean("opaque")
                        );



                        mazeLayer.setInheritSeed(worldLayers.getBoolean("inherit_seed"));
                        mazeLayer.setCenter(new Vector2(worldLayers.getInt("center_x"), worldLayers.getInt("center_y")));



                        ResultSet mazeTileSpecsRS = doSqlQuery(
                                "SELECT * FROM maze_tile_specs " +
                                        "WHERE \"layer_id\" = " + worldLayers.getInt("layer_id")
                        );

                        while(mazeTileSpecsRS.next()) {
                            mazeLayer.tileSpecs.add(new Tileset.MazeTileSpec(mazeTileSpecsRS.getString("tile_name"), Tileset.MazeTileSpec.neighbourMapParseString(mazeTileSpecsRS.getString("neighbour_map"))));
                        }

                        layers.add(mazeLayer);
                        break;
                }
            }
        } catch (SQLException e) {
            System.out.println(e);
        }

        return new World(worldName, layers, seed, dateCreated);
    }
}