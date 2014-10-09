/*
 * File: GrandFinaleFun.java
 * Creator: David Richardson
 * Created: 25th November 2013, 22:11.
 */

import uk.ac.warwick.dcs.maze.logic.IRobot;
import uk.ac.warwick.dcs.maze.logic.IEvent;
import uk.ac.warwick.dcs.maze.logic.EventBus;
import uk.ac.warwick.dcs.maze.logic.Event;
import uk.ac.warwick.dcs.maze.logic.Maze;

import java.lang.reflect.Field;

import java.util.Random;

import java.awt.Point;
import java.awt.Color;
import java.awt.Font;

import java.awt.event.FocusListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;
import javax.swing.JTextPane;

public class GrandFinaleFun {

	// Boolean 2D array for storing the state of the falling blocks.
	private boolean[][] mBlockGrid;

	// IOFrame to represent the IO display used by the robot.
	private IOFrame mRobotIo;

	// The most recent KeyEvent to be detected by the IOFrame.
	private KeyEvent mCurrentKeyEvent;

	// Boolean variables for various states of the game.
	private boolean mBonusIsAvailable;
	private boolean mBonusIsActive;
	private boolean mHasLostGame;
	private boolean mIsNewHighScore;

	// Random number generator for the generation of the next row of falling blocks.
	private Random mRandomGenerator;

	// Move counter of the robot.
	private long mMoveCounter;

	// Various integer variables for different states of the game.
	private int mScore;
	private int mScoreSinceBooster;
	private int mBonusPosition;
	private int mGameDifficulty;
	private int mNextPointGoal;
	private int mBlockMovementSpeed;
	private int mHighScore;

	/**
	 * Constructor for when the robot is first initialised within the maze environment.
	 */
	public GrandFinaleFun() {
		// Set-up the robot IO.
		if(mRobotIo == null) {
			mRobotIo = new IOFrame();
		}

		// Initialise the object's variables.
		mBonusIsAvailable = false;
		mBonusIsActive = false;
		mHasLostGame = false;
		mIsNewHighScore = false;

		mScore = 0;
		mScoreSinceBooster = 0;
		mMoveCounter = 0;
		mGameDifficulty = 0;
		mNextPointGoal = 15;
		mBlockMovementSpeed = 8;
		mHighScore = 0;

		mRandomGenerator = new Random();
	}

	/**
	 * Method called each time the robot is to move within the maze.
	 * @param robot - The robot that is to move within the maze.
	 */
	public void controlRobot(IRobot robot) {
		// Clear the maze.
		Maze maze = setupMaze(robot);

		// If we are on the first move of this game instance...
		if(maze != null && mMoveCounter == 0) {
			// Reset the maze so the clear becomes apparent.
			broadcastMazeChange(robot, maze);

			// And initialise a few more variables.
			mBlockGrid = new boolean[maze.getWidth() - 2][maze.getHeight() - 1];
			mBonusPosition = mBlockGrid[0].length;
		}

		// If the robot has lost the game...
		if(mHasLostGame) {
			// Display on the IO panel a game over screen.
			mRobotIo.gameOver();

			// And broadcast a reset.
			EventBus.broadcast(new Event(IEvent.ROBOT_RESET_REQUEST, null));
		// Otherwise...
		} else {
			// Handle the direction of the robot relative to the last key press.
			handleRobotDirection(robot);

			// Translate the falling blocks if suitable.
			if(mMoveCounter % mBlockMovementSpeed == 0) {
				transformBlockGrid();
			}

			// If the bonus wave is active, animate it.
			if(mBonusIsActive) {
				animateBlockWipe(robot, maze);
			}

			// Then apply the block grid to the maze.
			maze = applyBlockGrid(maze);
			// Following by removing any blocks in the robots way.
			maze = removeBlocks(robot, maze);

			// Then broadcast the changes to the maze.
			broadcastMazeChange(robot, maze);

			// If the robot will remain within the maze boundaries...
			if(isRobotWithinBoundaries(robot, maze)) {
				// Move it 1 step forward.
				robot.advance();
			}

			// Then if the robot has reached or surpassed its point goal...
			if(mScore >= mNextPointGoal) {
				// And if the game difficulty is within reasonable bounds...
				if(mGameDifficulty < 5) {
					// Increase the difficulty.
					mGameDifficulty++;

					// And increase the block movement speed.
					mBlockMovementSpeed--;
				}

				// Then set the next point goal.
				if(mNextPointGoal < 240) {
					mNextPointGoal *= 2;
				} else {
					mNextPointGoal += 60;
				}

				// And give the player a grid wipe bonus.
				mBonusIsAvailable = true;
			}

			// Increment the move counter.
			mMoveCounter++;

			// Then determine if a high score has been set.
			boolean isHighScore = (mScore > mHighScore);
			if(isHighScore) {mHighScore = mScore; mIsNewHighScore = true;}

			// Update the score display.
			mRobotIo.updateScore(mScore, mGameDifficulty, mBonusIsAvailable, mIsNewHighScore);

			// And finally determine if the robot will lose on the next turn or not.
			canContinueGame();
		}
	}

	/**
	 * Method called upon reset of the robot maze.
	 */
	public void reset() {
		// Reset any variables for a new game.
		mBonusIsAvailable = false;
		mBonusIsActive = false;
		mHasLostGame = false;
		mIsNewHighScore = false;

		mScore = 0;
		mScoreSinceBooster = 0;
		mMoveCounter = 0;
		mGameDifficulty = 0;
		mNextPointGoal = 15;
		mBlockMovementSpeed = 8;
	}

	/**
	 * Method that will animate the block wipe wave.
	 * @param robot - The robot that is used to detect whether it is in the middle of the wave.
	 * @param maze - The Maze object to manipulate and query.
	 */
	private void animateBlockWipe(IRobot robot, Maze maze) {
		// If the bonus wave is on the grid...
		if(mBonusPosition >= 0) {
			// For each y coordinate in the grid...
			for(int j = mBlockGrid[0].length - 1; j >= 0; j--) {
				// If we are at the position for the bonus wave...
				if(j == mBonusPosition) {
					// Iterate through each of the x coordinates...
					for(int i = 0; i < mBlockGrid.length; i++) {
						// And display the wave in the grid.
						mBlockGrid[i][j] = true;

						// Then count all of the items above the wave...
						if(j > 0) {
							// And adjust the score if they're a wall.
							if(mBlockGrid[i][j - 1]) {
								mScore++;
								mScoreSinceBooster++;
							}
						}
					}

					// If the wave is on the same level as the robot...
					if(j >= robot.getLocationY() - 2 && j <= robot.getLocationY() + 2) {
						// Figure out the begin and end position of the hole in the wave.
						int begin = robot.getLocationX() - 2;
						int end = robot.getLocationX() + 2;

						// Then make sure it remains in the bounds of the maze.
						if(begin < 1) {begin = 1;}
						if(end > (maze.getWidth() - 2)) {end = (maze.getWidth() - 2);}

						// And then display the hole in the maze.
						for(int k = begin; k <= end; k++) {
							mBlockGrid[k - 1][j] = false;
						}
					}
				// Otherwise if the y coordinate is behind the bonus position...
				} else if(j > mBonusPosition) {
					// Clear the blocks.
					for(int i = 0; i < mBlockGrid.length; i++) {
						mBlockGrid[i][j] = false;
					}
				}
			}

			// Then decrement the position of the bonus wave.
			mBonusPosition--;
		// Otherwise...
		} else {
			// Set the bonus to be inactive and reset the bonus wave position.
			mBonusIsActive = false;
			mBonusPosition = mBlockGrid[0].length - 1;

			// Then reset the block grid so it is entirely clear.
			for(int i = 0; i < mBlockGrid.length; i++) {
				for(int j = 0; j < mBlockGrid[0].length; j++) {
					mBlockGrid[i][j] = false;
				}
			}
		}
	}

	/**
	 * Method that determines whether or not the robot has lost the game or not.
	 */
	private void canContinueGame() {
		// Run through each of the squares on the bottom row of the block grid...
		for(int i = 0; i < mBlockGrid.length; i++) {
			// If there is a block there and the grid wipe inactive, the robot has lost the game.
			if(mBlockGrid[i][mBlockGrid[0].length - 1] && !mBonusIsActive) {
				mHasLostGame = true;
			}
		}
	}

	/**
	 * Method that will translate the block grid down 1, moving all available blocks.
	 */
	private void transformBlockGrid() {
		// Create a temporary grid of the same size as the current block grid.
		boolean[][] tempGrid = new boolean[mBlockGrid.length][mBlockGrid[0].length];

		// The for each item in the grid...
		for(int i = 0; i < mBlockGrid.length; i++) {
			for(int j = 0; j < mBlockGrid[0].length - 1; j++) {
				// Translate the grid down 1 and store it in our temporary grid.
				tempGrid[i][j + 1] = mBlockGrid[i][j];
			}
		}

		// Then assign the true block grid to the temporary one.
		mBlockGrid = tempGrid;

		// And finally add some blocks to the top of the grid.
		addBlocksToGrid();
	}

	/**
	 * Method that will add some blocks to the top of the grid.
	 */
	private void addBlocksToGrid() {
		// For each item in the first row of the block grid...
		for(int i = 0; i < mBlockGrid.length; i++) {
			// Add a new block to it with a 1/32 chance.
			if(mRandomGenerator.nextInt(32) == 16) {
				mBlockGrid[i][0] = true;
			}
		}
	}

	/**
	 * Method that will apply the block grid array to the maze so it is visible.
	 * @param maze - The Maze object to be modified.
	 * @return - The modified Maze object.
	 */
	private Maze applyBlockGrid(Maze maze) {
		// For each element in the block grid array...
		for(int i = 0; i < mBlockGrid.length; i++) {
			for(int j = 0; j < mBlockGrid[0].length; j++) {
				// Set the corresponding cell in the maze correctly.
				maze.setCellType(i + 1, j, (mBlockGrid[i][j] ? Maze.WALL : Maze.PASSAGE));
			}
		}

		// Then return the modified maze.
		return maze;
	}

	/**
	 * Method to handle the robot direction with key presses.
	 * @param robot - The robot that will have its direction modified by a key press.
	 */
	private void handleRobotDirection(IRobot robot) {
		// Get the current heading of the robot for a default case.
		int heading = robot.getHeading();

		// Then test the current key event.
		switch(mCurrentKeyEvent.getKeyChar()) {
			// Set the heading dependant on the key press.
			case 'w':
				heading = IRobot.NORTH;

				break;
			case 's':
				heading = IRobot.SOUTH;

				break;
			case 'a':
				heading = IRobot.WEST;

				break;
			case 'd':
				heading = IRobot.EAST;

				break;
		}

		// Then set the heading to the newly decided one.
		robot.setHeading(heading);
	}

	/**
	 * Method that will remove the block ahead of the robot.
	 * @param robot - The robot that will be used to check ahead and remove any walls.
	 * @param maze - The Maze object that will be modified.
	 * @return - The modified Maze object.
	 */
	private Maze removeBlocks(IRobot robot, Maze maze) {
		// Get the next coordinates of the robot.
		Point nextCoords = findNextCoordinates(robot);

		// If the coordinates for removal are in the boundaries make sure they are changed.
		if(nextCoords.y > maze.getHeight() - 2) {nextCoords.y = maze.getHeight() - 2;}
		if(nextCoords.x < 1) {nextCoords.x = 1;}
		if(nextCoords.x > maze.getWidth() - 2) {nextCoords.x = maze.getWidth() - 2;}

		// If the block ahead is a wall...
		if(maze.getCellType(nextCoords) == Maze.WALL) {
			// Remove it from the block grid.
			mBlockGrid[nextCoords.x - 1][nextCoords.y] = false;

			// Modify the maze correctly.
			maze.setCellType(nextCoords.x, nextCoords.y, Maze.PASSAGE);

			// Then adjust the score to suit.
			mScore++;
			mScoreSinceBooster++;
		}

		// If the robot is on a wall...
		if(maze.getCellType(robot.getLocation()) == Maze.WALL) {
			// Remove it from the block grid.
			mBlockGrid[robot.getLocationX() - 1][robot.getLocationY()] = false;

			// Modify the maze correctly.
			maze.setCellType(robot.getLocationX(), robot.getLocationY(), Maze.PASSAGE);

			// Then set the score to suit.
			mScore++;
			mScoreSinceBooster++;
		}

		// Finally return the modified maze object.
		return maze;
	}

	/**
	 * Method used to determine whether the robot will remain inside the maze upon moving.
	 * @param robot - The robot used to determine whether it is within the maze boundaries or not.
	 * @param maze - The Maze object that will determine if the robot is within the maze or not.
	 * @return Whether the robot will remain within the maze boundaries upon moving.
	 */
	private boolean isRobotWithinBoundaries(IRobot robot, Maze maze) {
		// Get the next coordinates of the robot.
		Point nextCoords = findNextCoordinates(robot);

		// Define some variables to store whether the robot is fine for each axis.
		boolean isFineX = false;
		boolean isFineY = false;

		// Then determine if the robot is good to go for its next move on each axis.
		if((nextCoords.y > 0) && (nextCoords.y < maze.getHeight() - 1)) {isFineY = true;}
		if((nextCoords.x > 0) && (nextCoords.x < maze.getWidth() - 1)) {isFineX = true;}

		// And return whether it is within the boundaries.
		return isFineX && isFineY;
	}

	/**
	 * Method used to determine the coordinates the robot will move to upon advancing.
	 * @param robot - The robot object that will be used to determine the next position.
	 * @return A Point object that retains the predicted X and Y coordinates.
	 */
	private Point findNextCoordinates(IRobot robot) {
		// Get the current robot coordinates.
		int x = robot.getLocationX();
		int y = robot.getLocationY();

		// Then pick the coordinates ahead of the robot based on its heading.
		switch(robot.getHeading()) {
			case IRobot.NORTH:
				y--;

				break;
			case IRobot.SOUTH:
				y++;

				break;
			case IRobot.WEST:
				x--;

				break;
			case IRobot.EAST:
				x++;

				break;
		}

		// Then finally return a new Point with the predicted coordinates for the next move.
		return new Point(x, y);
	}

	/**
	 * Method that is called when a key is pressed within the IO pane.
	 * @param event - The KeyEvent that represents what key was pressed.
	 */
	private void onKeyPressed(KeyEvent event) {
		// Set the current key event to the event provided.
		this.mCurrentKeyEvent = event;

		// Then test the event to see what key was pressed...
		switch(event.getKeyCode()) {
			// If it was enter, start the game.
			case KeyEvent.VK_ENTER:
				EventBus.broadcast(new Event(IEvent.ROBOT_START, null));

				break;
			// If it was escape, attempt to reset the game.
			case KeyEvent.VK_ESCAPE:
				mRobotIo.gameOver();

				EventBus.broadcast(new Event(IEvent.ROBOT_RESET_REQUEST, null));

				break;
			// If it was space bar, start the bonus if possible.
			case KeyEvent.VK_SPACE:
				if(mBonusIsAvailable) {
					mScoreSinceBooster = 0;

					mBonusIsActive = true;
					mBonusIsAvailable = false;
				}

				break;
		}
	}

	/**
	 * Method that is used to get the Maze object the robot is within at this point in time.
	 * @param robot - The robot object to retrieve the maze from.
	 */
	private Maze getRobotMaze(IRobot robot) {
		// Try to attempt the following...
		try {
			// Get the private maze field from the robot object and make it accessible.
			Field robotMazeField = robot.getClass().getDeclaredField("maze");
			robotMazeField.setAccessible(true);

			// Then return the data that it holds.
			return (Maze) robotMazeField.get(robot);
		// If any exceptions are thrown...
		} catch (Exception e) {
			// Print them to the console.
			e.printStackTrace();
		}

		// If anything bad happened, return null.
		return null;
	}

	/**
	 * Method used to set-up the maze for the first time, making it suitable for the game.
	 * @param robot - The robot currently within the maze so the Maze object can be retrieved.
	 * @return The Maze object that now is the base state of the maze.
	 */
	private Maze setupMaze(IRobot robot) {
		// Get the current Maze object.
		Maze maze = getRobotMaze(robot);

		// If the maze isn't null...
		if(maze != null) {
			// For each coordinate within the maze excluding the edges...
			for(int i = 1; i < maze.getWidth() - 1; i++) {
				for(int j = 0; j < maze.getHeight() - 1; j++) {
					// Set the cell type to passage.
					maze.setCellType(i, j, Maze.PASSAGE);
				}
			}

			// Then return the modified Maze object.
			return maze;
		// Otherwise...
		} else {
			// Return null as something has gone wrong.
			return null;
		}
	}

	/**
	 * Method used to broadcast any changes made to the maze so it gets redrawn.
	 * @param robot - The robot in the maze so its position can be retained.
	 * @param maze - The modified Maze object that contains the changes over the current one.
	 */
	private void broadcastMazeChange(IRobot robot, Maze maze) {
		// Temporarily store the robots coordinates.
		Point tempRobotCoords = robot.getLocation();

		// Then broadcast a new maze event and relocate the robot to its old position.
		EventBus.broadcast(new Event(IEvent.NEW_MAZE, maze));
		EventBus.broadcast(new Event(IEvent.ROBOT_RELOCATE, tempRobotCoords));
	}

	public class IOFrame extends JFrame implements KeyListener, FocusListener {
		// String constants for the formatting of text in the IO panel.
		private static final String TEXT_DEFAULT = "<html><p style=\"font-family: Arial, sans-serif; font-size: 16px; color: white;\">Press enter to begin!</p></html>";
		private static final String TEXT_GAME_OVER = "<html><p style=\"font-family: Arial, sans-serif; font-size: 16px; color: white;\">Game over! Press enter to try again!</p>";

		/**
		 * The text pane that'll be used to display text to the user and perform IO for the robot.
		 */
		private JTextPane mIoPane;

		public IOFrame() {
			// Create the JFrame object with the correct title.
			super("GrandFinale Fun Solution - IO Panel");

			// Then set the bounds and default close operation of the JFrame in the superclass.
			setBounds(0, 0, 480, 200);
			setDefaultCloseOperation(EXIT_ON_CLOSE);

			// Initialise the IO panel.
			mIoPane = new JTextPane();

			// Setup various attributes of the IO pane.
			mIoPane.setBackground(Color.BLACK);
			mIoPane.setForeground(Color.LIGHT_GRAY);
			mIoPane.setContentType("text/html");
			mIoPane.addKeyListener(this);
			mIoPane.addFocusListener(this);

			// Add it to the super class and show the pane.
			getContentPane().add(mIoPane);
			setVisible(true);

			// Then setup the output.
			resetOutput();
		}

		/**
		 * Method called when focus on the IO pane has been lost.
		 * @param event - The FocusEvent that was broadcast because of focus loss.
		 */
		@Override
		public void focusLost(FocusEvent event) {mIoPane.setEditable(true);}

		/**
		 * Method called when focus on the IO pane has been gained.
		 * @param event - The FocusEvent that was broadcast because of focus gain.
		 */
		@Override
		public void focusGained(FocusEvent event) {mIoPane.setEditable(false);}

		/**
		 * Method called when a key is pressed within the IO pane.
		 * @param event - The KeyEvent that was broadcast because of the key being pressed.
		 */
		@Override
		public void keyPressed(KeyEvent event) {onKeyPressed(event);}

		/**
		 * Method called when a key is released within the IO pane.
		 * @param event - The KeyEvent that was broadcast because of the key being released.
		 */
		@Override
		public void keyReleased(KeyEvent event) {}

		/**
		 * Method called when a key is typed within the IO pane.
		 * @param event - The KeyEvent that was broadcast because of the key being typed.
		 */
		@Override
		public void keyTyped(KeyEvent event) {}

		/**
		 * Method called when the score for the player should be updated within the IO pane.
		 * @param score - The score of the player in points.
		 * @param mult - The score multiplier or game difficulty.
		 * @param hasBonus - Whether or not the player has a bonus to be used or not.
		 * @param highScore - Whether or not the new score is a high score.
		 */
		public void updateScore(int score, int mult, boolean hasBonus, boolean highScore) {
			// Create a new StringBuilder for appending strings to.
			StringBuilder scoreStringBuilder = new StringBuilder();

			// Start building the score display string.
			scoreStringBuilder
					.append("<html><p style=\"font-family: Arial, sans-serif; font-size: 16px; color: white\">Score: ")
					.append("<b style=\"font-family: Arial, sans-serif; font-size: ").append(16 + (mult * 2))
					.append("px; color: ");

			// Then choose the score colour based on the game difficulty multiplier.
			switch(mult) {
				case 0:
					scoreStringBuilder.append("#FF3131;\"");

					break;
				case 1:
					scoreStringBuilder.append("#FF8419;\"");

					break;
				case 2:
					scoreStringBuilder.append("#FFD800;\"");

					break;
				case 3:
					scoreStringBuilder.append("#56CF00;\"");

					break;
				case 4:
					scoreStringBuilder.append("#00A8FF;\"");

					break;
				default:
					scoreStringBuilder.append("#BE50FF;\"");

					break;
			}

			// Then add the score to it and state the next target.
			scoreStringBuilder.append(">").append(score)
					.append("</b></p><p style=\"font-family: Arial, sans-serif; font-size: 12px; color: white\">")
					.append("Target: ").append(mNextPointGoal).append("</p>");

			// If the robot has a bonus available to it, display it.
			if(hasBonus) {
				scoreStringBuilder.append("<p style=\"font-family: Arial, sans-serif; font-size: 16px; color: #00A8FF\">")
						.append("Grid wipe available</p>");
			}

			// If the new score is a high score, display it.
			if(highScore) {
				scoreStringBuilder.append("<p style=\"font-family: Arial, sans-serif; font-size: 16px; color: #BE50FF\">")
						.append("New high score!</p>");
			}

			// Then close the HTML tags.
			scoreStringBuilder.append("</html>");

			// And set the text in the IO panel to the newly constructed String.
			mIoPane.setText(scoreStringBuilder.toString().trim());
		}

		/**
		 * Method that should be called when the text output of the IO pane should be defaulted.
		 */
		public void resetOutput() {mIoPane.setText(TEXT_DEFAULT);}

		/**
		 * Method called when the game goes into a game over state, displaying the high score.
		 */
		public void gameOver() {
			// Create a new StringBuilder for appending strings to.
			StringBuilder endGameStringBuilder = new StringBuilder();

			// Then construct the game over string.
			endGameStringBuilder.append(TEXT_GAME_OVER).append("<p style=\"font-family: Arial, sans-serif; font-size: 16px; color: #BE50FF\">")
						.append("High score: ").append(mHighScore).append("</p></html>");

			// And set the text in the IO panel to the newly constructed String.
			mIoPane.setText(endGameStringBuilder.toString().trim());
		}
	}
}