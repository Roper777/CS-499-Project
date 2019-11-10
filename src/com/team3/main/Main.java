package com.team3.main;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.*;

import com.team3.main.entities.*;
import com.team3.main.math.Vector2f;
import com.team3.main.ui.Button;
import com.team3.main.ui.GUIHandler;
import com.team3.main.util.InputHandler;
import com.team3.main.util.MathUtil;

public class Main extends Canvas implements Runnable, MouseMotionListener {

	// INIT VARS
	private static final long serialVersionUID = 1L;
	private static JFrame frame, data_frame;
	private final String title = "Robot Vacuum";
	private final int WIDTH = 960;
	private final int HEIGHT = 540;
	private int fps, ups, frame_time;
	private static boolean running = false;
	private boolean showFPS = true;
	private static Thread thread;
	private int mouse_x = 0, mouse_y = 0;
	
	// END INIT VARS
	
	// UTIL VARS
	private InputHandler input;
	// END UTIL VARS

	private BufferedImage dirt_overlay, dirt_data;
	private Font font;
	private Robot robot;
	private House init_house;
	private GUIHandler gui_handler;
	private SimulationController simulation_controller;
	private DataController data_controller;
	private Display display;

	private JTable data_table;
	private final String[] COLUMN_HEADERS = {"Run Id", "House Id", "Random Eff %", "Snake Eff %", "Spiral Eff %", "Wall Follow Eff %"};
	private Object[][] run_data;

	private boolean run_simulation = false, show_obstacles = true, draw_mode = false, data_mode = false, has_started = false, all = true;
	private int mode_cooldown = 0;
	private String draw_brush = SimulationController.ERASE;

	private Color average_color;
	private double average_color_percentage, random_p, snake_p, spiral_p, wall_follow_p;
	
	public Main() {
		// INIT VARS
		Dimension d = new Dimension(WIDTH, HEIGHT);
		setPreferredSize(d);
		frame = new JFrame(title);
		// END INIT VARS
		
		// UTIL VARS
		input = new InputHandler();
		this.addKeyListener(input);
		this.addMouseListener(input);
		this.addMouseWheelListener(input);
		addMouseMotionListener(this);
		// END UTIL VARS

		// GRAPHICS VARS
		font = new Font("Arial", Font.BOLD, 12);

		try {
			dirt_overlay = ImageIO.read(new File("res/dirt.png"));
		} catch(IOException e) {
			e.printStackTrace();
		}

		dirt_data = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dirt_data.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.dispose();
		// END GRAPHICS VARS

		// Collision handling for vacuum and obstacles
		init_house = new House(WIDTH, HEIGHT);
		Random random = new Random();

		data_controller = new DataController("data/data.json", "data/houses.json", "data/data_pretty.json", "data/runs.json");

		init_house.id = data_controller.getHouseId(init_house);

		// Generate random obstacles
		for (int r = 0; r < HEIGHT; r += House.grid_size) {
			for (int c = 0; c < WIDTH; c += House.grid_size) {
				if ((r < HEIGHT / 2 - Robot.diameter || r > HEIGHT / 2 + Robot.diameter) && (c < WIDTH / 2 - Robot.diameter || c > WIDTH / 2 + Robot.diameter)) {
					if (random.nextBoolean()) {
						int index = 16 * (r / House.grid_size) + (c / House.grid_size);
						if (random.nextBoolean())
							init_house.obstacles.put(index, new Table(c + 9, r + 9));
						else
							init_house.obstacles.put(index, new Chest(c + 9, r + 9));
					}
				}
			}
		}

		// Create Vacuum
		robot = new Robot(new Vector2f(WIDTH/2, HEIGHT/2), Math.PI / 2.0, 1);

		// Create SimulationController
		simulation_controller = new SimulationController(init_house, robot);

		// Create Display
		display = new Display();
		
		Color background_color = new Color(31, 133, 222);
		Color pressed_color = new Color(30, 80, 130);
		//Color outline_color = new Color(38, 96, 145);
		Color font_color = new Color(241, 241, 241);
		
		gui_handler = new GUIHandler(background_color, pressed_color, background_color, font_color, 0.75f);
		gui_handler.addButton(new Button(25, 25, 80, 30, "▶"), "run");
		gui_handler.addButton(new Button(115, 25, 80, 30, "x1"), "speed");
		gui_handler.addButton(new Button(115, 25, 80, 30, "⏹"), "stop");
		gui_handler.addButton(new Button(205, 25, 150, 30, "Toggle Obstacles"), "obstacles");
		gui_handler.addButton(new Button(365, 25, 150, 30, "Path: " + simulation_controller.getMovementMethod()), "movement");
		gui_handler.addButton(new Button(525, 25, 150, 30, "Draw Mode"), "draw");
        gui_handler.addButton(new Button(685, 25, 150, 30, "Data Mode"), "data");

		gui_handler.addButton(new Button(25, 25, 80, 30, "Hold ESC"), "tools");
		gui_handler.addButton(new Button(25, 25, 150, 30, "Simulation Mode"), "simulation");
		gui_handler.addButton(new Button(185, 25, 150, 30, "Brush: " + draw_brush), "brush");
	}

	public static void main(String[] args) {
		Main game = new Main();

		System.setProperty("sun.java2d.opengl", "true");

		frame.setResizable(false);
		frame.add(game);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.toFront();
		frame.setState(JFrame.NORMAL);
		frame.requestFocus();

		frame.setVisible(true);
		game.start();
	}

	private synchronized void start() {
		running = true;

		thread = new Thread(this, "Game Thread");
		thread.start();
	}

	public void run() {
		long oldTime = System.nanoTime();
		long timer = System.currentTimeMillis();

		double ns = 1000000000.0 / 60.0;
		long newTime;
		double delta = 0;

		while (running) {
			newTime = System.nanoTime();
			delta += (double) (newTime - oldTime) / ns;
			oldTime = newTime;
			if (delta >= 1) {
				delta--;
				ups++;
				update();
			}
			render();
			fps++;

			if (System.currentTimeMillis() - timer > 1000) {
				average_color = MathUtil.averageColor(dirt_data, 0, 0, WIDTH, HEIGHT);

				average_color_percentage = (255.0 - average_color.getRed()) / 255.0 * 100.0;

				timer += 1000;
				if (showFPS)
					frame.setTitle(title + " | " + fps + " fps " + ups + " ups | Simulation " + (run_simulation ? "running at x" + simulation_controller.getSpeed() + " | Method: " + simulation_controller.getMovementMethod() : "paused") + " | Seconds elapsed: " + (simulation_controller.getTotalSteps() / 60) + " sec | Clean: " + String.format("%.2f", average_color_percentage) + "%");
				else
					frame.setTitle(title);
				// System.out.println(ups + " ups, " + fps + " fps");
				fps = 0;
				frame_time = ups;
				ups = 0;
			}
		}
		stop();
	}

	private synchronized void stop() {
		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void update() {
		input.update();
		
		if(run_simulation) {
			if (simulation_controller.getTotalSteps() < Robot.BATTERY_LIFE) {
				if (gui_handler.getButtons().get("run").isPressed()) {
					run_simulation = false;

					gui_handler.changeButtonText("run", "▶");
				}
				if (gui_handler.getButtons().get("stop").isPressed()) {
					run_simulation = false;
					data_mode = true;

					setPercentages();
					reset();
					simulation_controller.reset(new Robot(new Vector2f(WIDTH/2, HEIGHT/2), Math.PI / 2.0, 1));

					data_controller.saveData(simulation_controller.getFloorPlan().id, random_p, snake_p, spiral_p, wall_follow_p);

					fullReset();

					gui_handler.changeButtonText("run", "▶");
				}

				simulation_controller.update(dirt_overlay, show_obstacles, dirt_data);
			} else {
				setPercentages();
				reset();
				simulation_controller.reset(new Robot(new Vector2f(WIDTH/2, HEIGHT/2), Math.PI / 2.0, 1));
				if (all && (random_p == 0 || snake_p == 0 || spiral_p == 0 || wall_follow_p == 0))
					simulation_controller.updateMovementMethod();
				else {
					run_simulation = false;
					data_mode = true;

					data_controller.saveData(simulation_controller.getFloorPlan().id, random_p, snake_p, spiral_p, wall_follow_p);
					fullReset();

					gui_handler.changeButtonText("run", "▶");
				}
			}
		} else {
			if(draw_mode){
				if(mode_cooldown < 100){
					mode_cooldown++;
				} else {
					if (input.escape) {
						if (gui_handler.getButtons().get("simulation").isPressed()) {
							draw_mode = false;
							draw_brush = SimulationController.ERASE;
						}

						if (gui_handler.getButtons().get("brush").isPressed()) {
							switch (draw_brush) {
								case SimulationController.ERASE:
									draw_brush = SimulationController.TABLE;
									break;
								case SimulationController.TABLE:
									draw_brush = SimulationController.CHEST;
									break;
								case SimulationController.CHEST:
									draw_brush = SimulationController.ERASE;
									break;
							}

							gui_handler.changeButtonText("brush", "Brush: " + draw_brush);
						}
					} else
						simulation_controller.handleDraw(input, mouse_x, mouse_y, draw_brush);
				}
			} else if (data_mode) {
                if(mode_cooldown < 100){
                    mode_cooldown++;
                } else {
                    if (gui_handler.getButtons().get("simulation").isPressed()) {
                        data_mode = false;
                        data_frame.setVisible(false);
                    }
                }
            } else if (has_started) {
				if (gui_handler.getButtons().get("run").isPressed()) {
					run_simulation = true;
					has_started = true;

					gui_handler.changeButtonText("run", "❚❚");
				}

				if (gui_handler.getButtons().get("speed").isPressed()) {
					simulation_controller.updateSpeed();

					gui_handler.changeButtonText("speed", "x" + simulation_controller.getSpeed());
				}
            } else {
				if (gui_handler.getButtons().get("run").isPressed()) {
					run_simulation = true;
					has_started = true;
					data_controller.saveHouse(init_house);

					if (simulation_controller.getMovementMethod() == SimulationController.ALL)
						simulation_controller.updateMovementMethod();
					else
						all = false;

					gui_handler.changeButtonText("run", "❚❚");
				}

				if (gui_handler.getButtons().get("draw").isPressed()) {
					draw_mode = true;

					mode_cooldown = 0;
				}

				if (gui_handler.getButtons().get("data").isPressed()) {
					data_mode = true;
					showData();

					mode_cooldown = 0;
				}

				if (gui_handler.getButtons().get("speed").isPressed()) {
					simulation_controller.updateSpeed();

					gui_handler.changeButtonText("speed", "x" + simulation_controller.getSpeed());
				}

				if (gui_handler.getButtons().get("obstacles").isPressed()) {
					show_obstacles = !show_obstacles;
				}

				if (gui_handler.getButtons().get("movement").isPressed()) {
					simulation_controller.updateMovementMethod();

					gui_handler.changeButtonText("movement", "Path: " + simulation_controller.getMovementMethod());
				}
			}
		}
	}

	private void render() {
		// INIT CODE
		// GRAPHICS VARS
		BufferStrategy bs = getBufferStrategy();
		if (bs == null) {
			createBufferStrategy(3);
			
			return;
		}
		Graphics2D g = (Graphics2D) bs.getDrawGraphics();
		RenderingHints rh = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHints(rh);
        
		g.setColor(new Color(255, 202, 128));
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.setFont(font);
		// END INIT CODE

		display.render(g, simulation_controller, data_controller, show_obstacles, dirt_overlay);

		gui_handler.update(input, mouse_x, mouse_y, frame_time);
		gui_handler.render(g, run_simulation, draw_mode, input.escape, data_mode, has_started);
		
		// CLOSING CODE
		g.dispose();
		bs.show();
		// END CLOSING CODE
	}

	private void setPercentages() {
		switch (simulation_controller.getMovementMethod()) {
			case SimulationController.RANDOM:
				random_p = average_color_percentage;
				break;
			case SimulationController.SNAKE:
				snake_p = average_color_percentage;
				break;
			case SimulationController.SPIRAL:
				spiral_p = average_color_percentage;
				break;
			case SimulationController.WALL_FOLLOW:
				wall_follow_p = average_color_percentage;
				break;
		}
	}

	private void reset() {
		try {
			dirt_overlay = ImageIO.read(new File("res/dirt.png"));
		} catch(IOException e) {
			e.printStackTrace();
		}

		dirt_data = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dirt_data.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.dispose();
	}

	private void fullReset() {
		has_started = false;
		all = true;

		random_p = 0;
		snake_p = 0;
		spiral_p = 0;
		wall_follow_p = 0;
	}

	private void showData() {
		int length = data_controller.getRunData().size();
		run_data = new Object[length][6];
		DataEntry entry;
		for (int i = 0; i < length; i++) {
			entry = data_controller.getRunData().get(i);
			run_data[i][0] = entry.getRunId();
			run_data[i][1] = entry.getHouseId();
			run_data[i][2] = String.format("%.2f", entry.getRandom()) + "%";
			run_data[i][3] = String.format("%.2f", entry.getSnake()) + "%";;
			run_data[i][4] = String.format("%.2f", entry.getSpiral()) + "%";;
			run_data[i][5] = String.format("%.2f", entry.getWallFollow()) + "%";;
		}

		data_table = new JTable(run_data, COLUMN_HEADERS){public boolean isCellEditable(int rowIndex, int colIndex) {return false;}};;

		JScrollPane container = new JScrollPane(data_table);
		data_table.setFillsViewportHeight(true);

		container.setSize(550, 400);
		container.setLocation(185, 25);

		data_frame = new JFrame("Data");

		data_frame.setResizable(false);
		data_frame.add(container);
		data_frame.pack();
		data_frame.setLocationRelativeTo(null);
		data_frame.toFront();
		data_frame.setState(JFrame.NORMAL);
		data_frame.requestFocus();

		data_frame.setVisible(true);
	}
	
	public int getMouseX(){
		return mouse_x;
	}

	public int getMouseY(){
		return mouse_y;
	}
	
	public void mouseDragged(MouseEvent e){
		mouse_x = e.getX();
		mouse_y = e.getY();
	}

	public void mouseMoved(MouseEvent e) {
		mouse_x = e.getX();
		mouse_y = e.getY();
	}
}
