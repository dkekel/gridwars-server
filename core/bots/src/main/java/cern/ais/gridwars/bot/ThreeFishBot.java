package cern.ais.gridwars.bot;

import cern.ais.gridwars.api.Coordinates;
import cern.ais.gridwars.api.UniverseView;
import cern.ais.gridwars.api.bot.PlayerBot;
import cern.ais.gridwars.api.command.MovementCommand;

import java.util.List;


public class ThreeFishBot implements PlayerBot {

	Coordinates start;
	int i=18;

	public void getNextCommands(UniverseView universeView, List<MovementCommand> commandList) {
		if  (i == 18) {
			start = universeView.getMyCells().get(0);
			commandList.add(new MovementCommand(start, MovementCommand.Direction.LEFT, 45));
			commandList.add(new MovementCommand(start, MovementCommand.Direction.RIGHT, 45));
			i--;
		} else if (i>0) {
			for (Coordinates current : universeView.getMyCells()) {
				int dX=current.getX()-start.getX(),dY=current.getY()-start.getY();
				if (dX==0) {
					if (universeView.getPopulation(current) > 5 ) {
						commandList.add(new MovementCommand(current, MovementCommand.Direction.UP, universeView.getPopulation(current)-5));
					}
				} else if (dY==0) {
					if ((dX>0)^(Math.abs(dX)<25)) {
						if (universeView.belongsToMe(current.getLeft())) {
							if (universeView.getPopulation(current) > 5 ) {
								commandList.add(new MovementCommand(current, MovementCommand.Direction.RIGHT, universeView.getPopulation(current)-5));
							}
						} else if (universeView.getPopulation(current) > 44) {
							commandList.add(new MovementCommand(current, MovementCommand.Direction.LEFT, 45));
						} else {
							commandList.add(new MovementCommand(current, MovementCommand.Direction.LEFT, universeView.getPopulation(current)));
						}
					} else {
						if (universeView.belongsToMe(current.getRight())) {
							if (universeView.getPopulation(current) > 5 ) {
								commandList.add(new MovementCommand(current, MovementCommand.Direction.LEFT, universeView.getPopulation(current)-5));
							}
						} else if (universeView.getPopulation(current) > 44) {
							commandList.add(new MovementCommand(current, MovementCommand.Direction.RIGHT, 45));
						} else {
							commandList.add(new MovementCommand(current, MovementCommand.Direction.RIGHT, universeView.getPopulation(current)));
						}
					}
				}
			}
			i--;
		} else {
			for (Coordinates current : universeView.getMyCells()) {
				if (universeView.getPopulation(current) > 15) {
					commandList.add(new MovementCommand(current, MovementCommand.Direction.LEFT, 5));
					commandList.add(new MovementCommand(current, MovementCommand.Direction.RIGHT, 5));
					commandList.add(new MovementCommand(current, MovementCommand.Direction.UP, universeView.getPopulation(current)-15));
				} else {
					commandList.add(new MovementCommand(current, MovementCommand.Direction.UP, universeView.getPopulation(current)));
				}
			}
		}
	}
}
