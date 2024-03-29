package com.joelallison.screens.userInterface;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.joelallison.generation.World;
import com.joelallison.io.Database;
import com.joelallison.screens.LoginScreen;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.joelallison.io.Database.checkForWorldName;
import static com.joelallison.screens.WorldSelectScreen.loadWorldIntoApp;
import static com.joelallison.screens.WorldSelectScreen.username;
import static com.joelallison.screens.userInterface.AppUI.stage;

public class WorldUI extends UI {
    //this https://stackoverflow.com/a/17999317 was incredibly useful in getting the scrollpane to work
    Table worlds = new Table();
    private static final String DATE_FORMAT = "dd/MM/yyyy - HH:mm";
    TextButton newWorldButton = new TextButton("New World", skin);
    TextButton backButton = new TextButton("Back", skin);


    public void genUI(final Stage stage) { //stage is made final here so that it can be accessed within inner classes
        genWorlds(stage);
        ScrollPane selectionScroll = new ScrollPane(worlds);
        selectionScroll.setSize(Gdx.graphics.getWidth() * 0.3f, Gdx.graphics.getHeight());
        selectionScroll.setPosition(Gdx.graphics.getWidth() / 2 - selectionScroll.getWidth() / 2, Gdx.graphics.getHeight() / 2 - selectionScroll.getHeight() / 2);
        selectionScroll.setScrollbarsVisible(true);
        selectionScroll.setScrollingDisabled(false, true);

        stage.addActor(selectionScroll);
    }

    public void genWorlds(final Stage stage) {
        worlds.defaults().space(8);
        loadWorldsIn(stage);
        worlds.row();

        newWorldButton.addListener(
                new InputListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        //for this popup, I decided to see if it was worth making a custom table instead of the default Dialog one
                        //While this is definitely a better looking popup than others, the default system still works quite well
                        final Dialog popupBox = new Dialog("New world", skin);

                        Table table = new Table();
                        //table.setDebug(true);
                        table.setFillParent(true);
                        table.defaults().align(Align.left).space(8);
                        table.pad(16);

                        final Label nameLabel = new Label("Name:", skin);
                        table.add(nameLabel).colspan(100); //colspan is to allow the 'go' and 'cancel' buttons to be a lot closer together
                        final TextField nameField = new TextField("", skin);
                        table.add(nameField);

                        table.row();
                        final Label seedLabel = new Label("Seed: (leave blank for random) ", skin);
                        table.add(seedLabel).colspan(100); //colspan is to allow the 'go' and 'cancel' buttons to be a lot closer together
                        final TextField seedField = new TextField("", skin);
                        table.add(seedField);

                        table.row();
                        TextButton goButton = new TextButton("Go", skin);
                        goButton.addListener(new InputListener() {
                            @Override
                            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                                if (!nameField.getText().equals("")) {
                                    if (!checkForWorldName(username, nameField.getText())) {
                                        if (seedField.getText().matches("[0-9]*")) {
                                            if (seedField.getText().equals("")) {
                                                loadWorldIntoApp(new World(nameField.getText()), username);
                                            } else {
                                                loadWorldIntoApp(new World(nameField.getText(), Long.parseLong(seedField.getText())), username);
                                            }
                                        } else {
                                            seedLabel.setText("Seed: (leave blank for random)\nMust be a positive int.");
                                        }
                                    } else {
                                        nameLabel.setText("Name: (must be unique)");
                                    }
                                } else {
                                    nameLabel.setText("Name: (must not be left blank!)");
                                }
                                return true;
                            }
                        });

                        table.add(goButton);
                        TextButton cancelButton = new TextButton("Cancel", skin);
                        cancelButton.addListener(new InputListener() {
                            @Override
                            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                                popupBox.hide();
                                return true;
                            }
                        });
                        table.add(cancelButton);

                        popupBox.add(table);

                        popupBox.show(stage);
                        return true;
                    }
                });

        worlds.add(newWorldButton);
        worlds.row();
        backButton.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                Dialog backDialog = new Dialog("Go back", skin) {
                    public void result(Object obj) {
                        if (obj.equals(true)) {
                            ((Game) Gdx.app.getApplicationListener()).setScreen(new LoginScreen());
                        }
                    }
                };

                backDialog.text("This will log you out.");
                backDialog.button("Continue", true);
                backDialog.button("Cancel", false);
                backDialog.show(stage);

                return true;
            }
        });
        worlds.add(backButton);

        worlds.setSize(worlds.getPrefWidth(), worlds.getPrefHeight());
        //worlds.setDebug(true);
    }

    public void loadWorldsIn(final Stage stage) {
        //getting the metadata of the worlds so that the user can have more information about what they're choosing before they choose it.
        //(while this does use SQL, it made more sense to me to keep this method in this class, as it handles UI)

        try {
            ResultSet getWorldsResults = Database.doSqlQuery (
                    "SELECT * FROM world " +
                            "WHERE \"username\" = '" + username + "' " +
                            "ORDER BY last_accessed_timestamp DESC;"
            );


            if (getWorldsResults.next()) {
                try {
                    do {
                        final String name = getWorldsResults.getString("world_name");
                        Instant dateCreated = getWorldsResults.getTimestamp("created_timestamp").toInstant();
                        Instant lastAccessed = getWorldsResults.getTimestamp("last_accessed_timestamp").toInstant();
                        Long seed = getWorldsResults.getLong("world_seed");

                        //get number of layers which are part of this world
                        ResultSet layerCountRS = Database.doSqlQuery("SELECT COUNT(*) FROM layer WHERE world_name = '" + name + "' AND username = '" + username + "'");

                        int layerCount = 0;
                        if (layerCountRS.next()) {
                            layerCount = layerCountRS.getInt("count");
                        }

                        worlds.row();
                        worlds.add(selectWorldButton(name, dateCreated, lastAccessed, layerCount, seed));
                        TextButton deleteWorldButton = new TextButton("Delete", skin);
                        deleteWorldButton.addListener(new InputListener() {
                            @Override
                            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                                Dialog deleteDialog = new Dialog("Delete world: " + name, skin) {
                                    public void result(Object obj) {
                                        if (obj.equals(true)) {
                                            deleteWorld(name, username);
                                        }
                                    }
                                };

                                deleteDialog.text("Are you sure you want to delete '" + name + "'?");
                                deleteDialog.button("Yes", true);
                                deleteDialog.button("No", false);
                                deleteDialog.show(stage);

                                return true;
                            }
                        });

                        worlds.add(deleteWorldButton);

                    } while (getWorldsResults.next());
                } catch (SQLException e) {
                    basicPopupMessage("Error!", e.getMessage(), stage);
                }
            }
        } catch (Exception e) {
            basicPopupMessage("Error!", e.getMessage(), stage);
        }
    }

    void deleteWorld(String worldName, String username) {
        //because layers, tileSpecs etc. cascade all that's needed is to
        Database.doSqlStatement("DELETE FROM world WHERE "
                + "world_name = '" + worldName + "' "
                + "AND username = '" + username + "';"
        );

        worlds.clear();
        genWorlds(stage);
    }

    public TextButton selectWorldButton(final String name, final Instant dateCreated, final Instant lastAccessed, int layerCount, final Long seed) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT).withZone(ZoneId.systemDefault());

        TextButton button = new TextButton("World name: " + name +
                "\nDate created: " + formatter.format(dateCreated) +
                "\nLast accessed: " + formatter.format(lastAccessed) +
                "\nNumber of layers: " + Integer.toString(layerCount) +
                "\nSeed: " + Long.toString(seed), skin);

        button.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                //even though I could just pass name and username through, and then query to find the other details again, I think it's better to keep passing the values through
                loadWorldIntoApp(Database.getWorld(name, username, seed, dateCreated), username);
                return true;
            }
        });

        return button;
    }

}

