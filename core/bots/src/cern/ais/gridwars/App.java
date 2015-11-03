package cern.ais.gridwars;

import java.io.FileNotFoundException;

public class App {
	public static void main(String[] args) throws FileNotFoundException {
		Main bot1 = new Main();
		bot1.setBot(new FastExpandBot(bot1));
		Main bot2 = new Main();
		bot2.setBot(new AimedBot(bot2));

		new Visualizer().runGame(bot1, bot2);
		new Visualizer().runGame(new ThreeFishBot(), new RocketBot());
	}
}