package gridwars.starter;

import cern.ais.gridwars.Emulator;

import java.io.FileNotFoundException;


/**
 * Instantiates the example bots and starts the game emulator.
 */
public class EmulatorRunner {

    public static void main(String[] args) throws FileNotFoundException {
        MovingBot blueBot = new MovingBot();
        ExpandBot redBot = new ExpandBot();

        Emulator.playMatch(blueBot, redBot);
    }
}
