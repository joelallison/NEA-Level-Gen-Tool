package com.joelallison.screens.UserInterface;

import com.badlogic.gdx.Gdx;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;


public class MenuInterface extends UserInterface {

    Label usernameLabel = new Label("Username", chosenSkin);
    TextField usernameField = new TextField("", chosenSkin);
    Label passwordLabel = new Label("Password", chosenSkin);
    TextField passwordField = new TextField("", chosenSkin);
    TextButton loginButton = new TextButton("Login", chosenSkin);
    Table loginTable = new Table();

    public void genUI(Stage stage) {
        loginTable.setDebug(true);
        loginTable.add(usernameLabel);
        loginTable.add(usernameField);
        loginTable.row();
        loginTable.add(passwordLabel);
        loginTable.add(passwordField);
        loginTable.add(loginButton);

        loginTable.pad(16);
        loginTable.setPosition((float) Gdx.graphics.getWidth() / 2, (float) Gdx.graphics.getHeight() / 2);

        stage.addActor(loginTable);
    }
}
