package cern.ais.gridwars.bot;

import cern.ais.gridwars.Emulator;
import cern.ais.gridwars.api.bot.PlayerBot;

import java.io.FileNotFoundException;

public class App {
	public static void main(String[] args) throws FileNotFoundException {
//	    PlayerBot bot1 = new ExpandBot();

//		Main bot1 = new Main();
//		bot1.setBot(new FastExpandBot(bot1));

		Main bot1 = new Main();
        bot1.setBot(new SplitBot(bot1));

//        PlayerBot bot2 = new ExpandBot();

        PlayerBot bot2 = new RocketBot();

		Emulator.playMatch(bot1, bot2);
	}
}
